/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.Project
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal
import org.gradle.api.internal.artifacts.transform.TransformStepNodeDependencyResolver
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.filestore.DefaultArtifactIdentifierFileStore
import org.gradle.api.internal.notations.DependencyNotationParser
import org.gradle.api.internal.notations.ProjectDependencyFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.problems.Problems
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resource.cached.DefaultExternalResourceFileStore
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.util.internal.SimpleMapInterner

/**
 * The set of dependency management services that are created per project.
 */
internal class DependencyManagementProjectScopeServices : ServiceRegistrationProvider {
    fun configure(registration: ServiceRegistration) {
        registration.add(DefaultExternalResourceFileStore.Factory::class.java)
        registration.add(DefaultArtifactIdentifierFileStore.Factory::class.java)
        registration.add(TransformStepNodeDependencyResolver::class.java)
        registration.add(DependencyManagementManagedTypesFactory::class.java)
        registration.add(DefaultProjectDependencyFactory::class.java)
    }

    @Provides
    fun createDependencyFactory(
        instantiator: Instantiator?,
        factory: DefaultProjectDependencyFactory,
        classPathRegistry: ClassPathRegistry?,
        fileCollectionFactory: FileCollectionFactory?,
        runtimeShadedJarFactory: RuntimeShadedJarFactory?,
        attributesFactory: AttributesFactory?,
        stringInterner: SimpleMapInterner?,
        capabilityNotationParser: CapabilityNotationParser?,
        objectFactory: ObjectFactory?,
        project: Project?,
        problems: Problems?
    ): DependencyFactoryInternal {
        val projectDependencyFactory = ProjectDependencyFactory(factory)

        val dependencyNotationParser: DependencyNotationParser? = DependencyNotationParser.create(
            instantiator!!,
            factory,
            classPathRegistry,
            fileCollectionFactory,
            runtimeShadedJarFactory,
            stringInterner,
            problems
        )

        return DefaultDependencyFactory(
            instantiator,
            dependencyNotationParser,
            capabilityNotationParser,
            objectFactory,
            projectDependencyFactory,
            attributesFactory,
            project
        )
    }

    @Provides
    protected fun createDependencyMetaDataProvider(project: ProjectInternal): DependencyMetaDataProvider {
        return ProjectBackedModuleMetaDataProvider(project)
    }

    private class ProjectBackedModuleMetaDataProvider(private val project: ProjectInternal) : DependencyMetaDataProvider {
        override fun getModule(): Module {
            return ProjectBackedModule(project)
        }
    }
}
