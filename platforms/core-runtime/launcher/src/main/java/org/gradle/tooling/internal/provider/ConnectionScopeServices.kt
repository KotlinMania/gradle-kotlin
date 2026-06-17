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
package org.gradle.tooling.internal.provider

import org.gradle.TaskExecutionRequest
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.initialization.loadercache.ModelClassLoaderFactory
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.daemon.client.serialization.ClasspathInferer
import org.gradle.internal.daemon.client.serialization.ClientSidePayloadClassLoaderFactory
import org.gradle.internal.daemon.client.serialization.ClientSidePayloadClassLoaderRegistry
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.snapshot.impl.DefaultIsolatableFactory
import org.gradle.internal.snapshot.impl.IsolatableSerializerRegistry
import org.gradle.internal.state.ManagedFactoryRegistry
import org.gradle.launcher.daemon.client.DaemonClientFactory
import org.gradle.launcher.daemon.client.DaemonClientGlobalServices
import org.gradle.launcher.daemon.client.DaemonStopClientExecuter
import org.gradle.launcher.daemon.client.NotifyDaemonClientExecuter
import org.gradle.tooling.internal.provider.serialization.ClassLoaderCache
import org.gradle.tooling.internal.provider.serialization.DefaultPayloadClassLoaderRegistry
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.internal.provider.serialization.WellKnownClassLoaderRegistry

/**
 * Shared services for a tooling API provider connection.
 */
class ConnectionScopeServices : ServiceRegistrationProvider {
    fun configure(serviceRegistration: ServiceRegistration) {
        serviceRegistration.addProvider(DaemonClientGlobalServices())
    }

    @Provides
    fun createShutdownCoordinator(listenerManager: ListenerManager, daemonStopClient: DaemonStopClientExecuter): ShutdownCoordinator {
        val shutdownCoordinator = ShutdownCoordinator(daemonStopClient)
        listenerManager.addListener(shutdownCoordinator)
        return shutdownCoordinator
    }

    @Provides
    fun createDaemonStopClientFactory(daemonClientFactory: DaemonClientFactory): DaemonStopClientExecuter {
        return DaemonStopClientExecuter(daemonClientFactory)
    }

    @Provides
    fun createNotifyDaemonClientExecuter(daemonClientFactory: DaemonClientFactory): NotifyDaemonClientExecuter {
        return NotifyDaemonClientExecuter(daemonClientFactory)
    }

    @Provides
    fun createClassLoaderHierarchyHasher(): ClassLoaderHierarchyHasher {
        return object : ClassLoaderHierarchyHasher {
            override fun getClassLoaderHash(classLoader: ClassLoader): HashCode? {
                throw UnsupportedOperationException()
            }
        }
    }

    @Provides
    fun createIsolatableSerializerRegistry(classLoaderHierarchyHasher: ClassLoaderHierarchyHasher, managedFactoryRegistry: ManagedFactoryRegistry): IsolatableSerializerRegistry {
        return IsolatableSerializerRegistry(classLoaderHierarchyHasher, managedFactoryRegistry)
    }

    @Provides
    fun createIsolatableFactory(
        classLoaderHierarchyHasher: ClassLoaderHierarchyHasher,
        managedFactoryRegistry: ManagedFactoryRegistry
    ): IsolatableFactory {
        return DefaultIsolatableFactory(classLoaderHierarchyHasher, managedFactoryRegistry)
    }

    @Provides
    fun createProviderConnection(
        daemonClientFactory: DaemonClientFactory,
        buildLayoutFactory: BuildLayoutFactory,
        serviceRegistry: ServiceRegistry,
        fileCollectionFactory: FileCollectionFactory,
        shutdownCoordinator: ShutdownCoordinator,
        notifyDaemonClientExecuter: NotifyDaemonClientExecuter,
        isolatableSerializerRegistry: IsolatableSerializerRegistry
    ): ProviderConnection {
        val classLoaderCache = ClassLoaderCache()

        val parent = this.javaClass.getClassLoader()
        val filterSpec = FilteringClassLoader.Spec()
        filterSpec.allowPackage("org.gradle.tooling.internal.protocol")
        filterSpec.allowClass(TaskExecutionRequest::class.java)
        val modelClassLoader = FilteringClassLoader(parent, filterSpec)

        val payloadSerializer = PayloadSerializer(
            WellKnownClassLoaderRegistry(
                ClientSidePayloadClassLoaderRegistry(
                    DefaultPayloadClassLoaderRegistry(
                        classLoaderCache,
                        ClientSidePayloadClassLoaderFactory(
                            ModelClassLoaderFactory(modelClassLoader)
                        )
                    ),
                    ClasspathInferer(),
                    classLoaderCache
                )
            )
        )

        return ProviderConnection(
            serviceRegistry,
            buildLayoutFactory,
            daemonClientFactory,
            payloadSerializer,
            fileCollectionFactory,
            shutdownCoordinator,
            notifyDaemonClientExecuter,
            isolatableSerializerRegistry
        )
    }
}
