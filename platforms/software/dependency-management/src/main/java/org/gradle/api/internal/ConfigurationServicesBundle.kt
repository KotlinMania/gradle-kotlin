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
package org.gradle.api.internal

import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * A bundle of services used by [Configuration].
 *
 *
 * This type exists to minimize the number of references that each configuration needs to maintain, thus reducing memory usage
 * and improving performance in large projects with many configurations.
 *
 *
 * Every service, factory, or other type in this bundle **must** be effectively immutable.
 */
@ServiceScope(Scope.Project::class)
interface ConfigurationServicesBundle {
    val buildOperationRunner: BuildOperationRunner?
    val projectStateRegistry: ProjectStateRegistry?
    val attributesFactory: AttributesFactory?
    val objectFactory: ObjectFactory?
    val taskDependencyFactory: TaskDependencyFactory?
    val domainObjectCollectionFactory: DomainObjectCollectionFactory?
    val calculatedValueContainerFactory: CalculatedValueContainerFactory?
    val fileCollectionFactory: FileCollectionFactory?
    val collectionCallbackActionDecorator: CollectionCallbackActionDecorator?
    val problems: ProblemsInternal?
    val attributeDesugaring: AttributeDesugaring?
    val exceptionMapper: ResolveExceptionMapper?
    val providerFactory: ProviderFactory?
}
