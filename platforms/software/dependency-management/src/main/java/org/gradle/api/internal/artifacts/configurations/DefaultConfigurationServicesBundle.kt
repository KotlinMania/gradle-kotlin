/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.ConfigurationServicesBundle
import org.gradle.api.internal.artifacts.ResolveExceptionMapper
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationRunner

/**
 * Default implementation of services bundle used by [DefaultConfiguration].
 *
 *
 * This type exists to minimize the number of references that each configuration needs to maintain, thus reducing memory usage
 * and improving performance in large projects with many configurations.
 *
 *
 * Every service, factory, or other type in this bundle **must** be effectively immutable.
 */
class DefaultConfigurationServicesBundle(
    val buildOperationRunner: BuildOperationRunner,
    val projectStateRegistry: ProjectStateRegistry,
    val calculatedValueContainerFactory: CalculatedValueContainerFactory,
    val objectFactory: ObjectFactory,
    val fileCollectionFactory: FileCollectionFactory,
    val taskDependencyFactory: TaskDependencyFactory,
    val attributesFactory: AttributesFactory,
    val domainObjectCollectionFactory: DomainObjectCollectionFactory,
    val collectionCallbackActionDecorator: CollectionCallbackActionDecorator,
    val problems: ProblemsInternal,
    val attributeDesugaring: AttributeDesugaring,
    val exceptionMapper: ResolveExceptionMapper,
    val providerFactory: ProviderFactory
) : ConfigurationServicesBundle
