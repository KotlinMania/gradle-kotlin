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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Transformer
import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ComponentMetadataBuilder
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor.processMetadata
import org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory.createComponentMetadataProcessor
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.MetadataResolutionContext
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MavenVersionUtils
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataAdapter
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.component.external.model.ExternalComponentResolveMetadata
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.component.external.model.ivy.IvyModuleResolveMetadata
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.rules.RuleAction.execute
import java.lang.Boolean
import kotlin.String
import kotlin.Suppress
import kotlin.compareTo

internal class DefaultMetadataProvider(private val resolveState: ModuleComponentResolveState) : MetadataProvider {
    private var cachedResult: BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?>? = null
    private var cachedComponentMetadata: ComponentMetadata? = null
    private var computedMetadata = false

    override fun getComponentMetadata(): ComponentMetadata? {
        if (computedMetadata) {
            return cachedComponentMetadata
        }

        cachedComponentMetadata = computeMetadata()
        computedMetadata = true
        return cachedComponentMetadata
    }

    private fun computeMetadata(): ComponentMetadata? {
        var metadata: ComponentMetadata? = null
        val componentMetadataSupplier: InstantiatingAction<ComponentMetadataSupplierDetails?>? = resolveState.componentMetadataSupplier
        if (componentMetadataSupplier != null) {
            metadata = getComponentMetadataFromSupplier(componentMetadataSupplier)
        }
        if (metadata != null) {
            metadata = transformThroughComponentMetadataRules(componentMetadataSupplier!!, metadata)
        } else if (resolve()) {
            @Suppress("deprecation") val legacyMetadata: ExternalComponentResolveMetadata = cachedResult!!.metaData.getLegacyMetadata()
            metadata = ComponentMetadataAdapter(legacyMetadata)
        }
        return metadata
    }

    private fun transformThroughComponentMetadataRules(componentMetadataSupplier: InstantiatingAction<ComponentMetadataSupplierDetails?>, metadata: ComponentMetadata?): ComponentMetadata? {
        var metadata = metadata
        val resolutionContext = DefaultMetadataResolutionContext(resolveState.cacheExpirationControl, componentMetadataSupplier.getInstantiator())
        metadata = resolveState.componentMetadataProcessorFactory.createComponentMetadataProcessor(resolutionContext).processMetadata(metadata)
        return metadata
    }

    private fun getComponentMetadataFromSupplier(componentMetadataSupplier: InstantiatingAction<ComponentMetadataSupplierDetails?>?): ComponentMetadata? {
        val metadata: ComponentMetadata?
        val id: ModuleVersionIdentifier? = DefaultModuleVersionIdentifier.newId(resolveState.id)
        metadata =
            resolveState.componentMetadataSupplierExecutor.< BuildableComponentMetadataSupplierDetails > execute < org . gradle . api . internal . artifacts . ivyservice . ivyresolve . DefaultMetadataProvider . BuildableComponentMetadataSupplierDetails ? > (id, componentMetadataSupplier, org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DefaultMetadataProvider.Companion.TO_COMPONENT_METADATA, { id1 ->
            val builder = SimpleComponentMetadataBuilder(id1, resolveState.attributesFactory)
            DefaultMetadataProvider.BuildableComponentMetadataSupplierDetails(builder)
        }, resolveState.cacheExpirationControl)
        return metadata
    }

    override fun getIvyModuleDescriptor(): IvyModuleDescriptor? {
        if (resolve()) {
            @Suppress("deprecation") val legacyMetadata: ExternalComponentResolveMetadata? = cachedResult!!.metaData.getLegacyMetadata()
            if (legacyMetadata is IvyModuleResolveMetadata) {
                val ivyMetadata = legacyMetadata
                return DefaultIvyModuleDescriptor(ivyMetadata.extraAttributes, ivyMetadata.branch, ivyMetadata.status!!)
            }
        }
        return null
    }

    private fun resolve(): Boolean {
        if (cachedResult == null) {
            cachedResult = resolveState.resolve()
        }
        return cachedResult!!.state === BuildableModuleComponentMetaDataResolveResult.State.Resolved
    }

