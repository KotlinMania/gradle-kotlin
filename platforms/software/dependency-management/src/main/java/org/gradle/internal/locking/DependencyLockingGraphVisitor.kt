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
package org.gradle.internal.locking

import com.google.common.collect.Maps
import com.google.common.collect.Sets
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.UnresolvedDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState
import org.gradle.api.internal.artifacts.ivyservice.DefaultUnresolvedDependency
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier
import org.gradle.internal.DisplayName

class DependencyLockingGraphVisitor(private val lockId: String, private val lockOwner: DisplayName, private val dependencyLockingProvider: DependencyLockingProvider) : DependencyGraphVisitor {
    private var allResolvedModules: MutableSet<ModuleComponentIdentifier>? = null
    private var changingResolvedModules: MutableSet<ModuleComponentIdentifier>? = null
    private var extraModules: MutableSet<ModuleComponentIdentifier>? = null
    private var forcedModules: MutableMap<ModuleComponentIdentifier, String>? = null
    private var modulesToBeLocked: MutableMap<ModuleIdentifier, ModuleComponentIdentifier>? = null
    private var dependencyLockingState: DependencyLockingState? = null
    private var lockOutOfDate = false

    override fun start(root: RootGraphNode) {
        dependencyLockingState = dependencyLockingProvider.loadLockState(lockId, lockOwner)
        if (dependencyLockingState!!.mustValidateLockState()) {
            val lockedModules = dependencyLockingState!!.lockedDependencies
            modulesToBeLocked = Maps.newHashMapWithExpectedSize<ModuleIdentifier, ModuleComponentIdentifier>(lockedModules.size)
            for (lockedModule in lockedModules) {
                modulesToBeLocked!!.put(lockedModule.getModuleIdentifier(), lockedModule)
            }
            allResolvedModules = Sets.newHashSetWithExpectedSize<ModuleComponentIdentifier>(this.modulesToBeLocked!!.size)
            extraModules = HashSet<ModuleComponentIdentifier>()
            forcedModules = HashMap<ModuleComponentIdentifier, String>()
        } else {
            modulesToBeLocked = mutableMapOf<ModuleIdentifier, ModuleComponentIdentifier>()
            allResolvedModules = HashSet<ModuleComponentIdentifier>()
        }
    }

    override fun visitNode(node: DependencyGraphNode) {
        var changing = false
        val identifier = node.getOwner().getComponentId()
        val metadata = node.getOwner().getMetadataOrNull()
        if (metadata != null && metadata.isChanging()) {
            changing = true
        }
        if (!node.isRoot() && identifier is ModuleComponentIdentifier) {
            var id = identifier
            if (identifier is MavenUniqueSnapshotComponentIdentifier) {
                id = (id as MavenUniqueSnapshotComponentIdentifier).getSnapshotComponent()
            }
            if (!id.getVersion().isEmpty()) {
                if (allResolvedModules!!.add(id)) {
                    if (changing) {
                        addChangingModule(id)
                    }
                    if (dependencyLockingState!!.mustValidateLockState()) {
                        val lockedId = modulesToBeLocked!!.remove(id.getModuleIdentifier())
                        if (lockedId == null) {
                            if (!dependencyLockingState!!.ignoredEntryFilter.isSatisfiedBy(id)) {
                                extraModules!!.add(id)
                            }
                        } else if (lockedId.getVersion() != id.getVersion() && !isNodeRejected(node)) {
                            // Need to check that versions do match, mismatch indicates a force was used
                            forcedModules!!.put(lockedId, id.getVersion())
                        }
                    }
                }
            }
        }
    }

    private fun isNodeRejected(node: DependencyGraphNode): Boolean {
        // That is the state a node is in when it was selected but the selection violates a constraint (reject or strictly)
        return node.getComponent().isRejected
    }

    private fun addChangingModule(id: ModuleComponentIdentifier) {
        if (changingResolvedModules == null) {
            changingResolvedModules = HashSet<ModuleComponentIdentifier>()
        }
        changingResolvedModules!!.add(id)
    }

    fun writeLocks() {
        if (!lockOutOfDate) {
            val changingModules: MutableSet<ModuleComponentIdentifier>? = if (this.changingResolvedModules == null) mutableSetOf<ModuleComponentIdentifier>() else this.changingResolvedModules
            dependencyLockingProvider.persistResolvedDependencies(lockId, lockOwner, allResolvedModules!!, changingModules!!)
        }
    }

    /**
     * This will transform any lock out of date result into an [UnresolvedDependency] in order to plug into lenient resolution.
     * This happens only if there are no previous failures as otherwise lock state can't be asserted.
     *
     * @return the existing failures augmented with any locking related one
     */
    fun collectLockingFailures(): MutableSet<UnresolvedDependency> {
        if (dependencyLockingState!!.mustValidateLockState()) {
            if (!modulesToBeLocked!!.isEmpty() || !extraModules!!.isEmpty() || !forcedModules!!.isEmpty()) {
                lockOutOfDate = true
                return Companion.createLockingFailures(modulesToBeLocked!!, extraModules!!, forcedModules!!)
            }
        }
        return mutableSetOf<UnresolvedDependency>()
    }

    companion object {
        private fun createLockingFailures(
            modulesToBeLocked: MutableMap<ModuleIdentifier, ModuleComponentIdentifier>,
            extraModules: MutableSet<ModuleComponentIdentifier>,
            forcedModules: MutableMap<ModuleComponentIdentifier, String>
        ): MutableSet<UnresolvedDependency> {
            val completedFailures: MutableSet<UnresolvedDependency> = Sets.newHashSetWithExpectedSize<UnresolvedDependency>(modulesToBeLocked.values.size + extraModules.size)
            for (presentInLock in modulesToBeLocked.values) {
                completedFailures.add(
                    DefaultUnresolvedDependency(
                        DefaultModuleVersionSelector.newSelector(presentInLock),
                        LockOutOfDateException("Did not resolve '" + presentInLock.getDisplayName() + "' which is part of the dependency lock state")
                    )
                )
            }
            for (extraModule in extraModules) {
                completedFailures.add(
                    DefaultUnresolvedDependency(
                        DefaultModuleVersionSelector.newSelector(extraModule),
                        LockOutOfDateException("Resolved '" + extraModule.getDisplayName() + "' which is not part of the dependency lock state")
                    )
                )
            }
            for (entry in forcedModules.entries) {
                val forcedModule = entry.key
                completedFailures.add(
                    DefaultUnresolvedDependency(
                        DefaultModuleVersionSelector.newSelector(forcedModule),
                        LockOutOfDateException("Did not resolve '" + forcedModule.getDisplayName() + "' which has been forced / substituted to a different version: '" + entry.value + "'")
                    )
                )
            }
            return completedFailures
        }
    }
}
