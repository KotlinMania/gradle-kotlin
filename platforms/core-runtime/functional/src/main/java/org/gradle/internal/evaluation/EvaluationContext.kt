/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.internal.evaluation

import com.google.common.collect.ImmutableList
import it.unimi.dsi.fastutil.objects.ReferenceArrayList
import java.util.function.Supplier

/**
 * This class keeps track of all objects being evaluated at the moment.
 * It helps to provide nicer error messages when the evaluation enters an endless cycle (A obtains a value of B which obtains a value of A).
 *
 *
 * Concurrent evaluation of same objects by multiple threads is still allowed.
 *
 *
 * This class is thread-safe, but the returned contexts must be closed by the same thread that opens them.
 */
class EvaluationContext private constructor() {
    private val threadLocalContext: ThreadLocal<PerThreadContext> = ThreadLocal.withInitial<PerThreadContext>(Supplier { PerThreadContext(null) })

    /**
     * Adds the owner to the set of "evaluating" objects and returns the context instance to remove it from there upon closing.
     * This method is intended to be used in the try-with-resources block's initializer.
     */
    fun open(owner: EvaluationOwner): EvaluationScopeContext {
        return this.context.open(owner)
    }

    val owner: EvaluationOwner?
        /**
         * Returns the "topmost" evaluation owner or null if nothing is being evaluated right now.
         */
        get() = this.context.owner

    val isEvaluating: Boolean
        /**
         * Returns whether an evaluation is in progress in the current thread.
         */
        get() = this.owner != null

    /**
     * Runs the `evaluation` with the `owner` being marked as "evaluating".
     * If the owner is already being evaluated, throws [CircularEvaluationException].
     *
     * @param owner the object to evaluate
     * @param evaluation the evaluation
     * @param <R> the type of the result
     * @param <E> (optional) exception type being thrown by the evaluation
     * @return the result of the evaluation
     * @throws E exception from the `evaluation` is propagated
     * @throws CircularEvaluationException if the owner is currently being evaluated in the outer scope
    </E></R> */
    fun <R, E : Exception?> evaluate(owner: EvaluationOwner, evaluation: ScopedEvaluation<out R, E?>): R? {
        open(owner).use { ignored ->
            return evaluation.evaluate()
        }
    }

    /**
     * Runs the `evaluation` with the `owner` being marked as "evaluating".
     * If the owner is already being evaluated, returns `fallbackValue`.
     *
     *
     * Note that fallback value is not used if the evaluation itself throws [CircularEvaluationException], the exception propagates instead.
     *
     * @param owner the owner to evaluate
     * @param fallbackValue the fallback value to return if the owner is already evaluating
     * @param evaluation the evaluation
     * @param <R> the type of the result
     * @param <E> (optional) exception type being thrown by the evaluation
     * @return the result of the evaluation
     * @throws E exception from the `evaluation` is propagated
    </E></R> */
    fun <R, E : Exception?> tryEvaluate(owner: EvaluationOwner, fallbackValue: R?, evaluation: ScopedEvaluation<out R, E?>): R? {
        if (this.context.isInScope(owner)) {
            return fallbackValue
        }
        // It is possible that the downstream chain itself forms a cycle.
        // However, it should be its responsibility to be defined in terms of safe evaluation rather than us intercepting the failure here.
        return evaluate(owner, evaluation)
    }

    /**
     * Runs the `evaluation` in a nested evaluation context. A nested context allows to re-enter evaluation of the objects that are being evaluated in the enclosed context.
     *
     *
     * Use sparingly. In most cases, it is better to rework the call chain to avoid re-evaluating the owner.
     *
     * @param evaluation the evaluation
     * @param <R> the type of the result
     * @param <E> (optional) exception type being thrown by the evaluation
     * @return the result of the evaluation
     * @throws E exception from the `evaluation` is propagated
    </E></R> */
    fun <R, E : Exception?> evaluateNested(evaluation: ScopedEvaluation<out R, E?>): R? {
        nested().use { ignored ->
            return evaluation.evaluate()
        }
    }

    private val context: PerThreadContext
        get() = threadLocalContext.get()

    private fun setContext(newContext: PerThreadContext): PerThreadContext {
        threadLocalContext.set(newContext)

        return newContext
    }

    fun nested(): EvaluationScopeContext {
        return this.context.nested()
    }

    /**
     * Uses a simple identity-based array stack.
     *
     * Cycle detection scans the array linearly, which is fast for the typical
     * shallow evaluation depths (< 10). Automatically resizes to accommodate
     * deeper stacks.
     */
    private inner class PerThreadContext(private val parent: PerThreadContext?) : EvaluationScopeContext {
        private val stack: ReferenceArrayList<EvaluationOwner>

        init {
            this.stack = ReferenceArrayList<EvaluationOwner>(INITIAL_CAPACITY)
        }

        fun open(owner: EvaluationOwner): PerThreadContext {
            // Identity scan for cycle detection
            if (isInScope(owner)) {
                throw prepareException(owner)
            }
            stack.add(owner)
            return this
        }

        override fun nested(): EvaluationScopeContext {
            return setContext(this@EvaluationContext.PerThreadContext(this))
        }

        override fun close() {
            // Closing the "nested" context.
            if (parent != null && stack.isEmpty()) {
                setContext(parent)
                return
            }
            stack.pop()
        }

        fun isInScope(owner: EvaluationOwner): Boolean {
            return indexOf(owner) != -1
        }

        override val owner: EvaluationOwner?
            get() {
            if (stack.isEmpty()) {
                return null
            }
            return stack.top()
        }

        fun prepareException(circular: EvaluationOwner): CircularEvaluationException {
            val i = indexOf(circular)
            assert(i >= 0)
            val path = stack.subList(i, stack.size)
            val builder = ImmutableList.builderWithExpectedSize<EvaluationOwner>(path.size + 1)
            builder.addAll(path)
            builder.add(circular)
            return CircularEvaluationException(builder.build())
        }

        fun indexOf(owner: EvaluationOwner): Int {
            return stack.indexOf(owner)
        }

    }

    companion object {
        /**
         * This was chosen by looking at the typical stack sizes on the
         * Gradle build. We're trying to balance resizing vs wasted space.
         *
         * Most stacks were small (only 1 element). The biggest stack was 19 elements.
         *
         * This size represented about 85% of all contexts, so Gradle does
         * no resizing in those cases.
         *
         * The extreme case still requires 2 resizings.
         */
        private const val INITIAL_CAPACITY = 8

        private val INSTANCE = EvaluationContext()

        /**
         * Returns the current instance of EvaluationContext for this thread.
         *
         * @return the evaluation context
         */
        @JvmStatic
        fun current(): EvaluationContext {
            return INSTANCE
        }
    }
}
