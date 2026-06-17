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
package org.gradle.internal.problems.failure

import com.google.common.collect.ImmutableList
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.ProblemLocator
import org.gradle.internal.exceptions.MultiCauseException
import org.gradle.util.internal.CollectionUtils
import java.lang.reflect.InvocationTargetException
import java.util.Collections
import java.util.IdentityHashMap
import java.util.function.Function

class DefaultFailureFactory(private val stackTraceClassifier: StackTraceClassifier) : FailureFactory {
    override fun create(failure: Throwable): Failure {
        return Job(stackTraceClassifier, ProblemLocator.EMPTY_LOCATOR)
            .convert(failure)
    }

    override fun create(failure: Throwable, problemLocator: ProblemLocator): Failure {
        return Job(stackTraceClassifier, problemLocator)
            .convert(failure)
    }

    private class Job(private val stackTraceClassifier: StackTraceClassifier, private val problemLocator: ProblemLocator, parentSeen: MutableSet<Throwable>? = null) {
        private val seen: MutableSet<Throwable>

        private val recursiveConverter = Function { failure: Throwable -> this.convertRecursively(failure) }

        init {
            this.seen = Collections.newSetFromMap<Throwable>(IdentityHashMap<Throwable, Boolean>())
            if (parentSeen != null) {
                this.seen.addAll(parentSeen)
            }
        }

        fun convert(failure: Throwable): Failure {
            return convertRecursively(failure)
        }

        fun convertRecursively(failure: Throwable): Failure {
            // InvocationTargetException carries no information of its own beyond the wrapped target.
            var failure = failure
            while (failure is InvocationTargetException && failure.cause != null && seen.add(failure)) {
                failure = failure.cause!!
            }
            if (!seen.add(failure)) {
                val replacement = Throwable("[CIRCULAR REFERENCE: " + failure + "]")
                replacement.setStackTrace(failure.getStackTrace())
                failure = replacement
            }

            val stackTrace = ImmutableList.copyOf<StackTraceElement>(failure.getStackTrace())
            val relevances: MutableList<StackTraceRelevance> = classify(stackTrace, stackTraceClassifier)
            val suppressedAndCauses: SuppressedAndCauses = getSuppressedAndCauses(failure)
            val suppressed = convertSuppressed(suppressedAndCauses)
            val causes = convertCauses(suppressedAndCauses)
            val problems: MutableList<ProblemInternal> = ImmutableList.copyOf<ProblemInternal>(problemLocator.findAll(failure))
            return DefaultFailure(failure, stackTrace, relevances, suppressed, causes, problems)
        }

        fun convertSuppressed(suppressedAndCauses: SuppressedAndCauses): MutableList<Failure> {
            val suppressed: Array<Throwable?> = suppressedAndCauses.suppressed
            if (suppressed == null) {
                return mutableListOf<Failure>()
            }

            return CollectionUtils.collect<Failure, Throwable>(
                suppressed,
                determineRecursiveConverter(suppressedAndCauses.childCount())
            )
        }

        fun convertCauses(suppressedAndCauses: SuppressedAndCauses): MutableList<Failure> {
            val causes = suppressedAndCauses.causes
            if (causes.isEmpty()) {
                return mutableListOf<Failure>()
            }
            return CollectionUtils.collect<Failure, Throwable>(
                causes,
                determineRecursiveConverter(suppressedAndCauses.childCount())
            )
        }

        fun determineRecursiveConverter(size: Int): Function<Throwable, Failure> {
            if (size <= 1) {
                return recursiveConverter
            } else {
                // when we branch, we need to have separate seen sets on each branch, since we there cannot be cycles between branches
                return Function { throwable: Throwable -> this.multiChildTransformer(throwable) }
            }
        }

        fun multiChildTransformer(throwable: Throwable): Failure {
            return Job(stackTraceClassifier, problemLocator, seen).convert(throwable)
        }

        private class SuppressedAndCauses(
            private val suppressed: Array<Throwable?>,
            private val causes: MutableList<Throwable>
        ) {
            fun childCount(): Int {
                return causes.size + (if (suppressed != null) suppressed.size else 0)
            }
        }

        companion object {
            private fun classify(stackTrace: MutableList<StackTraceElement>, classifier: StackTraceClassifier): MutableList<StackTraceRelevance> {
                val relevance = ArrayList<StackTraceRelevance>(stackTrace.size)
                for (stackTraceElement in stackTrace) {
                    val r = classifier.classify(stackTraceElement)
                    if (r == null) {
                        throw GradleException("Unable to classify stack trace element: " + stackTraceElement)
                    }
                    relevance.add(r)
                }

                return relevance
            }

            private fun getSuppressedAndCauses(failure: Throwable): SuppressedAndCauses {
                val suppressed: Array<Throwable?> = getSuppressed(failure)
                val causes: MutableList<Throwable> = getCauses(failure)
                return SuppressedAndCauses(suppressed, causes)
            }

            private fun getSuppressed(parent: Throwable): Array<Throwable?> {
                // Short-circuit if suppressed exceptions are not supported by the current JVM
                if (!JavaVersion.current().isJava7Compatible()) {
                    return null
                }

                return parent.getSuppressed()
            }

            private fun getCauses(parent: Throwable): MutableList<Throwable> {
                val causes = ImmutableList.Builder<Throwable>()
                if (parent is MultiCauseException) {
                    causes.addAll((parent as MultiCauseException).causes)
                } else if (parent.cause != null) {
                    causes.add(parent.cause!!)
                }

                return causes.build()
            }
        }
    }

    companion object {
        @JvmStatic
        fun withDefaultClassifier(): DefaultFailureFactory {
            return DefaultFailureFactory(
                CompositeStackTraceClassifier(
                    InternalStackTraceClassifier(),
                    StackTraceClassifier.USER_CODE
                )
            )
        }
    }
}
