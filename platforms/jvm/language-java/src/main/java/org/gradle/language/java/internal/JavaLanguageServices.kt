/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.language.java.internal

import org.gradle.api.internal.component.ArtifactType
import org.gradle.api.internal.component.ComponentTypeRegistry
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector
import org.gradle.api.internal.tasks.compile.tooling.JavaCompileTaskSuccessResultPostProcessor
import org.gradle.api.logging.configuration.LoggingConfiguration
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.cache.internal.FileContentCacheFactory
import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.build.event.OperationResultPostProcessor
import org.gradle.internal.build.event.OperationResultPostProcessorFactory
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.AbstractGradleModuleServices
import org.gradle.jvm.JvmLibrary
import org.gradle.language.java.artifact.JavadocArtifact
import org.gradle.tooling.events.OperationType
import org.slf4j.LoggerFactory

class JavaLanguageServices : AbstractGradleModuleServices() {
    public override fun registerGlobalServices(registration: ServiceRegistration) {
        registration.addProvider(JavaGlobalScopeServices())
    }

    public override fun registerBuildServices(registration: ServiceRegistration) {
        registration.addProvider(JavaBuildScopeServices())
    }

    public override fun registerBuildTreeServices(registration: ServiceRegistration) {
        registration.addProvider(object : ServiceRegistrationProvider {
            @Provides
            fun createAnnotationProcessorDetector(cacheFactory: FileContentCacheFactory, loggingConfiguration: LoggingConfiguration): AnnotationProcessorDetector {
                return AnnotationProcessorDetector(
                    cacheFactory,
                    LoggerFactory.getLogger(AnnotationProcessorDetector::class.java),
                    loggingConfiguration.showStacktrace !== ShowStacktrace.INTERNAL_EXCEPTIONS
                )
            }
        })
    }

    private class JavaGlobalScopeServices : ServiceRegistrationProvider {
        @Provides
        fun createJavaSubscribableBuildActionRunnerRegistration(): OperationResultPostProcessorFactory {
            return OperationResultPostProcessorFactory { clientSubscriptions: BuildEventSubscriptions?, consumer: BuildEventConsumer? ->
                if (clientSubscriptions!!.isRequested(OperationType.TASK)) mutableListOf<OperationResultPostProcessor>(JavaCompileTaskSuccessResultPostProcessor()) else
                    mutableListOf<OperationResultPostProcessor>()
            }
        }
    }

    private class JavaBuildScopeServices : ServiceRegistrationProvider {
        @Provides  //registration
        fun configure(registration: ServiceRegistration, componentTypeRegistry: ComponentTypeRegistry) {
            componentTypeRegistry.maybeRegisterComponentType(JvmLibrary::class.java)
                .registerArtifactType(JavadocArtifact::class.java, ArtifactType.JAVADOC)
        }
    }
}
