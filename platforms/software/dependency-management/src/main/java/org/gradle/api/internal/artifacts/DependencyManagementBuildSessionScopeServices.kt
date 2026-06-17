/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts

import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.ComponentSelectorNotationConverter
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.ModuleSelectorStringNotationConverter
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.CachingComponentSelectionDescriptorFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.DesugaredAttributeContainerSerializer
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultMavenVariantAttributesFactory
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MavenVariantAttributesFactory
import org.gradle.api.internal.attributes.AttributeSchemaServices
import org.gradle.api.internal.attributes.AttributeValueIsolator
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.DefaultAttributesFactory
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistryFactory
import org.gradle.api.internal.catalog.DependenciesAccessorsWorkspaceProvider
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.internal.component.external.model.PreferJavaRuntimeVariant
import org.gradle.internal.exceptions.DiagnosticsVisitor
import org.gradle.internal.model.InMemoryCacheFactory
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.snapshot.impl.ValueSnapshotterSerializerRegistry
import org.gradle.internal.typeconversion.CachingNotationConverter
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.internal.typeconversion.TypeConversionException

class DependencyManagementBuildSessionScopeServices : ServiceRegistrationProvider {
    fun configure(registration: ServiceRegistration) {
        registration.add(DependenciesAccessorsWorkspaceProvider::class.java)
        registration.add(AttributeValueIsolator::class.java)
        registration.add<DefaultAttributesFactory?>(AttributesFactory::class.java, DefaultAttributesFactory::class.java)
        registration.add<DefaultMavenVariantAttributesFactory?>(MavenVariantAttributesFactory::class.java, DefaultMavenVariantAttributesFactory::class.java)
        registration.add(DesugaredAttributeContainerSerializer::class.java)
        registration.add(MavenMutableModuleMetadataFactory::class.java)
        registration.add(IvyMutableModuleMetadataFactory::class.java)
        registration.add(PreferJavaRuntimeVariant::class.java)
        registration.add(ImmutableAttributesSchemaFactory::class.java)
        registration.add(ImmutableArtifactTypeRegistryFactory::class.java)
        registration.add(AttributeSchemaServices::class.java)
        registration.add<CachingComponentSelectionDescriptorFactory?>(ComponentSelectionDescriptorFactory::class.java, CachingComponentSelectionDescriptorFactory::class.java)
    }

    @Provides
    fun createDependencyManagementValueSnapshotterSerializerRegistry(
        moduleIdentifierFactory: ImmutableModuleIdentifierFactory?,
        attributesFactory: AttributesFactory?,
        namedObjectInstantiator: NamedObjectInstantiator?,
        componentSelectionDescriptorFactory: ComponentSelectionDescriptorFactory?
    ): ValueSnapshotterSerializerRegistry {
        return DependencyManagementValueSnapshotterSerializerRegistry(
            moduleIdentifierFactory,
            attributesFactory,
            namedObjectInstantiator,
            componentSelectionDescriptorFactory
        )
    }

    @Provides
    fun createComponentSelectorFactory(moduleIdentifierFactory: ImmutableModuleIdentifierFactory, cacheFactory: InMemoryCacheFactory): ComponentSelectorNotationConverter {
        val delegate = NotationParserBuilder
            .toType<ComponentSelector?>(ComponentSelector::class.java)
            .converter(CachingNotationConverter<ComponentSelector?>(ModuleSelectorStringNotationConverter(moduleIdentifierFactory), cacheFactory))
            .toComposite()

        return object : ComponentSelectorNotationConverter {
            @Throws(TypeConversionException::class)
            override fun parseNotation(notation: Any?): ComponentSelector? {
                return delegate.parseNotation(notation)
            }

            override fun describe(visitor: DiagnosticsVisitor?) {
                delegate.describe(visitor)
            }
        }
    }
}
