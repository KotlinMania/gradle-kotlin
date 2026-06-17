/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder

import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints.equals

class VirtualPlatformState(
    versionComparator: Comparator<Version>,
    versionParser: VersionParser,
    private val platformModule: ModuleResolveState,
    private val resolveOptimizations: ResolveOptimizations
) {
    private val vC: Comparator<String>

    val participatingModules: MutableSet<ModuleResolveState> = LinkedHashSet<ModuleResolveState>()
    private val orphanEdges: MutableList<EdgeState> = ArrayList<EdgeState>(2)

    private var hasForcedParticipatingModule = false

    init {
        this.vC = Comparator { o1: String?, o2: String? -> versionComparator.compare(versionParser.transform(o2), versionParser.transform(o1)) }
    }

    fun participatingModule(state: ModuleResolveState) {
        state.registerPlatformOwner(this)
        if (participatingModules.add(state)) {
            resolveOptimizations.declareVirtualPlatformInUse()
            val platformComponent = platformModule.getSelected()
            if (platformComponent != null) {
                // There is a possibility that a platform version was selected before a new member
                // of the platform was discovered. In this case, we need to restart the selection,
                // or some members will not be upgraded
                for (nodeState in platformComponent.getNodes()) {
                    nodeState.markForVirtualPlatformRefresh()
                }
            }
            // If any versions of this platform previously failed to resolve
            // (e.g. an explicit platform dependency resolved before any
            // belongsTo edges were discovered), replace those failures with
            // virtual platform metadata. We recover all versions, not just the
            // currently selected one, so that if selection later changes back to
            // a previously failed version, it has the correct valid virtual state.
            for (version in platformModule.getAllVersions()) {
                if (version.getMetadataResolveFailure() != null) {
                    version.resolveAsVirtualPlatform()
                }
            }
            hasForcedParticipatingModule = hasForcedParticipatingModule or isParticipatingModuleForced(state)
        }
    }

    private val forcedVersion: String?
        get() {
            var version: String? = null
            for (selector in platformModule.getSelectors()) {
                if (selector.hasStrongOpinion()) {
                    val requested = selector.getRequested()
                    if (requested is ModuleComponentSelector) {
                        val nv = requested.getVersion()
                        if (version == null || vC.compare(nv, version) < 0) {
                            version = nv
                        }
                    }
                }
            }
            return version
        }

    val candidateVersions: MutableList<String>
        get() {
            val forcedVersion = this.forcedVersion
            val selectedPlatformComponent = platformModule.getSelected()
            val sorted: MutableList<String> = ArrayList<String>(participatingModules.size + 1)
            sorted.add(selectedPlatformComponent!!.getVersion())
            for (module in participatingModules) {
                val selected = module.getSelected()
                if (selected != null) {
                    sorted.add(selected.getVersion())
                }
            }
            sorted.sort(vC)
            if (forcedVersion != null) {
                return sorted.subList(sorted.indexOf(forcedVersion), sorted.size)
            } else {
                return sorted
            }
        }

    val isForced: Boolean
        get() = hasForcedParticipatingModule || this.isSelectedPlatformForced

    private val isSelectedPlatformForced: Boolean
        get() {
            val forced = platformModule.getSelected()!!.hasStrongOpinion()
            if (forced) {
                resolveOptimizations.declareForcedPlatformInUse()
            }
            return forced
        }

    private fun isParticipatingModuleForced(participatingModule: ModuleResolveState): Boolean {
        val selected = participatingModule.getSelected()
        val forced = selected != null && selected.hasStrongOpinion()
        if (forced) {
            resolveOptimizations.declareForcedPlatformInUse()
        }
        return forced
    }

    /**
     * It is possible that a member of a virtual platform is discovered after trying
     * to resolve the platform itself. If the platform was declared as a dependency,
     * then the engine thinks that the platform module is unresolved. We need to
     * remember such edges, because in case a virtual platform gets defined, the error
     * is no longer valid and we can attach the target revision.
     *
     * @param edge the orphan edge
     */
    fun addOrphanEdge(edge: EdgeState) {
        orphanEdges.add(edge)
    }

    fun attachOrphanEdges() {
        for (orphanEdge in orphanEdges) {
            orphanEdge.getFrom().prepareToRecomputeEdge(orphanEdge)
        }
        orphanEdges.clear()
    }

    fun isGreaterThanForcedVersion(version: String): Boolean {
        val forcedVersion = this.forcedVersion
        if (forcedVersion == null) {
            return false
        }
        return vC.compare(forcedVersion, version) > 0
    }
}
