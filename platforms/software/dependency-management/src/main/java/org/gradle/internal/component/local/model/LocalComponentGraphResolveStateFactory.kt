/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.internal.component.local.model

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultRootComponentIdentifier
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider
import org.gradle.api.internal.artifacts.configurations.VariantIdentityUniquenessVerifier
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalVariantGraphResolveStateBuilder
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.internal.Describables
import org.gradle.internal.component.model.ComponentIdGenerator
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.model.ModelContainer
import org.gradle.internal.model.ValueCalculator
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.util.function.Consumer

@ServiceScope(Scope.BuildTree::class)
class LocalComponentGraphResolveStateFactory(
    idGenerator: ComponentIdGenerator,
    metadataBuilder: LocalVariantGraphResolveStateBuilder,
    calculatedValueContainerFactory: CalculatedValueContainerFactory
) {
    private val idGenerator: ComponentIdGenerator
    private val metadataBuilder: LocalVariantGraphResolveStateBuilder
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory

    init {
        this.idGenerator = idGenerator
        this.metadataBuilder = metadataBuilder
        this.calculatedValueContainerFactory = calculatedValueContainerFactory
    }

    /**
     * Creates state for a component loaded from the configuration cache.
     */
    fun realizedStateFor(
        metadata: LocalComponentGraphResolveMetadata,
        variants: MutableList<out LocalVariantGraphResolveState>
    ): LocalComponentGraphResolveState {
        val configurationFactory: LocalVariantGraphResolveStateFactory = RealizedListVariantFactory(variants)
        return createLocalComponentState(false, idGenerator.nextComponentId(), metadata, configurationFactory)
    }

    /**
     * Creates state for a variant loaded from the configuration cache.
     */
    fun realizedVariantStateFor(
        metadata: LocalVariantGraphResolveMetadata,
        dependencyMetadata: DefaultLocalVariantGraphResolveState.VariantDependencyMetadata,
        variants: MutableSet<LocalVariantMetadata>
    ): LocalVariantGraphResolveState {
        val calculatedDependencies: CalculatedValue<DefaultLocalVariantGraphResolveState.VariantDependencyMetadata> =
            calculatedValueContainerFactory.create<DefaultLocalVariantGraphResolveState.VariantDependencyMetadata, ValueCalculator<out DefaultLocalVariantGraphResolveState.VariantDependencyMetadata>>(
                Describables.of(metadata, "dependencies"), ValueCalculator { context: NodeExecutionContext? -> dependencyMetadata })

        return DefaultLocalVariantGraphResolveState(
            idGenerator.nextVariantId(),
            metadata,
            calculatedDependencies,
            variants
        )
    }

    /**
     * Creates state for a standard local component, with variants derived from the given [ConfigurationsProvider].
     */
    fun stateFor(
        model: ModelContainer<*>,
        metadata: LocalComponentGraphResolveMetadata,
        configurations: ConfigurationsProvider
    ): LocalComponentGraphResolveState {
        val variantsFactory: LocalVariantGraphResolveStateFactory = ConfigurationsProviderVariantFactory(
            metadata.getId(),
            configurations,
            metadataBuilder,
            model,
            calculatedValueContainerFactory
        )

        return createLocalComponentState(false, idGenerator.nextComponentId(), metadata, variantsFactory)
    }

    /**
     * Creates state for an adhoc root component with no variants.
     */
    fun adhocRootComponentState(
        status: String,
        moduleVersionId: ModuleVersionIdentifier,
        attributesSchema: ImmutableAttributesSchema
    ): LocalComponentGraphResolveState {
        val instanceId = idGenerator.nextComponentId()
        val componentIdentifier: ComponentIdentifier = DefaultRootComponentIdentifier(instanceId)

        val metadata = LocalComponentGraphResolveMetadata(
            moduleVersionId,
            componentIdentifier,
            status,
            attributesSchema
        )

        val configurationFactory: LocalVariantGraphResolveStateFactory = RealizedListVariantFactory(mutableListOf<LocalVariantGraphResolveState>())
        return createLocalComponentState(true, instanceId, metadata, configurationFactory)
    }

    private fun createLocalComponentState(
        adHoc: Boolean,
        instanceId: Long,
        metadata: LocalComponentGraphResolveMetadata,
        variantsFactory: LocalVariantGraphResolveStateFactory
    ): DefaultLocalComponentGraphResolveState {
        return DefaultLocalComponentGraphResolveState(
            instanceId,
            metadata,
            adHoc,
            variantsFactory,
            calculatedValueContainerFactory
        )
    }

    /**
     * A [LocalVariantGraphResolveStateFactory] which uses a list of pre-constructed variant
     * states as its data source.
     */
    private class RealizedListVariantFactory(variants: MutableList<out LocalVariantGraphResolveState>) : LocalVariantGraphResolveStateFactory {
        private val variants: MutableList<out LocalVariantGraphResolveState>

        init {
            this.variants = variants
        }

        override fun visitConsumableVariants(visitor: Consumer<LocalVariantGraphResolveState>) {
            for (variant in variants) {
                visitor.accept(variant)
            }
        }
    }

    /**
     * A [LocalVariantGraphResolveStateFactory] which uses a [ConfigurationsProvider] as its data source.
     */
    private class ConfigurationsProviderVariantFactory(
        componentId: ComponentIdentifier,
        configurationsProvider: ConfigurationsProvider,
        stateBuilder: LocalVariantGraphResolveStateBuilder,
        model: ModelContainer<*>,
        calculatedValueContainerFactory: CalculatedValueContainerFactory
    ) : LocalVariantGraphResolveStateFactory {
        private val componentId: ComponentIdentifier
        private val configurationsProvider: ConfigurationsProvider
        private val stateBuilder: LocalVariantGraphResolveStateBuilder
        private val model: ModelContainer<*>
        private val calculatedValueContainerFactory: CalculatedValueContainerFactory

        init {
            this.componentId = componentId
            this.configurationsProvider = configurationsProvider
            this.stateBuilder = stateBuilder
            this.model = model
            this.calculatedValueContainerFactory = calculatedValueContainerFactory
        }

        //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
        override fun visitConsumableVariants(visitor: Consumer<LocalVariantGraphResolveState>) {
            model.applyToMutableState { p: Any? ->
                val cache =
                    LocalVariantGraphResolveStateBuilder.DependencyCache()
                VariantIdentityUniquenessVerifier.buildReport(configurationsProvider).assertNoConflicts()
                configurationsProvider.visitConsumable(Consumer { configuration: ConfigurationInternal? ->
                    val variantState = stateBuilder.createConsumableVariantState(
                        configuration!!,
                        componentId,
                        cache,
                        model,
                        calculatedValueContainerFactory
                    )
                    visitor.accept(variantState)
                })
            }
        }
    }
}
