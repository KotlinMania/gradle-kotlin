/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.dsl.ImmutableModuleReplacements
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ConflictResolverDetails
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ModuleResolveState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ResolveState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.Describables
import org.gradle.internal.UncheckedException

class DefaultModuleConflictHandler(resolver: ModuleConflictResolver<ComponentState?>, moduleReplacements: ImmutableModuleReplacements, resolveState: ResolveState) : ModuleConflictHandler {
    private val resolver: ModuleConflictResolver<ComponentState?>
    private val conflicts = ConflictContainer<ModuleIdentifier, ComponentState>()
    private val moduleReplacements: ImmutableModuleReplacements
    private val resolveState: ResolveState

    init {
        this.resolver = resolver
        this.moduleReplacements = moduleReplacements
        this.resolveState = resolveState
    }

    override fun getResolver(): ModuleConflictResolver<ComponentState?> {
        return resolver
    }

    override fun registerCandidate(candidate: CandidateModule): Boolean {
        val replacement = moduleReplacements.getReplacementFor(candidate.getId())
        val replacedBy = if (replacement == null) null else replacement.target
        val conflict: Conflict? = conflicts.newElement(candidate.getId(), candidate.getVersions(), replacedBy)
        if (conflict != null) {
            // For each module participating in the conflict, deselect the currently selection, and remove all outgoing edges from the version.
            // This will propagate through the graph and prune configurations that are no longer required.
            for (participant in conflict.participants) {
                resolveState.getModule(participant).markInModuleConflict()
            }
            return true
        }
        return false
    }

    /**
     * Informs if there are any batched up conflicts.
     */
    override fun hasConflicts(): Boolean {
        return !conflicts.isEmpty()
    }

    /**
     * Resolves the conflict by delegating to the conflict resolver who selects single version from given candidates.
     */
    override fun resolveNextConflict() {
        assert(hasConflicts())
        val conflict: Conflict = conflicts.popConflict()
        val details: ConflictResolverDetails<ComponentState?> = DefaultConflictResolverDetails<ComponentState?>(conflict.candidates)
        resolver.select(details)
        if (details.hasFailure()) {
            throw UncheckedException.throwAsUncheckedException(details.failure!!)
        }

        val selected: ComponentState = details.selected!!
        requireNotNull(selected) { "Module conflict resolver " + resolver + " did not select any module from " + conflict.candidates }

        // Visit the winning module first so that when we visit unattached dependencies of
        // losing modules, the winning module always has a selected component.
        val winningModule: ModuleResolveState = selected.getModule()
        resolveState.getModule(winningModule.getId()).resolveModuleConflict(selected)

        for (moduleId in conflict.participants) {
            if (moduleId != winningModule.getId()) {
                resolveState.getModule(moduleId).resolveModuleConflict(selected)
            }
        }

        maybeSetReason(conflict.participants, details.selected!!)
        LOGGER.debug("Selected {} from conflicting modules {}.", details.selected, conflict.candidates)
    }

    private fun maybeSetReason(participants: MutableSet<ModuleIdentifier>, selected: ComponentResolutionState) {
        for (identifier in participants) {
            val replacement = moduleReplacements.getReplacementFor(identifier)
            if (replacement != null) {
                val reason = replacement.reason
                var moduleReplacement = ComponentSelectionReasons.SELECTED_BY_RULE.withDescription(Describables.of(identifier, "replaced with", replacement.target))
                if (reason != null) {
                    moduleReplacement = moduleReplacement!!.withDescription(Describables.of(reason))
                }
                selected.addCause(moduleReplacement!!)
            }
        }
    }

    companion object {
        private val LOGGER: Logger = getLogger(DefaultModuleConflictHandler::class.java)!!
    }
}
