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
package org.gradle.internal.component.resolution.failure

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.MessageBuilderHelper
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ModuleResolveState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal
import java.util.function.Consumer

/**
 * A static utility class used by [ResolutionFailureHandler] to assess and classify
 * component selection failures during graph construction.
 *
 *
 * This type will map from types internal to the resolution engine to simple value types
 * defined as static inner types here that are serializable, lightweight, and contain only
 * the information necessary to describe the failure.
 */
object SelectionReasonAssessor {
    /**
     * Assess the reasons for selecting (or failing to select) a component of the given [ModuleResolveState].
     *
     * @param moduleResolveState the module resolve state to assess
     * @return an [AssessedSelection] instance that summarizes the reasons for selecting (or failing to select) the module
     */
    fun assessSelection(moduleResolveState: ModuleResolveState): AssessedSelection {
        val assessedReasons = ImmutableList.builder<AssessedSelection.AssessedSelectionReason>()
        moduleResolveState.visitAllIncomingEdges(Consumer { incomingEdge: DependencyGraphEdge ->
            val requestedVersion = getRequestedVersion(incomingEdge.getDependencyMetadata().getSelector())
            val pathNames = MessageBuilderHelper.findPathNamesTo(incomingEdge)
            val causes = getCauses(incomingEdge)
            assessedReasons.add(
                AssessedSelection.AssessedSelectionReason(
                    pathNames,
                    requestedVersion,
                    causes,
                    incomingEdge.isFromLock()
                )
            )
        })

        return AssessedSelection(moduleResolveState.getId(), assessedReasons.build())
    }

    private fun getCauses(incomingEdge: DependencyGraphEdge): ImmutableSet<ComponentSelectionCause> {
        val causes = ImmutableSet.builder<ComponentSelectionCause>()
        incomingEdge.visitSelectionReasons(Consumer { reason: ComponentSelectionDescriptorInternal? -> causes.add(reason!!.getCause()) })
        return causes.build()
    }

    private fun getRequestedVersion(selector: ComponentSelector): String? {
        val versionConstraint = getVersionConstraint(selector)
        if (versionConstraint != null) {
            return if (!versionConstraint.getStrictVersion().isEmpty())
                versionConstraint.getStrictVersion()
            else
                versionConstraint.getRequiredVersion()
        }
        return null
    }

    private fun getVersionConstraint(selector: ComponentSelector): VersionConstraint? {
        if (selector is ModuleComponentSelector) {
            return selector.getVersionConstraint()
        } else {
            return null
        }
    }

    /**
     * Simple serializable, lightweight value type that represents all the reasons for selecting a
     * specific module.
     */
    class AssessedSelection(private val moduleId: ModuleIdentifier, private val reasons: ImmutableList<AssessedSelectionReason>) {
        fun getModuleId(): ModuleIdentifier {
            return moduleId
        }

        fun getReasons(): ImmutableList<AssessedSelectionReason> {
            return reasons
        }

        /**
         * Simple serializable, lightweight value type that represents a single reason for selecting a specific module,
         * including the version, the cause, and a description.
         */
        class AssessedSelectionReason(
            private val segmentedSelectionPaths: ImmutableList<ImmutableList<String>>,
            private val requestedVersion: String?,
            private val causes: ImmutableSet<ComponentSelectionCause>,
            private val isFromLock: Boolean
        ) {
            fun getSegmentedSelectionPaths(): ImmutableList<ImmutableList<String>> {
                return segmentedSelectionPaths
            }

            fun getRequestedVersion(): String? {
                return requestedVersion
            }

            fun getCauses(): MutableSet<ComponentSelectionCause> {
                return causes
            }

            fun isFromLock(): Boolean {
                return isFromLock
            }
        }
    }
}
