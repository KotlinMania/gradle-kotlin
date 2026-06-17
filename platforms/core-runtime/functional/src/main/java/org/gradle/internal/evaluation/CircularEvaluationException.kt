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
import org.gradle.api.GradleException
import java.util.stream.Collectors

/**
 * An exception caused by the circular evaluation.
 */
class CircularEvaluationException internal constructor(evaluationCycle: MutableList<EvaluationOwner>) : GradleException() {
    private val evaluationCycle: ImmutableList<EvaluationOwner>

    init {
        this.evaluationCycle = ImmutableList.copyOf<EvaluationOwner>(evaluationCycle)
    }

    override val message: String
        get() = "Circular evaluation detected: " + formatEvaluationChain(evaluationCycle)

    /**
     * Returns the evaluation cycle.
     * The list represents a "stack" of owners currently being evaluated, and is at least two elements long.
     * The first and last elements of the list are the same owner.
     *
     * @return the evaluation cycle as a list
     */
    fun getEvaluationCycle(): MutableList<EvaluationOwner> {
        return evaluationCycle
    }

    companion object {
        private fun formatEvaluationChain(evaluationCycle: MutableList<EvaluationOwner>): String {
            EvaluationContext.Companion.current().nested().use { ignored ->
                return evaluationCycle.stream()
                    .map<String> { owner: EvaluationOwner? -> Companion.safeToString(owner!!) }
                    .collect(Collectors.joining("\n -> "))
            }
        }

        /**
         * Computes `Object.toString()`, but swallows all thrown exceptions.
         */
        private fun safeToString(owner: Any): String {
            try {
                return owner.toString()
            } catch (e: Throwable) {
                // Calling e.getMessage() here can cause infinite recursion.
                // It happens if e is CircularEvaluationException itself, because getMessage calls formatEvaluationChain.
                // It can also happen for some other custom exceptions that wrap CircularEvaluationException and call its getMessage inside their.
                // This is why we resort to losing the information and only providing exception class.
                // A well-behaved toString should not throw anyway.
                return owner.javaClass.getName() + " (toString failed with " + e.javaClass + ")"
            }
        }
    }
}
