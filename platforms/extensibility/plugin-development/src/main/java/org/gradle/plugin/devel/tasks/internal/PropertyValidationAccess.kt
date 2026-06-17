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
package org.gradle.plugin.devel.tasks.internal

import com.google.common.reflect.TypeToken
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.properties.TypeScheme
import org.gradle.api.internal.tasks.properties.annotations.OutputPropertyRoleAnnotationHandler
import org.gradle.api.tasks.Nested
import org.gradle.cache.internal.DefaultCrossBuildInMemoryCacheFactory
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.event.ScopedListenerManager
import org.gradle.internal.instantiation.generator.DefaultInstantiatorFactory
import org.gradle.internal.properties.annotations.NestedValidationUtil
import org.gradle.internal.properties.annotations.PropertyMetadata
import org.gradle.internal.properties.annotations.TypeMetadata
import org.gradle.internal.properties.annotations.TypeMetadataStore
import org.gradle.internal.properties.annotations.TypeMetadataWalker
import org.gradle.internal.reflect.validation.TypeValidationContext
import org.gradle.internal.service.DefaultServiceLocator
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.ServiceRegistryBuilder.Companion.builder
import org.gradle.internal.service.scopes.GradleModuleServices
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.state.DefaultManagedFactoryRegistry
import org.jspecify.annotations.NullMarked
import java.lang.reflect.Modifier

/**
 * Class for easy access to property validation from the validator task.
 */
@NullMarked
class PropertyValidationAccess private constructor() {
    private val typeSchemes: MutableList<TypeScheme>

    init {
        val builder = builder().displayName("Global services")
        // Should reuse `GlobalScopeServices` here, however this requires a bunch of stuff in order to discover the plugin service registries
        // For now, re-implement the discovery here
        builder.provider(object : ServiceRegistrationProvider {
            @Suppress("unused")
            fun configure(registration: ServiceRegistration) {
                registration.add<ScopedListenerManager?>(ScopedListenerManager::class.java, DefaultListenerManager(Scope.Global::class.java))
                registration.add(DefaultCrossBuildInMemoryCacheFactory::class.java)
                // TODO: do we need any factories here?
                registration.add<DefaultManagedFactoryRegistry?>(DefaultManagedFactoryRegistry::class.java, DefaultManagedFactoryRegistry())
                registration.add(OutputPropertyRoleAnnotationHandler::class.java)
                registration.add(DefaultInstantiatorFactory::class.java)
                val servicesProviders = DefaultServiceLocator(false, javaClass.getClassLoader()).getAll<GradleModuleServices>(GradleModuleServices::class.java)
                for (services in servicesProviders) {
                    services.registerGlobalServices(registration)
                }
            }
        })
        val services: ServiceRegistry = builder.build()
        this.typeSchemes = services.getAll<TypeScheme?>(TypeScheme::class.java)
    }

    private fun collectTypeValidationProblems(topLevelBean: Class<*>, validationContext: TypeValidationContext) {
        // Skip this for now
        if (topLevelBean == TaskInternal::class.java) {
            return
        }

        val metadataStore = getTypeMetadataStore(topLevelBean)
        if (metadataStore == null) {
            // Don't know about this type
            return
        }

        val topLevelType: TypeToken<*> = TypeToken.of(topLevelBean)
        TypeMetadataWalker.typeWalker(metadataStore, Nested::class.java).walk(topLevelType, object : TypeMetadataWalker.StaticMetadataVisitor {
            override fun visitRoot(typeMetadata: TypeMetadata, value: TypeToken<*>) {
                typeMetadata.visitValidationFailures(null, validationContext)
            }

            override fun visitNested(typeMetadata: TypeMetadata, qualifiedName: String, propertyMetadata: PropertyMetadata, value: TypeToken<*>) {
                typeMetadata.visitValidationFailures(qualifiedName, validationContext)
                // Inspecting annotations of static types is only conclusive if type is final
                if (Modifier.isFinal(value.getRawType().getModifiers())) {
                    NestedValidationUtil.validateBeanType(validationContext, propertyMetadata.getPropertyName(), typeMetadata.getType())
                }
            }
        })
    }

    private fun getTypeMetadataStore(topLevelBean: Class<*>): TypeMetadataStore? {
        for (typeScheme in typeSchemes) {
            if (typeScheme.appliesTo(topLevelBean)) {
                return typeScheme.getMetadataStore()
            }
        }
        return null
    }

    companion object {
        private val INSTANCE = PropertyValidationAccess()

        fun collectValidationProblems(topLevelBean: Class<*>, validationContext: TypeValidationContext) {
            INSTANCE.collectTypeValidationProblems(topLevelBean, validationContext)
        }
    }
}
