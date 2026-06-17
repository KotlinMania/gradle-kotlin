/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult

class RepositoryChainDependencyToComponentIdResolver(
    componentChooser: VersionedComponentChooser,
    versionParser: VersionParser,
    attributesFactory: AttributesFactory,
    componentMetadataProcessorFactory: ComponentMetadataProcessorFactory,
    componentMetadataSupplierRuleExecutor: ComponentMetadataSupplierRuleExecutor,
    cacheExpirationControl: CacheExpirationControl
) : DependencyToComponentIdResolver {
    private val dynamicRevisionResolver: DynamicVersionResolver

    init {
        this.dynamicRevisionResolver = DynamicVersionResolver(
            componentChooser,
            versionParser,
            attributesFactory,
            componentMetadataProcessorFactory,
            componentMetadataSupplierRuleExecutor,
            cacheExpirationControl
        )
    }

    fun add(repository: ModuleComponentRepository<ExternalModuleComponentGraphResolveState>) {
        dynamicRevisionResolver.add(repository)
    }

    override fun resolve(
        selector: ComponentSelector,
        overrideMetadata: ComponentOverrideMetadata,
        acceptor: VersionSelector,
        rejector: VersionSelector?,
        result: BuildableComponentIdResolveResult,
        consumerAttributes: ImmutableAttributes
    ) {
        if (selector is ModuleComponentSelector) {
            val module = selector
            if (acceptor.isDynamic) {
                dynamicRevisionResolver.resolve(module, overrideMetadata, acceptor, rejector, consumerAttributes, result)
            } else {
                val version = acceptor.selector
                val moduleId = module.getModuleIdentifier()
                val id = newId(moduleId, version)
                val mvId: ModuleVersionIdentifier? = DefaultModuleVersionIdentifier.newId(moduleId, version)
                if (rejector != null && rejector.accept(version)) {
                    result.rejected(id, mvId)
                } else {
                    result.resolved(id, mvId)
                }
            }
        }
    }
}
