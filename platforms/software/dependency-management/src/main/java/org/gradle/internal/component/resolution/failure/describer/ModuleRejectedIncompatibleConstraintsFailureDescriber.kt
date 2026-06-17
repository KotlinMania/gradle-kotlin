/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.internal.component.resolution.failure.describer

import com.google.common.collect.HashMultimap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Multimap
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.internal.component.resolution.failure.SelectionReasonAssessor
import org.gradle.internal.component.resolution.failure.exception.ConflictingConstraintsException
import org.gradle.internal.component.resolution.failure.type.ModuleRejectedFailure
import org.gradle.util.internal.VersionNumber
import java.util.function.Consumer
import java.util.stream.Collectors

/**
 * A [ResolutionFailureDescriber] that describes a [ModuleRejectedFailure] where
 * there were multiple constraints involved in a selection failure that each require different versions.
 *
 *
 * Note that this describer will also be used during the `dependencyInsight` report when there is a selection failure.
 */
abstract class ModuleRejectedIncompatibleConstraintsFailureDescriber : AbstractResolutionFailureDescriber<ModuleRejectedFailure>() {
    override fun canDescribeFailure(failure: ModuleRejectedFailure): Boolean {
        val versionsByReason: MutableList<SelectionReasonAssessor.AssessedSelection.AssessedSelectionReason> = findConflictingConstraints(failure)
        val uniqueVersions = versionsByReason.stream()
            .map<String?> { obj: SelectionReasonAssessor.AssessedSelection.AssessedSelectionReason? -> obj!!.getRequestedVersion() }
            .collect(Collectors.toSet())
            .size
        return uniqueVersions > 1
    }

    override fun describeFailure(failure: ModuleRejectedFailure): ConflictingConstraintsException {
        return ConflictingConstraintsException(summarizeFailure(failure), failure, buildResolutions(failure))
    }

    private fun buildResolutions(failure: ModuleRejectedFailure): MutableList<String> {
        val resolutions: MutableList<String> = ArrayList<String>(failure.getResolutions().size + 1)
        resolutions.addAll(failure.getResolutions())
        resolutions.add(
            DEBUGGING_WITH_DEPENDENCY_INSIGHT_PREFIX + getDocumentationRegistry().getDocumentationFor(
                DEBUGGING_WITH_DEPENDENCY_INSIGHT_ID,
                DEBUGGING_WITH_DEPENDENCY_INSIGHT_SECTION
            ) + "."
        )
        return resolutions
    }

    companion object {
        private const val DEPENDENCY_INSIGHT_TASK_NAME = "dependencyInsight"
        private val DEBUGGING_WITH_DEPENDENCY_INSIGHT_PREFIX = "Debugging using the " + DEPENDENCY_INSIGHT_TASK_NAME + " report is described in more detail at: "
        private const val DEBUGGING_WITH_DEPENDENCY_INSIGHT_ID = "viewing_debugging_dependencies"
        private const val DEBUGGING_WITH_DEPENDENCY_INSIGHT_SECTION = "sec:identifying-reason-dependency-selection"

        private fun findConflictingConstraints(failure: ModuleRejectedFailure): MutableList<SelectionReasonAssessor.AssessedSelection.AssessedSelectionReason> {
            return failure.getAssessedSelection().getReasons().stream()
                .filter { reason: SelectionReasonAssessor.AssessedSelection.AssessedSelectionReason? ->
                    reason!!.getCauses().contains(ComponentSelectionCause.CONSTRAINT) && reason.getRequestedVersion() != null
                }
                .collect(Collectors.toList())
        }

        private fun summarizeFailure(failure: ModuleRejectedFailure): String {
            val conflictingVersionsWithExplanations: Multimap<VersionNumber, String> = HashMultimap.create<VersionNumber, String>()
            findConflictingConstraints(failure).forEach(Consumer { v: SelectionReasonAssessor.AssessedSelection.AssessedSelectionReason? ->
                val version = VersionNumber.parse(v!!.getRequestedVersion())
                val explanations: MutableCollection<String> = Companion.explainReason(v)
                conflictingVersionsWithExplanations.putAll(version!!, explanations)
            })

            val sb = StringBuilder("Component is the target of multiple version constraints with conflicting requirements:\n")
            conflictingVersionsWithExplanations.keySet().stream().sorted().forEach { version: VersionNumber? ->
                val explanations = conflictingVersionsWithExplanations.get(version!!).stream().sorted().collect(Collectors.toList())
                sb.append(explanations.get(0))
                val numOtherPaths = explanations.size - 1
                if (numOtherPaths > 0) {
                    sb.append(" (").append(numOtherPaths).append(" other path")
                    if (numOtherPaths > 1) {
                        sb.append("s")
                    }
                    sb.append(" to this version)")
                }
                sb.append("\n")
            }

            return sb.toString()
        }

        private fun explainReason(reason: SelectionReasonAssessor.AssessedSelection.AssessedSelectionReason): MutableCollection<String> {
            val requestedVersion: String? = checkNotNull(reason.getRequestedVersion())
            if (reason.isFromLock()) {
                return mutableSetOf<String>(requestedVersion + " - from lock file")
            }

            val paths = reason.getSegmentedSelectionPaths()
            val result = ImmutableSet.builderWithExpectedSize<String>(paths.size)
            for (path in paths) {
                val pathLength = path.size
                if (pathLength == 1) {
                    // The constraint is declared in the root node
                    result.add(requestedVersion!!)
                } else if (pathLength == 2) {
                    // The constraint is declared in a direct dependency
                    result.add(requestedVersion + " - directly in " + path.get(1))
                } else if (pathLength > 2) {
                    // The constraint is declared at some arbitrarily deep point in the graph
                    result.add(requestedVersion + " - transitively via " + path.get(1))
                }
            }

            return result.build()
        }
    }
}
