/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.artifacts.configurations

import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.ConfigurationServicesBundle
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.ConfigurationResolver
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory
import org.gradle.internal.Factory
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.event.ListenerBroadcast
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.typeconversion.NotationParser
import javax.annotation.concurrent.ThreadSafe
import javax.inject.Inject

/**
 * Factory for creating [org.gradle.api.artifacts.Configuration] instances.
 */
@ServiceScope(Scope.Project::class)
@ThreadSafe
class DefaultConfigurationFactory @Inject constructor(
    private val configurationServices: ConfigurationServicesBundle,
    private val listenerManager: ListenerManager,
    private val domainObjectContext: DomainObjectContext,
    artifactNotationParserFactory: PublishArtifactNotationParserFactory,
    private val userCodeApplicationContext: UserCodeApplicationContext
) {
    private val artifactNotationParser: NotationParser<Any, ConfigurablePublishArtifact>
    private val capabilityNotationParser: NotationParser<Any, Capability>

    init {
        this.artifactNotationParser = artifactNotationParserFactory.create()
        this.capabilityNotationParser = CapabilityNotationParserFactory(true).create()
    }

    /**
     * Creates a new unlocked configuration instance.
     */
    fun create(
        name: String,
        isDetached: Boolean,
        resolver: ConfigurationResolver,
        resolutionStrategyFactory: Factory<ResolutionStrategyInternal?>,
        role: ConfigurationRole
    ): DefaultLegacyConfiguration {
        val dependencyResolutionListeners: ListenerBroadcast<DependencyResolutionListener?>? =
            listenerManager.createAnonymousBroadcaster<DependencyResolutionListener?>(DependencyResolutionListener::class.java)
        return configurationServices.objectFactory.newInstance(
            DefaultLegacyConfiguration::class.java,
            configurationServices,
            domainObjectContext,
            name,
            isDetached,
            resolver,
            dependencyResolutionListeners,
            resolutionStrategyFactory,
            artifactNotationParser,
            capabilityNotationParser,
            userCodeApplicationContext,
            this,
            role
        )
    }

    /**
     * Creates a new locked resolvable configuration instance.
     */
    fun createResolvable(
        name: String,
        resolver: ConfigurationResolver,
        resolutionStrategyFactory: Factory<ResolutionStrategyInternal?>
    ): DefaultResolvableConfiguration {
        val dependencyResolutionListeners: ListenerBroadcast<DependencyResolutionListener?>? =
            listenerManager.createAnonymousBroadcaster<DependencyResolutionListener?>(DependencyResolutionListener::class.java)
        return configurationServices.objectFactory.newInstance(
            DefaultResolvableConfiguration::class.java,
            configurationServices,
            domainObjectContext,
            name,
            resolver,
            dependencyResolutionListeners,
            resolutionStrategyFactory,
            artifactNotationParser,
            capabilityNotationParser,
            userCodeApplicationContext,
            this
        )
    }

    /**
     * Creates a new locked consumable configuration instance.
     */
    fun createConsumable(
        name: String,
        resolver: ConfigurationResolver,
        resolutionStrategyFactory: Factory<ResolutionStrategyInternal?>
    ): DefaultConsumableConfiguration {
        val dependencyResolutionListeners: ListenerBroadcast<DependencyResolutionListener?>? =
            listenerManager.createAnonymousBroadcaster<DependencyResolutionListener?>(DependencyResolutionListener::class.java)
        return configurationServices.objectFactory.newInstance(
            DefaultConsumableConfiguration::class.java,
            configurationServices,
            domainObjectContext,
            name,
            resolver,
            dependencyResolutionListeners,
            resolutionStrategyFactory,
            artifactNotationParser,
            capabilityNotationParser,
            userCodeApplicationContext,
            this
        )
    }

    /**
     * Creates a new locked dependency scope configuration instance.
     */
    fun createDependencyScope(
        name: String,
        resolver: ConfigurationResolver,
        resolutionStrategyFactory: Factory<ResolutionStrategyInternal?>
    ): DefaultDependencyScopeConfiguration {
        val dependencyResolutionListeners: ListenerBroadcast<DependencyResolutionListener?>? =
            listenerManager.createAnonymousBroadcaster<DependencyResolutionListener?>(DependencyResolutionListener::class.java)
        return configurationServices.objectFactory.newInstance(
            DefaultDependencyScopeConfiguration::class.java,
            configurationServices,
            domainObjectContext,
            name,
            resolver,
            dependencyResolutionListeners,
            resolutionStrategyFactory,
            artifactNotationParser,
            capabilityNotationParser,
            userCodeApplicationContext,
            this
        )
    }
}
