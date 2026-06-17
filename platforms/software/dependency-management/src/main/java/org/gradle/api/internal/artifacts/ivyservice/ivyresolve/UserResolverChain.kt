/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.attributes.AttributeSchemaServices
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.model.CalculatedValueFactory
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor
import org.gradle.internal.resolve.resolver.ArtifactResolver
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver

class UserResolverChain(
    versionComparator: VersionComparator,
    val componentSelectionRules: ComponentSelectionRulesInternal,
    versionParser: VersionParser,
    consumerSchema: ImmutableAttributesSchema,
    attributesFactory: AttributesFactory,
    attributeSchemaServices: AttributeSchemaServices,
    componentMetadataProcessor: ComponentMetadataProcessorFactory,
    componentMetadataSupplierRuleExecutor: ComponentMetadataSupplierRuleExecutor,
    calculatedValueFactory: CalculatedValueFactory,
    cacheExpirationControl: CacheExpirationControl
) : ComponentResolvers {
    private val componentIdResolver: RepositoryChainDependencyToComponentIdResolver
    private val componentResolver: RepositoryChainComponentMetaDataResolver
    private val artifactResolver: RepositoryChainArtifactResolver

    init {
        val componentChooser: VersionedComponentChooser = DefaultVersionedComponentChooser(
            versionComparator, versionParser, attributeSchemaServices,
            componentSelectionRules, consumerSchema
        )
        componentIdResolver = RepositoryChainDependencyToComponentIdResolver(
            componentChooser,
            versionParser,
            attributesFactory,
            componentMetadataProcessor,
            componentMetadataSupplierRuleExecutor,
            cacheExpirationControl
        )
        componentResolver = RepositoryChainComponentMetaDataResolver(componentChooser, calculatedValueFactory)
        artifactResolver = RepositoryChainArtifactResolver(calculatedValueFactory)
    }

    override fun getComponentIdResolver(): DependencyToComponentIdResolver {
        return componentIdResolver
    }

    override fun getComponentResolver(): ComponentMetaDataResolver {
        return componentResolver
    }

    override fun getArtifactResolver(): ArtifactResolver {
        return artifactResolver
    }

    fun add(repository: ModuleComponentRepository<ExternalModuleComponentGraphResolveState>) {
        componentIdResolver.add(repository)
        componentResolver.add(repository)
        artifactResolver.add(repository)
    }
}
