/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.resolver

import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.DependencyConstraintMetadata
import org.gradle.api.artifacts.DirectDependenciesMetadata
import org.gradle.api.artifacts.DirectDependencyMetadata
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.VariantMetadata
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser

class ComponentMetadataDetailsAdapter(
    private val metadata: MutableModuleComponentResolveMetadata, private val instantiator: Instantiator,
    private val dependencyMetadataNotationParser: NotationParser<Any, DirectDependencyMetadata>,
    private val dependencyConstraintMetadataNotationParser: NotationParser<Any, DependencyConstraintMetadata>,
    private val componentIdentifierParser: NotationParser<Any, ComponentIdentifier>,
    private val platformSupport: PlatformSupport
) : ComponentMetadataDetails {
    override fun getId(): ModuleVersionIdentifier {
        return metadata.moduleVersionId
    }

    override fun isChanging(): Boolean {
        return metadata.isChanging
    }

    override fun getStatus(): String {
        return metadata.status!!
    }

    override fun getStatusScheme(): MutableList<String> {
        return metadata.statusScheme!!
    }

    override fun setChanging(changing: Boolean) {
        metadata.isChanging = changing
    }

    override fun setStatus(status: String) {
        metadata.status = status
    }

    override fun setStatusScheme(statusScheme: MutableList<String>) {
        metadata.statusScheme = statusScheme
    }

    override fun withVariant(name: String, action: Action<in VariantMetadata>) {
        action.execute(
            instantiator.newInstance<VariantMetadataAdapter>(
                VariantMetadataAdapter::class.java,
                name,
                metadata,
                instantiator,
                dependencyMetadataNotationParser,
                dependencyConstraintMetadataNotationParser
            )
        )
    }

    override fun allVariants(action: Action<in VariantMetadata>) {
        action.execute(
            instantiator.newInstance<VariantMetadataAdapter>(
                VariantMetadataAdapter::class.java,
                null,
                metadata,
                instantiator,
                dependencyMetadataNotationParser,
                dependencyConstraintMetadataNotationParser
            )
        )
    }

    override fun addVariant(name: String, action: Action<in VariantMetadata>) {
        metadata.variantMetadataRules!!.addVariant(name)
        withVariant(name, action)
    }

    override fun addVariant(name: String, base: String, action: Action<in VariantMetadata>) {
        metadata.variantMetadataRules!!.addVariant(name, base, false)
        withVariant(name, action)
    }

    override fun maybeAddVariant(name: String, base: String, action: Action<in VariantMetadata>) {
        metadata.variantMetadataRules!!.addVariant(name, base, true)
        withVariant(name, action)
    }

    override fun belongsTo(notation: Any) {
        belongsTo(notation, true)
    }

    override fun belongsTo(notation: Any, virtual: Boolean) {
        val id = componentIdentifierParser.parseNotation(notation)
        if (virtual) {
            metadata.belongsTo(VirtualComponentHelper.makeVirtual(id))
        } else if (id is ModuleComponentIdentifier) {
            addPlatformDependencyToAllVariants(id)
        } else {
            throw InvalidUserCodeException(notation.toString() + " is not a valid platform identifier")
        }
    }

    private fun addPlatformDependencyToAllVariants(platformId: ModuleComponentIdentifier) {
        allVariants(Action { v: VariantMetadata ->
            v.withDependencies(Action { dependencies: DirectDependenciesMetadata? ->
                val dependencyNotation = platformId.getGroup() + ":" + platformId.getModule() + ":" + platformId.getVersion()
                dependencies!!.add(dependencyNotation, Action { platformDependency: DirectDependencyMetadata? ->
                    platformDependency!!.attributes(Action { attributes: AttributeContainer? ->
                        attributes!!.attribute<Category>(
                            Category.CATEGORY_ATTRIBUTE, platformSupport.getRegularPlatformCategory()
                        )
                    }
                    )
                }
                )
            })
        })
    }

    override fun attributes(action: Action<in AttributeContainer>): ComponentMetadataDetails {
        val attributes: AttributeContainer? = metadata.attributesFactory.mutable(metadata.attributes as AttributeContainerInternal)
        action.execute(attributes)
        metadata.attributes = attributes
        return this
    }

    override fun getAttributes(): AttributeContainer {
        return metadata.attributes
    }

    override fun toString(): String {
        return metadata.moduleVersionId.toString()
    }
}
