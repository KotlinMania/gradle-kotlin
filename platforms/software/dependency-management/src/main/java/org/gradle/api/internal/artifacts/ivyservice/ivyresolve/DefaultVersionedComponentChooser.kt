/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.Action
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ComponentSelectionInternal
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal
import org.gradle.api.internal.artifacts.DefaultComponentSelection
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.artifacts.repositories.ArtifactResolutionDetails
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributeSchemaServices
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import org.gradle.internal.resolve.RejectedByAttributesVersion
import org.gradle.internal.resolve.RejectedByRuleVersion
import org.gradle.internal.resolve.result.ComponentSelectionContext
import org.gradle.internal.rules.SpecRuleAction
import org.gradle.util.internal.CollectionUtils.sort
import java.util.Collections

internal class DefaultVersionedComponentChooser(
    private val versionComparator: VersionComparator,
    private val versionParser: VersionParser,
    private val attributeSchemaServices: AttributeSchemaServices,
    private val componentSelectionRules: ComponentSelectionRulesInternal,
    private val consumerSchema: ImmutableAttributesSchema
) : VersionedComponentChooser {
    private val rulesProcessor = ComponentSelectionRulesProcessor()

    override fun selectNewestComponent(one: ExternalModuleComponentGraphResolveMetadata?, two: ExternalModuleComponentGraphResolveMetadata?): ComponentGraphResolveMetadata? {
        if (one == null || two == null) {
            return if (two == null) one else two
        }

        val comparison =
            versionComparator.compare(VersionInfo(versionParser.transform(one.getModuleVersionId()!!.getVersion())), VersionInfo(versionParser.transform(two.getModuleVersionId()!!.getVersion())))

        if (comparison == 0) {
            if (isMissingModuleDescriptor(one) && !isMissingModuleDescriptor(two)) {
                return two
            }
            return one
        }

        return if (comparison < 0) two else one
    }

    private fun isMissingModuleDescriptor(metadata: ExternalModuleComponentGraphResolveMetadata): Boolean {
        return metadata.isMissing
    }

    override fun selectNewestMatchingComponent(
        versions: MutableCollection<out ModuleComponentResolveState?>?,
        result: ComponentSelectionContext,
        requestedVersionMatcher: VersionSelector,
        rejectedVersionSelector: VersionSelector?,
        consumerAttributes: ImmutableAttributes
    ) {
        val rules = componentSelectionRules.rules

        // Loop over all listed versions, sorted by LATEST first
        val resolveStates = sortLatestFirst(versions)
        val contentFilter: Action<in ArtifactResolutionDetails?>? = result.contentFilter
        for (candidate in resolveStates) {
            if (contentFilter != null) {
                val details = DynamicArtifactResolutionDetails(candidate)
                contentFilter.execute(details)
                if (!details.found) {
                    continue
                }
            }

            val metadataProvider: DefaultMetadataProvider = createMetadataProvider(candidate)
            val versionMatches: Boolean = versionMatches(requestedVersionMatcher, candidate, metadataProvider)
            if (metadataIsNotUsable(result, metadataProvider)) {
                return
            }

            val candidateId: ModuleComponentIdentifier = candidate.id
            if (!versionMatches) {
                result.notMatched(candidateId, requestedVersionMatcher)
                continue
            }

            // Do this check first, it cannot require the metadata
            if (isRejectedBySelector(candidateId, rejectedVersionSelector)) {
                // Mark this version as rejected
                result.rejectedBySelector(candidateId, rejectedVersionSelector)
                continue
            }

            // Do these checks second, they may require the metadata
            val rejectedByRules = isRejectedByRule(candidateId, rules, metadataProvider)
            if (rejectedByRules != null) {
                // Mark this version as rejected
                result.rejectedByRule(rejectedByRules)

                if (requestedVersionMatcher.matchesUniqueVersion()) {
                    // Only consider one candidate, because matchesUniqueVersion means that there's no ambiguity on the version number
                    break
                }
            } else {
                // This last check always requires the metadata
                val maybeRejectByAttributes = tryRejectByAttributes(candidateId, metadataProvider, consumerAttributes)
                if (maybeRejectByAttributes != null) {
                    result.doesNotMatchConsumerAttributes(maybeRejectByAttributes)
                } else {
                    result.matches(candidateId)
                    return
                }
            }
        }

        // if we reach this point, no match was found, either because there are no versions matching the selector
        // or all of them were rejected
        result.noMatchFound()
    }

    private fun tryRejectByAttributes(id: ModuleComponentIdentifier?, provider: MetadataProvider, consumerAttributes: ImmutableAttributes): RejectedByAttributesVersion? {
        if (consumerAttributes.isEmpty()) {
            return null
        }

        // At this point, we need the component metadata, because it may declare attributes that are needed for matching
        // Component metadata may not necessarily hit the network if there is a custom component metadata supplier
        val componentMetadata = provider.getComponentMetadata()
        if (componentMetadata != null) {
            // TODO: Do not assume the producer schema is empty.

            val producerSchema = ImmutableAttributesSchema.EMPTY
            val matcher = attributeSchemaServices.getMatcher(consumerSchema, producerSchema)

            val attributes = (componentMetadata.getAttributes() as AttributeContainerInternal).asImmutable()
            val matching = matcher.isMatchingCandidate(attributes.asImmutable(), consumerAttributes)
            if (!matching) {
                return RejectedByAttributesVersion(id, matcher.describeMatching(attributes, consumerAttributes)!!)
            }
        }
        return null
    }

    /**
     * This method checks if the metadata provider already knows that metadata for this version is not usable.
     * If that's the case it means it's not necessary to perform more checks for this version, because we already
     * know it's broken in some way.
     *
     * @param result where to notify that metadata is broken, if broken
     * @param metadataProvider the metadata provider
     * @return true if metadata is not usable
     */
    private fun metadataIsNotUsable(result: ComponentSelectionContext, metadataProvider: DefaultMetadataProvider): Boolean {
        if (!metadataProvider.isUsable()) {
            applyTo(metadataProvider, result)
            return true
        }
        return false
    }

    override fun isRejectedComponent(candidateIdentifier: ModuleComponentIdentifier, metadataProvider: MetadataProvider): RejectedByRuleVersion? {
        return isRejectedByRule(candidateIdentifier, componentSelectionRules.rules, metadataProvider)
    }

    private fun isRejectedByRule(
        candidateIdentifier: ModuleComponentIdentifier,
        rules: MutableCollection<SpecRuleAction<in ComponentSelection?>?>?,
        metadataProvider: MetadataProvider
    ): RejectedByRuleVersion? {
        val selection: ComponentSelectionInternal = DefaultComponentSelection(candidateIdentifier, metadataProvider)
        rulesProcessor.apply(selection, rules, metadataProvider)
        if (selection.isRejected) {
            return RejectedByRuleVersion(candidateIdentifier, selection.rejectionReason)
        }
        return null
    }

    private fun isRejectedBySelector(candidateIdentifier: ModuleComponentIdentifier, rejectedVersionSelector: VersionSelector?): Boolean {
        return rejectedVersionSelector != null && rejectedVersionSelector.accept(candidateIdentifier.getVersion())
    }

    private fun sortLatestFirst(listing: MutableCollection<out ModuleComponentResolveState?>?): MutableList<ModuleComponentResolveState> {
        return sort<ModuleComponentResolveState?>(listing, Collections.reverseOrder<Versioned?>(versionComparator))
    }

    private class DynamicArtifactResolutionDetails(private val resolveState: ModuleComponentResolveState) : ArtifactResolutionDetails {
        var found: Boolean = true

        val moduleId: ModuleIdentifier
            get() = resolveState.id.getModuleIdentifier()

        val componentId: ModuleComponentIdentifier?
            get() = resolveState.id

        val isVersionListing: Boolean
            get() = false

        override fun notFound() {
            found = false
        }
    }

    companion object {
        private fun createMetadataProvider(candidate: ModuleComponentResolveState?): DefaultMetadataProvider {
            return DefaultMetadataProvider(candidate)
        }

        private fun applyTo(provider: DefaultMetadataProvider, result: ComponentSelectionContext) {
            val metaDataResult = provider.getResult()
            when (metaDataResult.state) {
                Unknown, Missing -> result.noMatchFound()
                Failed -> result.failed(metaDataResult.getFailure())
                else -> throw IllegalStateException("Unexpected meta-data resolution result.")
            }
        }

        private fun versionMatches(selector: VersionSelector, component: ModuleComponentResolveState, metadataProvider: MetadataProvider): Boolean {
            if (selector.requiresMetadata()) {
                val componentMetadata = metadataProvider.getComponentMetadata()
                return componentMetadata != null && selector.accept(componentMetadata)
            } else {
                return selector.accept(component.version)
            }
        }
    }
}
