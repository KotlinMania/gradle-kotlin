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
package org.gradle.api.internal.artifacts

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyContextManager
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultDependencyMetadataFactory
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultExcludeRuleConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyMetadataFactory
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ExcludeRuleConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ExternalModuleDependencyMetadataConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ProjectDependencyMetadataConverter
import org.gradle.api.internal.artifacts.transform.CacheableTransformTypeAnnotationHandler
import org.gradle.api.internal.artifacts.transform.InputArtifactAnnotationHandler
import org.gradle.api.internal.artifacts.transform.InputArtifactDependenciesAnnotationHandler
import org.gradle.api.internal.artifacts.transform.TransformActionScheme
import org.gradle.api.internal.artifacts.transform.TransformParameterScheme
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.api.internal.tasks.properties.InspectionSchemeFactory
import org.gradle.api.model.ReplacedBy
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.cache.internal.ProducerGuard
import org.gradle.internal.instantiation.InjectAnnotationHandler
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.properties.annotations.PropertyAnnotationHandler
import org.gradle.internal.properties.annotations.TypeAnnotationHandler
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.connector.ResourceConnectorFactory
import org.gradle.internal.resource.transport.file.FileConnectorFactory
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.work.Incremental
import org.gradle.work.NormalizeLineEndings

internal class DependencyManagementGlobalScopeServices : ServiceRegistrationProvider {
    fun configure(registration: ServiceRegistration) {
        registration.add(VersionParser::class.java)
        registration.add<DefaultIvyContextManager?>(IvyContextManager::class.java, DefaultIvyContextManager::class.java)
        registration.add<DefaultImmutableModuleIdentifierFactory?>(ImmutableModuleIdentifierFactory::class.java, DefaultImmutableModuleIdentifierFactory::class.java)
        registration.add<DefaultExcludeRuleConverter?>(ExcludeRuleConverter::class.java, DefaultExcludeRuleConverter::class.java)
        registration.add<InputArtifactAnnotationHandler?>(PropertyAnnotationHandler::class.java, InjectAnnotationHandler::class.java, InputArtifactAnnotationHandler::class.java)
        registration.add<InputArtifactDependenciesAnnotationHandler?>(
            PropertyAnnotationHandler::class.java,
            InjectAnnotationHandler::class.java,
            InputArtifactDependenciesAnnotationHandler::class.java
        )
    }

    @Provides
    fun createDependencyMetadataFactory(excludeRuleConverter: ExcludeRuleConverter): DependencyMetadataFactory {
        return DefaultDependencyMetadataFactory(
            ProjectDependencyMetadataConverter(excludeRuleConverter),
            ExternalModuleDependencyMetadataConverter(excludeRuleConverter)
        )
    }

    @Provides
    fun createFileConnectorFactory(): ResourceConnectorFactory {
        return FileConnectorFactory()
    }

    @Provides
    fun createProducerAccess(): ProducerGuard<ExternalResourceName?> {
        return ProducerGuard.adaptive<ExternalResourceName?>()
    }

    @Provides
    fun createCacheableTransformAnnotationHandler(): TypeAnnotationHandler {
        return CacheableTransformTypeAnnotationHandler()
    }

    @Provides
    fun createPlatformSupport(instantiator: NamedObjectInstantiator): PlatformSupport {
        return PlatformSupport(instantiator)
    }

    @Provides
    fun createTransformParameterScheme(inspectionSchemeFactory: InspectionSchemeFactory, instantiatorFactory: InstantiatorFactory): TransformParameterScheme {
        val instantiationScheme = instantiatorFactory.decorateScheme()
        val inspectionScheme = inspectionSchemeFactory.inspectionScheme(
            ImmutableSet.of<Class<out Annotation?>?>(
                Console::class.java,
                Input::class.java,
                InputDirectory::class.java,
                InputFile::class.java,
                InputFiles::class.java,
                Internal::class.java,
                Nested::class.java,
                ReplacedBy::class.java,
                ServiceReference::class.java
            ),
            ImmutableSet.of<Class<out Annotation?>?>(
                Classpath::class.java,
                CompileClasspath::class.java,
                Incremental::class.java,
                Optional::class.java,
                PathSensitive::class.java,
                IgnoreEmptyDirectories::class.java,
                NormalizeLineEndings::class.java
            ),
            ImmutableSet.of<Class<out Annotation?>?>(),
            instantiationScheme
        )
        return TransformParameterScheme(instantiationScheme, inspectionScheme)
    }

    @Provides
    fun createTransformActionScheme(inspectionSchemeFactory: InspectionSchemeFactory, instantiatorFactory: InstantiatorFactory): TransformActionScheme {
        val instantiationScheme = instantiatorFactory.injectScheme(
            ImmutableSet.of<Class<out Annotation?>?>(
                InputArtifact::class.java,
                InputArtifactDependencies::class.java
            )
        )
        val inspectionScheme = inspectionSchemeFactory.inspectionScheme(
            ImmutableSet.of<Class<out Annotation?>?>(
                InputArtifact::class.java,
                InputArtifactDependencies::class.java
            ),
            ImmutableSet.of<Class<out Annotation?>?>(
                Classpath::class.java,
                CompileClasspath::class.java,
                Incremental::class.java,
                Optional::class.java,
                PathSensitive::class.java,
                IgnoreEmptyDirectories::class.java,
                NormalizeLineEndings::class.java
            ),
            ImmutableSet.of<Class<out Annotation?>?>(),
            instantiationScheme
        )
        return TransformActionScheme(instantiationScheme, inspectionScheme)
    }
}
