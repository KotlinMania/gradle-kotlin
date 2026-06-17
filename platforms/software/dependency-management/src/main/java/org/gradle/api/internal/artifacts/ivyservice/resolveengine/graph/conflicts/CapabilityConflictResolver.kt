/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.collect.ImmutableList
import org.gradle.api.Describable
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.CapabilityResolutionDetails
import org.gradle.api.artifacts.ComponentVariantIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons
import org.gradle.api.internal.capabilities.ImmutableCapability
import org.gradle.api.internal.notations.ComponentIdentifierParserFactory
import org.gradle.internal.component.external.model.DefaultComponentVariantIdentifier
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.util.internal.VersionNumber
import java.util.stream.Collectors

/**
 * Responsible for resolving capability conflicts.
 */
class CapabilityConflictResolver(
    rules: ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule>
) {
    private val rules: ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule>
    private val componentNotationParser: NotationParser<Any, ComponentIdentifier>

    init {
        this.rules = rules
        this.componentNotationParser = ComponentIdentifierParserFactory().create()
    }

    /**
     * A node in conflict and the capability it provides that is in conflict.
     */
    private class Candidate(node: NodeState, capability: ImmutableCapability) {
        val node: NodeState
        val capability: ImmutableCapability

        init {
            this.node = node
            this.capability = capability
        }
    }

    /**
     * Applies user-supplied capability conflict resolution rules to the set of candidate nodes.
     * Conflict resolution finishes with a single selected candidate, or all candidates being rejected.
     *
     * @param group The group of the capability in conflict.
     * @param name The name of the capability in conflict.
     * @param nodes The nodes that provide the capability in conflict.
     */
    fun resolve(group: String, name: String, nodes: MutableCollection<NodeState>) {
        // Candidates that are no longer selected are filtered out before this resolver is executed.
        // If there is only one candidate at the beginning of conflict resolution, select that candidate.
        if (nodes.size == 1) {
            val onlyNode = nodes.iterator().next()
            onlyNode.resolveCapabilityConflict(onlyNode)
            return
        }

        val candidates: ImmutableList<Candidate> = discoverCandidates(group, name, nodes)
        val winner = findSelectedCandidate(group, name, candidates)
        if (winner != null) {
            winner.node.resolveCapabilityConflict(winner.node)
            winner.node.getComponent().addCause(ComponentSelectionReasons.CONFLICT_RESOLUTION.withDescription(winner.reason)!!)

            for (losingCandidate in candidates) {
                if (losingCandidate.node !== winner.node) {
                    losingCandidate.node.resolveCapabilityConflict(winner.node)
                }
            }
        } else {
            // If no winner was selected, reject all candidates.
            for (candidate in candidates) {
                val conflictedNodes = candidates.stream().map<NodeState> { c: Candidate? -> c!!.node }
                    .filter { node: NodeState? -> node !== candidate.node }
                    .collect(Collectors.toSet())

                candidate.node.rejectForCapabilityConflict(candidate.capability, conflictedNodes)
            }
        }
    }

    /**
     * Contains the user-selected node, if any, and the reason for selecting it.
     */
    internal class SelectedCandidate(node: NodeState, reason: Describable) {
        val node: NodeState
        val reason: Describable

        init {
            this.node = node
            this.reason = reason
        }
    }

    /**
     * Successively applies all applicable capability resolution rules until a candidate is selected.
     * Returns null if no rules selected any candidate.
     */
    private fun findSelectedCandidate(
        group: String,
        name: String,
        initialCandidates: ImmutableList<Candidate>
    ): SelectedCandidate? {
        var candidates = initialCandidates
        for (rule in rules) {
            if (rule.appliesTo(group, name)) {
                val details = DefaultCapabilityResolutionDetails(
                    componentNotationParser,
                    group,
                    name,
                    candidates
                )

                try {
                    rule.action.execute(details)
                } catch (ex: Exception) {
                    if (ex is InvalidUserCodeException) {
                        throw ex
                    }
                    throw InvalidUserCodeException("Capability resolution rule failed with an error", ex)
                }

                if (details.useHighest) {
                    val highestVersions: ImmutableList<Candidate> = findHighestVersions(candidates)
                    if (highestVersions.size == 1) {
                        return SelectedCandidate(
                            highestVersions.iterator().next().node,
                            Describable { "latest version of capability " + group + ":" + name }
                        )
                    } else {
                        candidates = highestVersions
                    }
                } else if (details.selected != null) {
                    val selectedNode = details.selected!!.node
                    val selectionReason = if (details.reason != null)
                        Describable { "On capability " + group + ":" + name + " " + details.reason }
                    else
                        Describable { "Explicit selection of " + selectedNode.getComponent().componentId.getDisplayName() + " variant " + selectedNode.getMetadata().getName() }
                    return SelectedCandidate(selectedNode, selectionReason)
                }
            }
        }

        return null
    }

    private class DefaultCapabilityResolutionDetails(
        componentIdParser: NotationParser<Any, ComponentIdentifier>,
        group: String,
        name: String,
        candidates: ImmutableList<Candidate>
    ) : CapabilityResolutionDetails {
        private val componentIdParser: NotationParser<Any, ComponentIdentifier>
        private val group: String
        private val name: String
        private val candidates: ImmutableList<Candidate>

        // Mutable State
        private var useHighest = false
        private var reason: String? = null
        private var selected: Candidate? = null

        init {
            this.componentIdParser = componentIdParser
            this.group = group
            this.name = name
            this.candidates = candidates
        }

        override fun getCapability(): ImmutableCapability {
            return DefaultImmutableCapability(group, name, null)
        }

        override fun getCandidates(): ImmutableList<ComponentVariantIdentifier> {
            val candidateIds = ImmutableList.builderWithExpectedSize<ComponentVariantIdentifier>(candidates.size)
            for (candidate in candidates) {
                candidateIds.add(
                    DefaultComponentVariantIdentifier(
                        candidate.node.getComponent().componentId,
                        candidate.node.getMetadata().getName()
                    )
                )
            }
            return candidateIds.build()
        }

        override fun select(candidateId: ComponentVariantIdentifier): CapabilityResolutionDetails {
            for (candidate in candidates) {
                if (candidate.node.getComponent().componentId == candidateId.getId()) {
                    if (candidate.node.getMetadata().getName() == candidateId.getVariantName()) {
                        this.selected = candidate
                        break
                    }
                }
            }

            return this
        }

        override fun select(notation: Any): CapabilityResolutionDetails {
            // TODO: This method only allows users to select a component identifier.
            //       However, it is the nodes of a component which participate in capability conflicts.
            //       This method arbitrarily selects the first candidate node from any given component,
            //       making it imprecise. We should fix this somehow or deprecate this method in favor
            //       of the other `select` method, which permits selecting a specific variant.
            val selectedComponentId = componentIdParser.parseNotation(notation)

            for (candidate in candidates) {
                val candidateComponentId = candidate.node.getComponent().componentId

                if (selectedComponentId == candidateComponentId) {
                    this.selected = candidate
                    return this
                }

                if (candidateComponentId is ModuleComponentIdentifier && selectedComponentId is ModuleComponentIdentifier) {
                    // Since we are performing capability conflict resolution, there is only one candidate component per module.
                    // So, we can be lenient wrt the version number in the component ID.
                    val candidateId = candidateComponentId
                    val selectedId = selectedComponentId
                    if (candidateId.getModuleIdentifier() == selectedId.getModuleIdentifier()) {
                        this.selected = candidate
                        return this
                    }
                }
            }

            val formattedCandidates = candidates.stream().map<String> { c: Candidate? -> c!!.node.getDisplayName() }.sorted().collect(Collectors.toList())
            throw InvalidUserCodeException(selectedComponentId.toString() + " is not a valid candidate for conflict resolution on capability '" + group + ":" + name + "': candidates are " + formattedCandidates)
        }

        override fun selectHighestVersion(): CapabilityResolutionDetails {
            this.useHighest = true
            return this
        }

        override fun because(reason: String): CapabilityResolutionDetails {
            this.reason = reason
            return this
        }
    }

    companion object {
        private fun discoverCandidates(group: String, name: String, nodes: MutableCollection<NodeState>): ImmutableList<Candidate> {
            val candidates = ImmutableList.builderWithExpectedSize<Candidate>(nodes.size)
            for (node in nodes) {
                val capability = node.findCapability(group, name)
                requireNotNull(capability) { "Node " + node.getDisplayName() + " does not provide capability " + group + ":" + name }
                candidates.add(Candidate(node, capability))
            }
            return candidates.build()
        }

        /**
         * Find all candidates that have the highest version of the capability in conflict.
         * If all candidates have the same version, returns all candidates.
         */
        private fun findHighestVersions(candidates: ImmutableList<Candidate>): ImmutableList<Candidate> {
            var highestVersion: String? = null
            var highestVersionCandidates = ImmutableList.builderWithExpectedSize<Candidate>(candidates.size)
            for (candidate in candidates) {
                val version = candidate.capability.getVersion()
                val comparison = VersionNumber.parse(version).compareTo(VersionNumber.parse(highestVersion))
                if (highestVersion == null) {
                    highestVersion = version
                    highestVersionCandidates.add(candidate)
                } else if (comparison > 0) {
                    highestVersion = version
                    highestVersionCandidates = ImmutableList.builderWithExpectedSize<Candidate>(candidates.size)
                    highestVersionCandidates.add(candidate)
                } else if (comparison == 0) {
                    highestVersionCandidates.add(candidate)
                }
            }

            return highestVersionCandidates.build()
        }
    }
}
