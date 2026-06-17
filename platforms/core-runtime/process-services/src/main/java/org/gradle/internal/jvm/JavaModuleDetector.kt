/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.process.internal.services

import org.gradle.api.internal.ExternalProcessStartedListener
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.provider.sources.process.ExecSpecFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.jvm.JavaModuleDetector
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.AbstractGradleModuleServices
import org.gradle.process.ExecOperations
import org.gradle.process.internal.DefaultExecActionFactory
import org.gradle.process.internal.DefaultExecOperations
import org.gradle.process.internal.DefaultExecSpecFactory
import org.gradle.process.internal.ExecActionFactory
import org.gradle.process.internal.ExecFactory
import org.jspecify.annotations.NullMarked

@NullMarked
class ProcessServices : AbstractGradleModuleServices() {
    override fun registerGlobalServices(registration: ServiceRegistration) {
        registration.addProvider(GlobalProcessServices())
    }

    override fun registerGradleUserHomeServices(registration: ServiceRegistration) {
        registration.addProvider(GradleUserHomeProcessServices())
    }

    override fun registerBuildSessionServices(registration: ServiceRegistration) {
        registration.addProvider(BuildSessionProcessServices())
    }

    override fun registerBuildServices(registration: ServiceRegistration) {
        registration.addProvider(BuildProcessServices())
    }

    override fun registerProjectServices(registration: ServiceRegistration) {
        registration.addProvider(ProjectProcessServices())
    }

    private class GlobalProcessServices : ServiceRegistrationProvider {
        @Provides
        fun createExecFactory(
            fileResolver: FileResolver,
            fileCollectionFactory: FileCollectionFactory,
            instantiator: Instantiator,
            objectFactory: ObjectFactory,
            executorFactory: ExecutorFactory,
            temporaryFileProvider: TemporaryFileProvider,
            buildCancellationToken: BuildCancellationToken
        ): ExecFactory {
            return DefaultExecActionFactory.Companion.of(
                fileResolver,
                fileCollectionFactory,
                instantiator,
                executorFactory,
                temporaryFileProvider,
                buildCancellationToken,
                objectFactory
            )
        }
    }

    private class GradleUserHomeProcessServices : ServiceRegistrationProvider {
        @Provides
        fun createExecFactory(
            parent: ExecFactory,
            fileResolver: FileResolver,
            fileCollectionFactory: FileCollectionFactory,
            instantiator: Instantiator,
            objectFactory: ObjectFactory,
            javaModuleDetector: JavaModuleDetector
        ): ExecFactory {
            return parent.forContext()
                .withFileResolver(fileResolver)
                .withFileCollectionFactory(fileCollectionFactory)
                .withInstantiator(instantiator)
                .withObjectFactory(objectFactory)
                .withJavaModuleDetector(javaModuleDetector)
                .build()
        }
    }

    private class BuildSessionProcessServices : ServiceRegistrationProvider {
        @Provides
        fun decorateExecFactory(
            execFactory: ExecFactory,
            fileResolver: FileResolver,
            fileCollectionFactory: FileCollectionFactory,
            instantiator: Instantiator,
            buildCancellationToken: BuildCancellationToken,
            objectFactory: ObjectFactory,
            javaModuleDetector: JavaModuleDetector
        ): ExecFactory {
            return execFactory.forContext()
                .withFileResolver(fileResolver)
                .withFileCollectionFactory(fileCollectionFactory)
                .withInstantiator(instantiator)
                .withBuildCancellationToken(buildCancellationToken)
                .withObjectFactory(objectFactory)
                .withJavaModuleDetector(javaModuleDetector)
                .build()
        }
    }

    private class BuildProcessServices : ServiceRegistrationProvider {
        @Provides
        fun configure(registration: ServiceRegistration) {
            registration.add<DefaultExecOperations>(ExecOperations::class.java, DefaultExecOperations::class.java)
        }

        @Provides
        fun decorateExecFactory(
            parent: ExecFactory,
            fileResolver: FileResolver,
            fileCollectionFactory: FileCollectionFactory,
            instantiator: Instantiator,
            objectFactory: ObjectFactory,
            javaModuleDetector: JavaModuleDetector,
            listenerManager: ListenerManager
        ): ExecFactory {
            return parent.forContext()
                .withFileResolver(fileResolver)
                .withFileCollectionFactory(fileCollectionFactory)
                .withInstantiator(instantiator)
                .withObjectFactory(objectFactory)
                .withJavaModuleDetector(javaModuleDetector)
                .withExternalProcessStartedListener(listenerManager.getBroadcaster<ExternalProcessStartedListener?>(ExternalProcessStartedListener::class.java))
                .build()
        }

        @Provides
        fun createExecSpecFactory(execActionFactory: ExecActionFactory): ExecSpecFactory {
            return DefaultExecSpecFactory(execActionFactory)
        }
    }

    private class ProjectProcessServices : ServiceRegistrationProvider {
        @Provides
        fun decorateExecFactory(
            execFactory: ExecFactory,
            fileResolver: FileResolver,
            fileCollectionFactory: FileCollectionFactory,
            instantiatorFactory: InstantiatorFactory,
            objectFactory: ObjectFactory,
            javaModuleDetector: JavaModuleDetector,
            listenerManager: ListenerManager
        ): ExecFactory {
            return execFactory.forContext()
                .withFileResolver(fileResolver)
                .withFileCollectionFactory(fileCollectionFactory)
                .withInstantiator(instantiatorFactory.decorateLenient())
                .withObjectFactory(objectFactory)
                .withJavaModuleDetector(javaModuleDetector)
                .withExternalProcessStartedListener(listenerManager.getBroadcaster<ExternalProcessStartedListener?>(ExternalProcessStartedListener::class.java))
                .build()
        }
    }
}
