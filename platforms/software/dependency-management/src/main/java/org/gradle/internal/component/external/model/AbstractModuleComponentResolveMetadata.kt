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
package org.gradle.internal.component.external.model

import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.ImmutableModuleSources
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.ModuleSources
import java.util.Optional

internal abstract class AbstractModuleComponentResolveMetadata : ModuleComponentResolveMetadata {
    val attributesFactory: AttributesFactory
    private val moduleVersionIdentifier: ModuleVersionIdentifier?
    private val componentIdentifier: ModuleComponentIdentifier?
    private val changing: Boolean
    private val missing: Boolean
    private val statusScheme: MutableList<String?>?
    private val moduleSources: ImmutableModuleSources
    @JvmField
    val variants: ImmutableList<out ComponentVariant?>
    private val attributes: ImmutableAttributes
    private val platformOwners: ImmutableList<out VirtualComponentIdentifier?>?
    private val schema: ImmutableAttributesSchema?
    @JvmField
    val variantDerivationStrategy: VariantDerivationStrategy?
    val isExternalVariant: Boolean
    val isComponentMetadataRuleCachingEnabled: Boolean

    constructor(metadata: AbstractMutableModuleComponentResolveMetadata) {
        this.componentIdentifier = metadata.id
        this.moduleVersionIdentifier = metadata.moduleVersionId
        this.changing = metadata.isChanging
        this.missing = metadata.isMissing
        this.statusScheme = metadata.statusScheme
        this.moduleSources = ImmutableModuleSources.of(metadata.sources)
        this.attributesFactory = metadata.attributesFactory!!
        this.schema = metadata.getAttributesSchema()
        this.attributes = extractAttributes(metadata)
        this.variants = metadata.getVariants()
        this.platformOwners = if (metadata.platformOwners == null) ImmutableList.of<VirtualComponentIdentifier?>() else ImmutableList.copyOf(metadata.platformOwners)
        this.variantDerivationStrategy = metadata.getVariantDerivationStrategy()
        this.isExternalVariant = metadata.isExternalVariant
        this.isComponentMetadataRuleCachingEnabled = metadata.isComponentMetadataRuleCachingEnabled
    }

    constructor(metadata: AbstractModuleComponentResolveMetadata, variants: ImmutableList<out ComponentVariant?>) {
        this.componentIdentifier = metadata.getId()
        this.moduleVersionIdentifier = metadata.getModuleVersionId()
        this.changing = metadata.isChanging()
        this.missing = metadata.isMissing()
        this.statusScheme = metadata.getStatusScheme()
        this.moduleSources = ImmutableModuleSources.of(metadata.getSources())
        this.attributesFactory = metadata.attributesFactory
        this.schema = metadata.getAttributesSchema()
        this.attributes = metadata.getAttributes()
        this.variants = variants
        this.platformOwners = metadata.getPlatformOwners()
        this.variantDerivationStrategy = metadata.variantDerivationStrategy
        this.isExternalVariant = metadata.isExternalVariant
        this.isComponentMetadataRuleCachingEnabled = metadata.isComponentMetadataRuleCachingEnabled
    }

    constructor(metadata: AbstractModuleComponentResolveMetadata, sources: ModuleSources, derivationStrategy: VariantDerivationStrategy?) {
        this.componentIdentifier = metadata.componentIdentifier
        this.moduleVersionIdentifier = metadata.moduleVersionIdentifier
        this.changing = metadata.changing
        this.missing = metadata.missing
        this.statusScheme = metadata.statusScheme
        this.attributesFactory = metadata.attributesFactory
        this.schema = metadata.schema
        this.attributes = metadata.attributes
        this.variants = metadata.variants
        this.platformOwners = metadata.platformOwners
        this.moduleSources = ImmutableModuleSources.of(sources)
        this.variantDerivationStrategy = derivationStrategy
        this.isExternalVariant = metadata.isExternalVariant
        this.isComponentMetadataRuleCachingEnabled = metadata.isComponentMetadataRuleCachingEnabled
    }

    override fun isChanging(): Boolean {
        return changing
    }

    override fun isMissing(): Boolean {
        return missing
    }

    override fun getStatusScheme(): MutableList<String?>? {
        return statusScheme
    }

    override fun getId(): ModuleComponentIdentifier? {
        return componentIdentifier
    }

    override fun getModuleVersionId(): ModuleVersionIdentifier? {
        return moduleVersionIdentifier
    }

    override fun getSources(): ModuleSources {
        return moduleSources
    }

    override fun toString(): String {
        return componentIdentifier!!.getDisplayName()
    }

    override fun getAttributesSchema(): ImmutableAttributesSchema? {
        return schema
    }

    override fun getAttributes(): ImmutableAttributes {
        return attributes
    }

    override fun getStatus(): String? {
        return attributes.getAttribute<String?>(ProjectInternal.STATUS_ATTRIBUTE)
    }

    override fun artifact(type: String, extension: String?, classifier: String?): ModuleComponentArtifactMetadata? {
        val ivyArtifactName: IvyArtifactName = DefaultIvyArtifactName(getModuleVersionId()!!.getName(), type, extension, classifier)
        return DefaultModuleComponentArtifactMetadata(getId(), ivyArtifactName)
    }

    override fun optionalArtifact(type: String, extension: String?, classifier: String?): ModuleComponentArtifactMetadata? {
        val ivyArtifactName: IvyArtifactName = DefaultIvyArtifactName(getModuleVersionId()!!.getName(), type, extension, classifier)
        return ModuleComponentOptionalArtifactMetadata(getId()!!, ivyArtifactName)
    }

    /**
     * If there are no variants defined in the metadata, but the implementation knows how to provide variants it can do that here.
     * If it can not provide variants, absent must be returned to fall back to traditional configuration selection.
     */
    protected open fun maybeDeriveVariants(): Optional<MutableList<out ExternalModuleVariantGraphResolveMetadata?>?> {
        return Optional.empty<MutableList<out ExternalModuleVariantGraphResolveMetadata?>?>()
    }

    override fun getPlatformOwners(): ImmutableList<out VirtualComponentIdentifier?>? {
        return platformOwners
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as AbstractModuleComponentResolveMetadata
        return changing == that.changing && missing == that.missing && this.isExternalVariant == that.isExternalVariant && isComponentMetadataRuleCachingEnabled == that.isComponentMetadataRuleCachingEnabled && Objects.equal(
            moduleVersionIdentifier,
            that.moduleVersionIdentifier
        )
                && Objects.equal(componentIdentifier, that.componentIdentifier)
                && Objects.equal(statusScheme, that.statusScheme)
                && Objects.equal(moduleSources, that.moduleSources)
                && Objects.equal(attributes, that.attributes)
                && Objects.equal(variants, that.variants)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(
            moduleVersionIdentifier,
            componentIdentifier,
            changing,
            missing,
            this.isExternalVariant,
            isComponentMetadataRuleCachingEnabled,
            statusScheme,
            moduleSources,
            attributes,
            variants
        )
    }

    companion object {
        private fun extractAttributes(metadata: AbstractMutableModuleComponentResolveMetadata): ImmutableAttributes {
            return (metadata.attributes as AttributeContainerInternal).asImmutable()
        }
    }
}
