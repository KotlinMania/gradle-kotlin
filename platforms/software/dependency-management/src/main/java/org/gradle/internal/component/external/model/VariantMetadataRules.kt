/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.api.Action
import org.gradle.api.artifacts.DependencyConstraintMetadata
import org.gradle.api.artifacts.DependencyConstraintsMetadata
import org.gradle.api.artifacts.DirectDependenciesMetadata
import org.gradle.api.artifacts.DirectDependencyMetadata
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.MutableVariantFilesMetadata
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.MutableCapabilitiesMetadata
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.DependencyMetadataRules
import org.gradle.internal.component.model.VariantAttributesRules
import org.gradle.internal.component.model.VariantFilesRules
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser
import java.util.concurrent.ConcurrentHashMap

open class VariantMetadataRules private constructor(
    private val attributesFactory: AttributesFactory,
    private val moduleVersionId: ModuleVersionIdentifier,
    private val baseAttributes: AttributeContainerInternal
) {
    val additionalVariants: MutableList<AdditionalVariant> = ArrayList<AdditionalVariant>()

    private var dependencyMetadataRules: DependencyMetadataRules? = null
    private var variantAttributesRules: VariantAttributesRules? = null
    private var capabilitiesRules: MutableList<VariantAction<in MutableCapabilitiesMetadata>>? = null
    private var variantFilesRules: VariantFilesRules? = null

    // If two configurations have a dependency on the same module, there is a chance they can be
    // resolved concurrently. Dependency resolution exercises this code when performing attribute
    // matching, so this map must support concurrent modification.
    private val variantAttributes: MutableMap<String, AttributeContainerInternal> = ConcurrentHashMap<String, AttributeContainerInternal>()

    constructor(attributesFactory: AttributesFactory, moduleVersionId: ModuleVersionIdentifier) : this(attributesFactory, moduleVersionId, attributesFactory.mutable())

    open fun getAttributes(variantName: String?): AttributeContainerInternal {
        if (variantName == null) {
            return baseAttributes
        } else {
            return variantAttributes.computeIfAbsent(variantName) { name: String? -> attributesFactory.mutable(baseAttributes) }
        }
    }

    protected open fun joinVariantAttributes(variant: VariantResolveMetadata, parent: AttributeContainerInternal): AttributeContainerInternal {
        val variantAttrs = getAttributes(variant.name)
        return attributesFactory.join(parent, variantAttrs)
    }

    fun applyVariantAttributeRules(variant: VariantResolveMetadata, parent: AttributeContainerInternal): ImmutableAttributes {
        val joined = joinVariantAttributes(variant, parent)
        if (variantAttributesRules != null) {
            return variantAttributesRules!!.execute(variant, joined)
        }
        return joined.asImmutable()
    }

    fun applyCapabilitiesRules(variant: VariantResolveMetadata, capabilities: ImmutableCapabilities): ImmutableCapabilities {
        if (capabilitiesRules == null) {
            return capabilities
        }

        val mutableCapabilities: DefaultMutableCapabilitiesMetadata?
        if (capabilities.asSet().isEmpty()) {
            // we must add the implicit capability here because it is assumed that if there's a rule
            // "addCapability" would effectively _add_ a capability, so the implicit one must not be forgotten
            mutableCapabilities = DefaultMutableCapabilitiesMetadata(
                ImmutableCapabilities.of(
                    DefaultImmutableCapability(moduleVersionId.getGroup(), moduleVersionId.getName(), moduleVersionId.getVersion())
                )
            )
        } else {
            mutableCapabilities = DefaultMutableCapabilitiesMetadata(capabilities)
        }

        for (action in capabilitiesRules) {
            action.maybeExecute(variant, mutableCapabilities)
        }

        return mutableCapabilities.asImmutableCapabilities()
    }

    fun <T : ModuleDependencyMetadata?> applyDependencyMetadataRules(variant: VariantResolveMetadata, configDependencies: MutableList<T?>): MutableList<out ModuleDependencyMetadata> {
        if (dependencyMetadataRules != null) {
            return dependencyMetadataRules!!.execute<T?>(variant, configDependencies)
        }
        return configDependencies
    }

    fun <T : ComponentArtifactMetadata?> applyVariantFilesMetadataRulesToArtifacts(
        variant: VariantResolveMetadata,
        declaredArtifacts: ImmutableList<T?>,
        componentIdentifier: ModuleComponentIdentifier
    ): ImmutableList<T?> {
        if (variantFilesRules != null) {
            return variantFilesRules!!.executeForArtifacts<T?>(variant, declaredArtifacts, componentIdentifier)
        }
        return declaredArtifacts
    }

    fun <T : ComponentVariant.File?> applyVariantFilesMetadataRulesToFiles(
        variant: VariantResolveMetadata,
        declaredFiles: ImmutableList<T?>,
        componentIdentifier: ModuleComponentIdentifier
    ): ImmutableList<T?> {
        if (variantFilesRules != null) {
            return variantFilesRules!!.executeForFiles<T?>(variant, declaredFiles, componentIdentifier)
        }
        return declaredFiles
    }

    open fun addDependencyAction(
        instantiator: Instantiator,
        dependencyNotationParser: NotationParser<Any, DirectDependencyMetadata>,
        dependencyConstraintNotationParser: NotationParser<Any, DependencyConstraintMetadata>,
        action: VariantAction<in DirectDependenciesMetadata>
    ) {
        if (dependencyMetadataRules == null) {
            dependencyMetadataRules = DependencyMetadataRules(instantiator, dependencyNotationParser, dependencyConstraintNotationParser, attributesFactory)
        }
        dependencyMetadataRules!!.addDependencyAction(action)
    }

    open fun addDependencyConstraintAction(
        instantiator: Instantiator,
        dependencyNotationParser: NotationParser<Any, DirectDependencyMetadata>,
        dependencyConstraintNotationParser: NotationParser<Any, DependencyConstraintMetadata>,
        action: VariantAction<in DependencyConstraintsMetadata>
    ) {
        if (dependencyMetadataRules == null) {
            dependencyMetadataRules = DependencyMetadataRules(instantiator, dependencyNotationParser, dependencyConstraintNotationParser, attributesFactory)
        }
        dependencyMetadataRules!!.addDependencyConstraintAction(action)
    }

    open fun addAttributesAction(attributesFactory: AttributesFactory, action: VariantAction<in AttributeContainer>) {
        if (variantAttributesRules == null) {
            variantAttributesRules = VariantAttributesRules(attributesFactory)
        }
        variantAttributesRules!!.addAttributesAction(action)
    }

    open fun addCapabilitiesAction(action: VariantAction<in MutableCapabilitiesMetadata>) {
        if (capabilitiesRules == null) {
            capabilitiesRules = ArrayList<VariantAction<in MutableCapabilitiesMetadata>>(1)
        }
        capabilitiesRules!!.add(action)
    }

    fun addVariantFilesAction(action: VariantAction<in MutableVariantFilesMetadata>) {
        if (variantFilesRules == null) {
            variantFilesRules = VariantFilesRules()
        }
        variantFilesRules!!.addFilesAction(action)
    }

    fun addVariant(name: String) {
        additionalVariants.add(AdditionalVariant(name))
    }

    fun addVariant(name: String, basedOn: String, lenient: Boolean) {
        additionalVariants.add(AdditionalVariant(name, basedOn, lenient))
    }

    /**
     * A variant action is an action which is only executed if it matches the name of the variant.
     * @param <T> the type of the action subject
    </T> */
    class VariantAction<T>
    /**
     * @param variantName The variant name to match. If null, all variants are matched.
     * @param delegate The action to execute upon matching.
     */(private val variantName: String?, private val delegate: Action<in T?>) {
        /**
         * Executes the underlying action if the supplied variant matches.
         *
         * @param variant the variant metadata, used to check if the rule applies
         * @param subject the subject of the rule
         */
        fun maybeExecute(variant: VariantResolveMetadata, subject: T?) {
            if (variantName == null || variantName == variant.name) {
                delegate.execute(subject)
            }
        }
    }

    private class ImmutableRules : VariantMetadataRules(null, null, null) {
        override fun getAttributes(variantName: String?): AttributeContainerInternal {
            return ImmutableAttributes.EMPTY
        }

        override fun joinVariantAttributes(variant: VariantResolveMetadata, parent: AttributeContainerInternal): AttributeContainerInternal {
            return parent
        }

        override fun addDependencyAction(
            instantiator: Instantiator,
            dependencyNotationParser: NotationParser<Any, DirectDependencyMetadata>,
            dependencyConstraintNotationParser: NotationParser<Any, DependencyConstraintMetadata>,
            action: VariantAction<in DirectDependenciesMetadata>
        ) {
            throw UnsupportedOperationException("You are probably trying to add a dependency rule to something that wasn't supposed to be mutable")
        }

        override fun addDependencyConstraintAction(
            instantiator: Instantiator,
            dependencyNotationParser: NotationParser<Any, DirectDependencyMetadata>,
            dependencyConstraintNotationParser: NotationParser<Any, DependencyConstraintMetadata>,
            action: VariantAction<in DependencyConstraintsMetadata>
        ) {
            throw UnsupportedOperationException("You are probably trying to add a dependency constraint rule to something that wasn't supposed to be mutable")
        }

        override fun addAttributesAction(attributesFactory: AttributesFactory, action: VariantAction<in AttributeContainer>) {
            throw UnsupportedOperationException("You are probably trying to add a variant attribute to something that wasn't supposed to be mutable")
        }

        override fun addCapabilitiesAction(action: VariantAction<in MutableCapabilitiesMetadata>) {
            throw UnsupportedOperationException("You are probably trying to change capabilities of something that wasn't supposed to be mutable")
        }

        companion object {
            private val INSTANCE = ImmutableRules()
        }
    }

    companion object {
        fun noOp(): VariantMetadataRules {
            return ImmutableRules.Companion.INSTANCE
        }
    }
}