    override fun isUsable(): Boolean {
        return cachedResult == null || cachedResult!!.state === BuildableModuleComponentMetaDataResolveResult.State.Resolved
    }

    val result: BuildableModuleComponentMetaDataResolveResult<*>?
        get() = cachedResult

    /**
     * This class bridges from the public type available in metadata suppliers ([ComponentMetadataBuilder]
     * to the complete type ([ComponentMetadata]) which provides more than what we want to expose in those
     * rules. In particular, the builder exposes setters, that we don't want on the component metadata type.
     */
    private class SimpleComponentMetadataBuilder(private val id: ModuleVersionIdentifier, attributesFactory: AttributesFactory) : ComponentMetadataBuilder {
        private var mutated = false // used internally to determine if a rule effectively did something

        private var statusScheme = ExternalComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        private val attributes: AttributeContainerInternal

        init {
            this.attributes = attributesFactory.mutable()
            this.attributes.attribute<String?>(
                ProjectInternal.STATUS_ATTRIBUTE, MavenVersionUtils.inferStatusFromEffectiveVersion(
                    id.getVersion()
                )
            )
        }

        override fun setStatus(status: String) {
            attributes.attribute<String?>(ProjectInternal.STATUS_ATTRIBUTE, status)
            mutated = true
        }

        override fun setStatusScheme(scheme: MutableList<String?>) {
            this.statusScheme = scheme
            mutated = true
        }

        override fun attributes(attributesConfiguration: Action<in AttributeContainer?>) {
            mutated = true
            attributesConfiguration.execute(attributes)
        }

        override fun getAttributes(): AttributeContainer {
            mutated = true
            return attributes
        }

        fun validateAttributeTypes(attributes: AttributeContainerInternal): ImmutableAttributes? {
            var invalidAttributes: MutableList<Attribute<*>>? = null
            for (attribute in attributes.keySet()) {
                if (!isValidType(attribute)) {
                    if (invalidAttributes == null) {
                        invalidAttributes = ArrayList<Attribute<*>>()
                    }
                    invalidAttributes.add(attribute)
                }
            }
            maybeThrowValidationError(invalidAttributes)
            return attributes.asImmutable()
        }

        fun maybeThrowValidationError(invalidAttributes: MutableList<Attribute<*>>?) {
            if (invalidAttributes != null) {
                val fm = TreeFormatter()
                fm.node("Invalid attributes types have been provider by component metadata supplier. Attributes must either be strings or booleans")
                fm.startChildren()
                for (invalidAttribute in invalidAttributes) {
                    fm.node("Attribute '" + invalidAttribute.getName() + "' has type " + invalidAttribute.getType())
                }
                fm.endChildren()
                throw InvalidUserDataException(fm.toString())
            }
        }

        fun build(): ComponentMetadata {
            return UserProvidedMetadata(id, statusScheme, validateAttributeTypes(attributes)!!)
        }

        companion object {
            private fun isValidType(attribute: Attribute<*>): Boolean {
                val type: Class<*> = attribute.getType()
                return type == String::class.java || type == Boolean::class.java || type == Boolean.TYPE
            }
        }
    }

    private inner class BuildableComponentMetadataSupplierDetails(private val builder: SimpleComponentMetadataBuilder) : ComponentMetadataSupplierDetails {
        override fun getId(): ModuleComponentIdentifier {
            return resolveState.id
        }

        override fun getResult(): ComponentMetadataBuilder {
            return builder
        }

        val executionResult: ComponentMetadata?
            get() {
                if (builder.mutated) {
                    return builder.build()
                }
                return null
            }
    }

    private class DefaultMetadataResolutionContext(val cacheExpirationControl: CacheExpirationControl?, val injectingInstantiator: Instantiator?) : MetadataResolutionContext
    companion object {
        private val TO_COMPONENT_METADATA = Transformer { obj: BuildableComponentMetadataSupplierDetails? -> obj!!.executionResult }
    }
}
