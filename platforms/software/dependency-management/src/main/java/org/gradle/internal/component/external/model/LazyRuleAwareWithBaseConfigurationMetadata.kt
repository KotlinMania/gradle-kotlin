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
package org.gradle.internal.component.external.model

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.DefaultVariantMetadata
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.ModuleConfigurationMetadata
import org.gradle.internal.component.model.VariantIdentifier

/**
 * A configuration representing an additional variant of a published component added by a component metadata rule.
 * It can be backed by an existing configuration/variant (base) or can initially be empty (base = null).
 */
internal class LazyRuleAwareWithBaseConfigurationMetadata(
    private val name: String,
    private val id: VariantIdentifier,
    private val componentId: ModuleComponentIdentifier,
    private val base: ModuleConfigurationMetadata?,
    private val attributesFactory: AttributesFactory,
    private val componentLevelAttributes: ImmutableAttributes?,
    private val variantMetadataRules: VariantMetadataRules,
    private val excludes: ImmutableList<ExcludeMetadata?>?,
    private val externalVariant: Boolean
) : ModuleConfigurationMetadata {
    private var computedDependencies: MutableList<out ModuleDependencyMetadata?>? = null
    private var computedAttributes: ImmutableAttributes? = null
    private var computedCapabilities: ImmutableCapabilities? = null
    private var computedArtifacts: ImmutableList<out ComponentArtifactMetadata?>? = null

    override fun getName(): String {
        return name
    }

    override fun getId(): VariantIdentifier {
        return id
    }

    val identifier: VariantResolveMetadata.Identifier?
        get() = null

    override fun getDependencies(): MutableList<out ModuleDependencyMetadata?>? {
        if (computedDependencies == null) {
            computedDependencies = variantMetadataRules.applyDependencyMetadataRules(this, if (base == null) ImmutableList.of() else base.getDependencies())
        }
        return computedDependencies
    }

    override fun getAttributes(): ImmutableAttributes? {
        if (computedAttributes == null) {
            computedAttributes = variantMetadataRules.applyVariantAttributeRules(
                this,
                (if (base != null) attributesFactory.concat(base.attributes, componentLevelAttributes) else componentLevelAttributes)!!
            )
        }
        return computedAttributes
    }

    val artifacts: ImmutableList<out ComponentArtifactMetadata?>
        get() {
            if (computedArtifacts == null) {
                computedArtifacts = variantMetadataRules.applyVariantFilesMetadataRulesToArtifacts<ComponentArtifactMetadata?>(
                    this,
                    if (base == null) ImmutableList.of<ComponentArtifactMetadata?>() else base.artifacts,
                    componentId
                )
            }
            return computedArtifacts
        }

    override fun getCapabilities(): ImmutableCapabilities? {
        if (computedCapabilities == null) {
            computedCapabilities = variantMetadataRules.applyCapabilitiesRules(this, if (base == null) ImmutableCapabilities.Companion.EMPTY else base.capabilities)
        }
        return computedCapabilities
    }

    val artifactVariants: MutableSet<out VariantResolveMetadata?>?
        get() = ImmutableSet.of<DefaultVariantMetadata?>(
            DefaultVariantMetadata(
                name,
                null,
                asDescribable()!!,
                getAttributes()!!,
                this.artifacts,
                getCapabilities()!!
            )
        )

    override fun asDescribable(): DisplayName? {
        return Describables.of(id.componentId, "configuration", name)
    }

    override fun artifact(artifact: IvyArtifactName?): ComponentArtifactMetadata? {
        return DefaultModuleComponentArtifactMetadata(componentId, artifact)
    }

    override fun getHierarchy(): ImmutableSet<String?>? {
        return ImmutableSet.of<String?>(name)
    }

    override fun getExcludes(): ImmutableList<ExcludeMetadata?>? {
        return excludes
    }

    override fun isTransitive(): Boolean {
        return true
    }

    override fun isVisible(): Boolean {
        return true
    }

    override fun isExternalVariant(): Boolean {
        return externalVariant
    }
}
