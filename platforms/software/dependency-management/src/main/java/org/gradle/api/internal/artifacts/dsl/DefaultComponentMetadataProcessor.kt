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
package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Transformer
import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.VariantMetadata
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor
import org.gradle.api.internal.artifacts.MetadataResolutionContext
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.UserProvidedMetadata
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataDetailsAdapter
import org.gradle.api.internal.artifacts.repositories.resolver.DependencyConstraintMetadataImpl
import org.gradle.api.internal.artifacts.repositories.resolver.DirectDependencyMetadataImpl
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.Actions
import org.gradle.internal.action.ConfigurableRule
import org.gradle.internal.action.DefaultConfigurableRules
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.ivy.DefaultIvyModuleResolveMetadata
import org.gradle.internal.component.external.model.ivy.RealisedIvyModuleResolveMetadata.Companion.transform
import org.gradle.internal.component.external.model.maven.DefaultMavenModuleResolveMetadata
import org.gradle.internal.component.external.model.maven.RealisedMavenModuleResolveMetadata.Companion.transform
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.caching.ComponentMetadataRuleExecutor
import org.gradle.internal.rules.RuleAction
import org.gradle.internal.rules.SpecRuleAction
import org.gradle.internal.serialize.InputStreamBackedDecoder
import org.gradle.internal.serialize.OutputStreamBackedEncoder
import org.gradle.internal.typeconversion.NotationParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.Boolean
import java.lang.String
import kotlin.Any
import kotlin.Exception
import kotlin.IllegalStateException
import kotlin.Int
import kotlin.RuntimeException
import kotlin.Throwable
import kotlin.synchronized
import kotlin.text.format

