/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.internal.component.external.model

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.model.ModuleConfigurationMetadata
import org.gradle.internal.component.model.ModuleSources

/**
 * The meta-data for a component that is resolved from a module in a binary repository.
 *
 *
 * Implementations of this type should be immutable and thread safe.
 *
 * This type is being replaced by several other interfaces. Try to avoid this interface.
 * @see ComponentGraphResolveMetadata
 *
 * @see ExternalModuleComponentGraphResolveMetadata
 *
 * @see ComponentArtifactResolveMetadata
 */
interface ModuleComponentResolveMetadata : ExternalComponentResolveMetadata, ExternalModuleComponentGraphResolveMetadata {
    /**
     * {@inheritDoc}
     */
    override fun getId(): ModuleComponentIdentifier?

    /**
     * Creates a mutable copy of this metadata.
     *
     * Note that this method can be expensive. Often it is more efficient to use a more specialised mutation method such as [.withSources] rather than this method.
     */
    fun asMutable(): MutableModuleComponentResolveMetadata?

    /**
     * Creates a copy of this meta-data with the given sources.
     */
    fun withSources(sources: ModuleSources): ModuleComponentResolveMetadata?

    /**
     * Creates a copy of this meta-data with the given derivation strategy.
     */
    fun withDerivationStrategy(derivationStrategy: VariantDerivationStrategy): ModuleComponentResolveMetadata?

    override fun getConfiguration(name: String): ModuleConfigurationMetadata?

    /**
     * Creates an artifact for this module. Does not mutate this metadata.
     */
    fun artifact(type: String, extension: String?, classifier: String?): ModuleComponentArtifactMetadata?

    fun optionalArtifact(type: String, extension: String?, classifier: String?): ModuleComponentArtifactMetadata?

    /**
     * Returns the variants of this component
     */
    val variants: ImmutableList<out ComponentVariant>?

    val attributesFactory: AttributesFactory?

    val variantMetadataRules: VariantMetadataRules?

    val variantDerivationStrategy: VariantDerivationStrategy?

    val isExternalVariant: Boolean

    /*
     * When set to false component metadata rules are not cached.
     * Currently, we disable it just for local maven/ivy repository.
     *
     * Default value is true.
     */
    val isComponentMetadataRuleCachingEnabled: Boolean
}
