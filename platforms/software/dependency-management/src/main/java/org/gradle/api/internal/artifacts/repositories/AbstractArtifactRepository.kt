/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories

import org.gradle.api.Action
import org.gradle.api.ActionConfiguration
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.artifacts.ComponentMetadataListerDetails
import org.gradle.api.artifacts.ComponentMetadataSupplier
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails
import org.gradle.api.artifacts.ComponentMetadataVersionLister
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.MetadataSupplierAware
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalRepositoryResourceAccessor
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.UncheckedException
import org.gradle.internal.action.ConfigurableRule
import org.gradle.internal.action.DefaultConfigurableRule
import org.gradle.internal.action.DefaultConfigurableRules
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resolve.caching.ImplicitInputsCapturingInstantiator
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.service.ServiceLookup
import org.gradle.internal.service.ServiceLookupException
import org.gradle.internal.service.UnknownServiceException
import java.lang.reflect.Type
import java.net.URI
import java.util.function.Supplier

abstract class AbstractArtifactRepository protected constructor(private val objectFactory: ObjectFactory, versionParser: VersionParser) : ArtifactRepositoryInternal, ContentFilteringRepository,
    MetadataSupplierAware {
    private var name: String? = null
    private var isPartOfContainer = false
    private var componentMetadataSupplierRuleClass: Class<out ComponentMetadataSupplier>? = null
    private var componentMetadataListerRuleClass: Class<out ComponentMetadataVersionLister>? = null
    private var componentMetadataSupplierRuleConfiguration: Action<in ActionConfiguration>? = null
    private var componentMetadataListerRuleConfiguration: Action<in ActionConfiguration>? = null
    private val repositoryContentDescriptor: RepositoryContentDescriptorInternal

    init {
        this.repositoryContentDescriptor = createRepositoryDescriptor(versionParser)
    }

    override fun onAddToContainer(container: NamedDomainObjectCollection<ArtifactRepository>) {
        isPartOfContainer = true
    }

    override fun getName(): String {
        return name!!
    }

    override fun setName(name: String) {
        check(!isPartOfContainer) { "The name of an ArtifactRepository cannot be changed after it has been added to a repository container. You should set the name when creating the repository." }
        this.name = name
    }

    override fun getDisplayName(): String {
        return getName()
    }

    override fun setMetadataSupplier(ruleClass: Class<out ComponentMetadataSupplier>) {
        this.componentMetadataSupplierRuleClass = ruleClass
        this.componentMetadataSupplierRuleConfiguration = null
    }

    override fun setMetadataSupplier(rule: Class<out ComponentMetadataSupplier>, configureAction: Action<in ActionConfiguration>) {
        this.componentMetadataSupplierRuleClass = rule
        this.componentMetadataSupplierRuleConfiguration = configureAction
    }

    override fun setComponentVersionsLister(lister: Class<out ComponentMetadataVersionLister>) {
        this.componentMetadataListerRuleClass = lister
        this.componentMetadataListerRuleConfiguration = null
    }

    override fun setComponentVersionsLister(lister: Class<out ComponentMetadataVersionLister>, configureAction: Action<in ActionConfiguration>) {
        this.componentMetadataListerRuleClass = lister
        this.componentMetadataListerRuleConfiguration = configureAction
    }

    override fun createRepositoryDescriptor(versionParser: VersionParser): RepositoryContentDescriptorInternal {
        return DefaultRepositoryContentDescriptor(Supplier { this.getDisplayName() }, versionParser)
    }

    override fun getRepositoryDescriptorCopy(): RepositoryContentDescriptorInternal {
        return repositoryContentDescriptor.asMutableCopy()
    }

    override fun getContentFilter(): Action<in ArtifactResolutionDetails> {
        return repositoryContentDescriptor.toContentFilter()
    }

    override fun getIncludedConfigurations(): MutableSet<String> {
        return repositoryContentDescriptor.getIncludedConfigurations()!!
    }

    override fun getExcludedConfigurations(): MutableSet<String> {
        return repositoryContentDescriptor.getExcludedConfigurations()!!
    }

    override fun getRequiredAttributes(): MutableMap<Attribute<Any>, MutableSet<Any>> {
        return repositoryContentDescriptor.getRequiredAttributes()!!
    }

    override fun content(configureAction: Action<in RepositoryContentDescriptor>) {
        configureAction.execute(repositoryContentDescriptor)
    }

    fun createComponentMetadataSupplierFactory(instantiator: Instantiator, isolatableFactory: IsolatableFactory): InstantiatingAction<ComponentMetadataSupplierDetails>? {
        if (componentMetadataSupplierRuleClass != null) {
            return createRuleAction<ComponentMetadataSupplierDetails>(
                instantiator,
                DefaultConfigurableRule.of<ComponentMetadataSupplierDetails>(componentMetadataSupplierRuleClass, componentMetadataSupplierRuleConfiguration, isolatableFactory)
            )
        } else {
            return null
        }
    }

    fun createComponentMetadataVersionLister(instantiator: Instantiator, isolatableFactory: IsolatableFactory): InstantiatingAction<ComponentMetadataListerDetails>? {
        if (componentMetadataListerRuleClass != null) {
            return createRuleAction<ComponentMetadataListerDetails>(
                instantiator,
                DefaultConfigurableRule.of<ComponentMetadataListerDetails>(componentMetadataListerRuleClass, componentMetadataListerRuleConfiguration, isolatableFactory)
            )
        } else {
            return null
        }
    }

    /**
     * Creates a service registry giving access to the services we want to expose to rules and returns an instantiator that uses this service registry.
     *
     * @param transport the transport used to create the repository accessor
     * @return a dependency injecting instantiator, aware of services we want to expose
     */
    fun createInjectorForMetadataSuppliers(
        transport: RepositoryTransport,
        instantiatorFactory: InstantiatorFactory,
        rootUri: URI?,
        externalResourcesFileStore: FileStore<String>
    ): ImplicitInputsCapturingInstantiator {
        val repositoryResourceAccessor = createRepositoryAccessor(transport, rootUri, externalResourcesFileStore)
        val services: ServiceLookup = RepositoryRuleServiceLookup(objectFactory, repositoryResourceAccessor)
        return ImplicitInputsCapturingInstantiator(services, instantiatorFactory)
    }

    protected open fun createRepositoryAccessor(
        transport: RepositoryTransport,
        rootUri: URI?,
        externalResourcesFileStore: FileStore<String>
    ): RepositoryResourceAccessor? {
        if (rootUri == null) {
            return null
        }
        return ExternalRepositoryResourceAccessor(rootUri, transport.getResourceAccessor(), externalResourcesFileStore)
    }

    private class RepositoryRuleServiceLookup(
        private val objectFactory: ObjectFactory,
        private val repositoryResourceAccessor: RepositoryResourceAccessor?
    ) : ServiceLookup {
        @Throws(ServiceLookupException::class)
        override fun find(serviceType: Type): Any? {
            if (serviceType === RepositoryResourceAccessor::class.java) {
                if (repositoryResourceAccessor == null) {
                    throw ServiceLookupException("Can not inject RepositoryResourceAccessor since repository has no URL.")
                } else {
                    return repositoryResourceAccessor
                }
            } else if (serviceType === ObjectFactory::class.java) {
                return objectFactory
            }

            return null
        }

        @Throws(UnknownServiceException::class, ServiceLookupException::class)
        override fun get(serviceType: Type): Any {
            val service = find(serviceType)
            if (service != null) {
                return service
            }
            throw UnknownServiceException(
                serviceType,
                "Service of type " + serviceType + " is not available for repository metadata rules. Available services: " + availableServicesDescription() + "."
            )
        }

        @Throws(UnknownServiceException::class, ServiceLookupException::class)
        override fun get(serviceType: Type, annotatedWith: Class<out Annotation>): Any? {
            throw UnknownServiceException(
                serviceType,
                "Service of type " + serviceType + " annotated with @" + annotatedWith.getSimpleName() + " is not available for repository metadata rules. Available services: " + availableServicesDescription() + "."
            )
        }

        fun availableServicesDescription(): String {
            return if (repositoryResourceAccessor != null)
                ObjectFactory::class.java.getSimpleName() + ", " + RepositoryResourceAccessor::class.java.getSimpleName()
            else
                ObjectFactory::class.java.getSimpleName()
        }
    }

    companion object {
        private fun <T> createRuleAction(instantiator: Instantiator, rule: ConfigurableRule<T?>): InstantiatingAction<T?> {
            return InstantiatingAction<T?>(DefaultConfigurableRules.of<T?>(rule), instantiator, InstantiatingAction.ExceptionHandler { target: T?, throwable: Throwable? ->
                throw UncheckedException.throwAsUncheckedException(throwable!!)
            })
        }
    }
}