class DefaultComponentMetadataProcessor(
    private val metadataRuleContainer: ComponentMetadataRuleContainer,
    private val instantiator: Instantiator,
    private val dependencyMetadataNotationParser: NotationParser<Any, DirectDependencyMetadataImpl>,
    private val dependencyConstraintMetadataNotationParser: NotationParser<Any, DependencyConstraintMetadataImpl>,
    private val componentIdentifierNotationParser: NotationParser<Any, ComponentIdentifier>,
    private val attributesFactory: AttributesFactory,
    private val ruleExecutor: ComponentMetadataRuleExecutor,
    private val platformSupport: PlatformSupport,
    private val metadataResolutionContext: MetadataResolutionContext
) : ComponentMetadataProcessor {
    private fun maybeForceRealisation(metadata: ModuleComponentResolveMetadata): ModuleComponentResolveMetadata {
        var metadata = metadata
        if (FORCE_REALIZE) {
            metadata = realizeMetadata(metadata)
            metadata = forceSerialization(metadata)
        }
        return metadata
    }

    private fun forceSerialization(metadata: ModuleComponentResolveMetadata): ModuleComponentResolveMetadata {
        var metadata = metadata
        val serializer = ruleExecutor.componentMetadataContextSerializer
        try {
            ByteArrayOutputStream().use { baos ->
                serializer!!.write(OutputStreamBackedEncoder(baos), metadata)
                // TODO: CC cannot enable this assertion because moduleSource is not serialized, so doesn't appear in the deserialized form
                //assert metadata.equals(rereadMetadata);
                metadata = serializer.read(InputStreamBackedDecoder(ByteArrayInputStream(baos.toByteArray())))!!
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
        return metadata
    }

    override fun processMetadata(origin: ModuleComponentResolveMetadata): ModuleComponentResolveMetadata {
        val curStrategy = metadataRuleContainer.getVariantDerivationStrategy()
        val metadata = origin.withDerivationStrategy(curStrategy)
        val updatedMetadata: ModuleComponentResolveMetadata
        if (metadataRuleContainer.isEmpty()) {
            updatedMetadata = maybeForceRealisation(metadata!!)
        } else if (metadataRuleContainer.isClassBasedRulesOnly()) {
            val action = collectRulesAndCreateAction(metadataRuleContainer.getOnlyClassRules(), metadata!!.moduleVersionId, metadataResolutionContext.injectingInstantiator)
            if (action is InstantiatingAction<*>) {
                val ia = action as InstantiatingAction<ComponentMetadataContext>
                if (shouldCacheComponentMetadataRule(ia, metadata)) {
                    updatedMetadata = processClassRuleWithCaching(ia, metadata, metadataResolutionContext)
                } else {
                    val mutableMetadata = metadata.asMutable()
                    processClassRule(action, metadata, createDetails(mutableMetadata!!))
                    updatedMetadata = maybeForceRealisation(mutableMetadata.asImmutable()!!)
                }
            } else {
                updatedMetadata = maybeForceRealisation(metadata)
            }
        } else {
            val mutableMetadata = metadata!!.asMutable()
            val details = createDetails(mutableMetadata!!)
            processAllRules(metadata, details, metadata.moduleVersionId)
            updatedMetadata = maybeForceRealisation(mutableMetadata.asImmutable()!!)
        }

        if (!updatedMetadata.statusScheme!!.contains(updatedMetadata.status)) {
            throw ModuleVersionResolveException(
                updatedMetadata.moduleVersionId,
                { String.format("Unexpected status '%s' specified for %s. Expected one of: %s", updatedMetadata.status, updatedMetadata.getId()!!.getDisplayName(), updatedMetadata.statusScheme) })
        }
        return updatedMetadata
    }

    private fun shouldCacheComponentMetadataRule(action: InstantiatingAction<ComponentMetadataContext>, metadata: ModuleComponentResolveMetadata): Boolean {
        return action.getRules().isCacheable() && metadata.isComponentMetadataRuleCachingEnabled
    }

    protected fun createDetails(mutableMetadata: MutableModuleComponentResolveMetadata): ComponentMetadataDetails {
        return instantiator.newInstance<ComponentMetadataDetailsAdapter>(
            ComponentMetadataDetailsAdapter::class.java,
            mutableMetadata,
            instantiator,
            dependencyMetadataNotationParser,
            dependencyConstraintMetadataNotationParser,
            componentIdentifierNotationParser,
            platformSupport
        )
    }

    override fun processMetadata(metadata: ComponentMetadata): ComponentMetadata {
        val updatedMetadata: ComponentMetadata
        if (metadataRuleContainer.isEmpty()) {
            updatedMetadata = metadata
        } else {
            val details = ShallowComponentMetadataAdapter(metadata, attributesFactory)
            processAllRules(null, details, metadata.getId())
            updatedMetadata = details.asImmutable()
        }
        if (!updatedMetadata.getStatusScheme().contains(updatedMetadata.getStatus())) {
            throw ModuleVersionResolveException(
                updatedMetadata.getId(),
                org.gradle.internal.Factory {
                    kotlin.String.format(
                        "Unexpected status '%s' specified for %s. Expected one of: %s",
                        updatedMetadata.getStatus(),
                        updatedMetadata.getId().toString(),
                        updatedMetadata.getStatusScheme()
                    )
                })
        }
        return updatedMetadata
    }

    val rulesHash: Int
        get() = metadataRuleContainer.getRulesHash()

    private fun processAllRules(metadata: ModuleComponentResolveMetadata, details: ComponentMetadataDetails, id: ModuleVersionIdentifier) {
        for (wrapper in metadataRuleContainer) {
            if (wrapper.isClassBased()) {
                val rules = wrapper.getClassRules()
                val action = collectRulesAndCreateAction(rules, id, metadataResolutionContext.injectingInstantiator)
                processClassRule(action, metadata, details)
            } else {
                processRule(wrapper.getRule(), metadata, details)
            }
        }
    }

    private fun processClassRule(action: Action<ComponentMetadataContext>, metadata: ModuleComponentResolveMetadata, details: ComponentMetadataDetails) {
        val componentMetadataContext = DefaultComponentMetadataContext(details, metadata)
        try {
            action.execute(componentMetadataContext)
        } catch (e: InvalidUserCodeException) {
            throw e
        } catch (e: Exception) {
            throw InvalidUserCodeException(kotlin.String.format("There was an error while evaluating a component metadata rule for %s.", details.getId()), e)
        }
    }

    private fun processClassRuleWithCaching(
        action: InstantiatingAction<ComponentMetadataContext>,
        metadata: ModuleComponentResolveMetadata,
        metadataResolutionContext: MetadataResolutionContext
    ): ModuleComponentResolveMetadata {
        try {
            return ruleExecutor.execute<WrappingComponentMetadataContext?>(
                metadata,
                action,
                org.gradle.api.internal.artifacts.dsl.DefaultComponentMetadataProcessor.Companion.DETAILS_TO_RESULT,
                org.gradle.api.Transformer { moduleVersionIdentifier: org.gradle.internal.component.external.model.ModuleComponentResolveMetadata? ->
                    org.gradle.api.internal.artifacts.dsl.WrappingComponentMetadataContext(
                        metadata,
                        instantiator,
                        dependencyMetadataNotationParser,
                        dependencyConstraintMetadataNotationParser,
                        componentIdentifierNotationParser,
                        platformSupport
                    )
                },
                metadataResolutionContext.cacheExpirationControl
            )!!
        } catch (e: InvalidUserCodeException) {
            throw e
        } catch (e: Exception) {
            throw InvalidUserCodeException(String.format("There was an error while evaluating a component metadata rule for %s.", metadata.moduleVersionId), e)
        }
    }

    private fun collectRulesAndCreateAction(rules: MutableCollection<SpecConfigurableRule>, id: ModuleVersionIdentifier, instantiator: Instantiator): Action<ComponentMetadataContext> {
        if (rules.isEmpty()) {
            return Actions.doNothing<ComponentMetadataContext>()
        }
        val collectedRules = ArrayList<ConfigurableRule<ComponentMetadataContext>>()
        for (classBasedRule in rules) {
            if (classBasedRule.getSpec().isSatisfiedBy(id)) {
                collectedRules.add(classBasedRule.getConfigurableRule())
            }
        }
        return InstantiatingAction<ComponentMetadataContext>(DefaultConfigurableRules<ComponentMetadataContext?>(collectedRules), instantiator, ExceptionHandler())
    }


    private fun processRule(specRuleAction: SpecRuleAction<in ComponentMetadataDetails?>, metadata: ModuleComponentResolveMetadata, details: ComponentMetadataDetails) {
        if (!specRuleAction.spec!!.isSatisfiedBy(details)) {
            return
        }
        val action: RuleAction<in ComponentMetadataDetails?>? = specRuleAction.action
        if (!shouldExecute(action!!, metadata)) {
            return
        }

        val inputs = gatherAdditionalInputs(action, metadata)
        executeAction(action, inputs, details)
    }

    private fun executeAction(action: RuleAction<in ComponentMetadataDetails?>, inputs: MutableList<*>, details: ComponentMetadataDetails) {
        try {
            synchronized(this) {
                action.execute(details, inputs)
            }
        } catch (e: InvalidUserCodeException) {
            throw e
        } catch (e: Exception) {
            throw InvalidUserCodeException(kotlin.String.format("There was an error while evaluating a component metadata rule for %s.", details.getId()), e)
        }
    }

    private fun shouldExecute(action: RuleAction<in ComponentMetadataDetails?>, metadata: ModuleComponentResolveMetadata): Boolean {
        val inputTypes: MutableList<Class<*>> = action.inputTypes
        if (!inputTypes.isEmpty()) {
            return inputTypes.stream().anyMatch { input: Class<*>? -> MetadataDescriptorFactory.Companion.isMatchingMetadata(input, metadata) }
        }
        return true
    }

    private fun gatherAdditionalInputs(action: RuleAction<in ComponentMetadataDetails?>, metadata: ModuleComponentResolveMetadata): MutableList<*> {
        val inputs: MutableList<Any> = ArrayList<Any>()
        for (inputType in action.inputTypes!!) {
            val descriptorFactory = MetadataDescriptorFactory(metadata)
            val descriptor: Any = descriptorFactory.createDescriptor(inputType)
            if (descriptor != null) {
                inputs.add(descriptor)
            }
        }
        return inputs
    }

    private class ExceptionHandler : InstantiatingAction.ExceptionHandler<ComponentMetadataContext> {
        override fun handleException(context: ComponentMetadataContext, throwable: Throwable) {
            throw InvalidUserCodeException(kotlin.String.format("There was an error while evaluating a component metadata rule for %s.", context.getDetails().getId()), throwable)
        }
    }

    internal class ShallowComponentMetadataAdapter(source: ComponentMetadata, attributesFactory: AttributesFactory) : ComponentMetadataDetails {
        private val id: ModuleVersionIdentifier
        private var changing: Boolean
        private var statusScheme: MutableList<kotlin.String>
        private val attributes: AttributeContainerInternal

        init {
            id = source.getId()
            changing = source.isChanging()
            statusScheme = source.getStatusScheme()
            attributes = attributesFactory.mutable(source.getAttributes() as AttributeContainerInternal)
        }

        override fun setChanging(changing: Boolean) {
            this.changing = changing
        }

        override fun setStatus(status: kotlin.String) {
            this.attributes.attribute<kotlin.String>(ProjectInternal.STATUS_ATTRIBUTE, status)
        }

        override fun setStatusScheme(statusScheme: MutableList<kotlin.String>) {
            this.statusScheme = statusScheme
        }

        override fun withVariant(name: kotlin.String, action: Action<in VariantMetadata>) {
        }

        override fun allVariants(action: Action<in VariantMetadata>) {
        }

        override fun addVariant(name: kotlin.String, action: Action<in VariantMetadata>) {
        }

        override fun addVariant(name: kotlin.String, base: kotlin.String, action: Action<in VariantMetadata>) {
        }

        override fun maybeAddVariant(name: kotlin.String, base: kotlin.String, action: Action<in VariantMetadata>) {
        }

        override fun belongsTo(notation: Any) {
        }

        override fun belongsTo(notation: Any, virtual: Boolean) {
        }

        override fun getId(): ModuleVersionIdentifier {
            return id
        }

        override fun isChanging(): Boolean {
            return changing
        }

        override fun getStatus(): kotlin.String {
            return attributes.getAttribute<kotlin.String>(ProjectInternal.STATUS_ATTRIBUTE)!!
        }

        override fun getStatusScheme(): MutableList<kotlin.String> {
            return statusScheme
        }

        override fun attributes(action: Action<in AttributeContainer>): ComponentMetadataDetails {
            action.execute(attributes)
            return this
        }

        override fun getAttributes(): AttributeContainer {
            return attributes
        }

        fun asImmutable(): ComponentMetadata {
            return UserProvidedMetadata(id, statusScheme, attributes.asImmutable())
        }
    }

    companion object {
        private val FORCE_REALIZE = Boolean.getBoolean("org.gradle.integtest.force.realize.metadata")

        private val DETAILS_TO_RESULT: Transformer<ModuleComponentResolveMetadata, WrappingComponentMetadataContext> = Transformer { componentMetadataContext: WrappingComponentMetadataContext ->
            val metadata = componentMetadataContext
                .getImmutableMetadataWithDerivationStrategy(componentMetadataContext.getVariantDerivationStrategy())
            realizeMetadata(metadata)
        }

        private fun realizeMetadata(metadata: ModuleComponentResolveMetadata): ModuleComponentResolveMetadata {
            var metadata = metadata
            if (metadata is DefaultIvyModuleResolveMetadata) {
                metadata = transform(metadata)
            } else if (metadata is DefaultMavenModuleResolveMetadata) {
                metadata = transform(metadata)
            } else {
                throw IllegalStateException("Invalid type received: " + metadata.javaClass)
            }
            return metadata
        }
    }
}
