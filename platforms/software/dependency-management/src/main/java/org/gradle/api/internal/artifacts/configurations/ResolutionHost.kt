/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts.configurations

import org.gradle.api.internal.artifacts.ivyservice.TypedResolveException
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.component.resolution.failure.ReportableAsProblem
import org.gradle.internal.exceptions.MultiCauseException
import java.util.LinkedList
import java.util.Optional
import java.util.Queue
import java.util.function.Consumer

/**
 * The "Host" or owner of a resolution -- the thing in charge of the resolution, or the thing being resolved.
 *
 *
 * The purpose of this type is to be a configuration-cache compatible representation of the thing
 * being resolved. This type should remain as minimal as possible.
 *
 * TODO: Split the interface into two: one for tracking what is doing the resolving and one for mapping resolution problems
 * We already have [ResolveExceptionMapper], which might be perfect for this purpose, we'd just have to refactor to
 * pass that type alongside this one.
 */
interface ResolutionHost {
    fun displayName(): DisplayName?

    val displayName: String
        get() = displayName()!!.getDisplayName()

    fun displayName(type: String): DisplayName {
        return Describables.of(displayName()!!, type)
    }

    /**
     * Returns the problems service this host can use for reporting failures.
     *
     * @return the problems service
     */
    val problems: ProblemsInternal?

    /**
     * Rethrows the provided failures, doing nothing if the list of failures is empty.
     *
     *
     * If any of the failures (or their ancestor causes) are [ReportableAsProblem], they will all be reported to the problems
     * service available on this type via [.getProblems].
     *
     * @param resolutionType what was resolved, e.g. "dependencies", "artifacts", "files"
     * @param failures the exceptions encountered during resolution
     */
    fun rethrowFailuresAndReportProblems(resolutionType: String, failures: MutableCollection<Throwable>) {
        reportProblems(failures)
        consolidateFailures(resolutionType, failures)!!.ifPresent(Consumer { e: TypedResolveException? ->
            throw e
        })
    }

    /**
     * Consolidates the given failures into a single exception, if there are any.
     *
     * @param resolutionType what was resolved, e.g. "dependencies", "artifacts", "files"
     * @param failures the exceptions encountered during resolution
     * @return a [TypedResolveException], which is a [MultiCauseException] containing all the failures,
     * or [Optional.empty] if there are no failures
     */
    fun consolidateFailures(resolutionType: String, failures: MutableCollection<Throwable>): Optional<TypedResolveException>?

    /**
     * If the given failure (or their ancestor causes) are [ReportableAsProblem], they will all be reported to the problems
     * service available on this type via [.getProblems].
     *
     * @param failure the exception to inspect
     */
    fun reportProblems(failure: Throwable) {
        reportProblems(mutableSetOf<Throwable>(failure))
    }

    /**
     * If the given failures (or their ancestor causes) are [ReportableAsProblem], they will all be reported to the problems
     * service available on this type via [.getProblems].
     *
     * @param failures the exceptions to inspect
     */
    fun reportProblems(failures: MutableCollection<Throwable>) {
        val seen: MutableSet<Throwable> = HashSet<Throwable>(failures.size * 2) // Assume every failure has a cause
        val exceptionQueue: Queue<Throwable> = LinkedList<Throwable>(failures)

        while (!exceptionQueue.isEmpty()) {
            val current = exceptionQueue.poll()

            // If we have self-caused exceptions, or other circular references, we may encounter the same failure again, in which case we can skip processing
            if (!seen.add(current)) {
                continue
            }

            if (current is ReportableAsProblem) {
                (current as ReportableAsProblem).reportAsProblem(this.problems)
            }

            if (current is MultiCauseException) {
                exceptionQueue.addAll((current as MultiCauseException).causes!!)
            } else {
                if (current.cause != null) {
                    exceptionQueue.add(current.cause)
                }
            }
        }
    }
}
