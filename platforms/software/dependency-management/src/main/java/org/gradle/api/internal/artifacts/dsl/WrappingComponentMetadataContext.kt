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

import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataDetailsAdapter
import org.gradle.api.internal.artifacts.repositories.resolver.DependencyConstraintMetadataImpl
import org.gradle.api.internal.artifacts.repositories.resolver.DirectDependencyMetadataImpl
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.VariantDerivationStrategy
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser

internal class WrappingComponentMetadataContext(
    private val metadata: ModuleComponentResolveMetadata, private val instantiator: Instantiator,
    private val dependencyMetadataNotationParser: NotationParser<Any, DirectDependencyMetadataImpl>,
    private val dependencyConstraintMetadataNotationParser: NotationParser<Any, DependencyConstraintMetadataImpl>,
    private val componentIdentifierParser: NotationParser<Any, ComponentIdentifier>,
    private val platformSupport: PlatformSupport
) : ComponentMetadataContext {
    private val descriptorFactory: MetadataDescriptorFactory

    private var mutableMetadata: MutableModuleComponentResolveMetadata? = null
    private var details: ComponentMetadataDetails? = null

    init {
        this.descriptorFactory = MetadataDescriptorFactory(metadata)
    }

    override fun <T> getDescriptor(descriptorClass: Class<T?>): T? {
        return descriptorFactory.createDescriptor<T?>(descriptorClass)
    }

    override fun getDetails(): ComponentMetadataDetails {
        createMutableMetadataIfNeeded()
        if (details == null) {
            details = instantiator.newInstance<ComponentMetadataDetailsAdapter>(
                ComponentMetadataDetailsAdapter::class.java,
                mutableMetadata,
                instantiator,
                dependencyMetadataNotationParser,
                dependencyConstraintMetadataNotationParser,
                componentIdentifierParser,
                platformSupport
            )
        }
        return details!!
    }

    val variantDerivationStrategy: VariantDerivationStrategy
        get() = metadata.variantDerivationStrategy

    fun getImmutableMetadataWithDerivationStrategy(variantDerivationStrategy: VariantDerivationStrategy): ModuleComponentResolveMetadata {
        // We need to create a copy or the rules will be added to the wrong container
        return createMutableMetadataIfNeeded().asImmutable()!!
            .withDerivationStrategy(variantDerivationStrategy)!!
    }

    private fun createMutableMetadataIfNeeded(): MutableModuleComponentResolveMetadata {
        if (mutableMetadata == null) {
            mutableMetadata = metadata.asMutable()
        }
        return mutableMetadata!!
    }
}
