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
package org.gradle.nativeplatform.internal.resolve

import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.resolve.ProjectModelResolver
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.nativeplatform.internal.prebuilt.PrebuiltLibraryBinaryLocator

class NativeDependencyResolverServices : ServiceRegistrationProvider {
    @Provides
    fun createLibraryBinaryLocator(projectModelResolver: ProjectModelResolver?, domainObjectCollectionFactory: DomainObjectCollectionFactory?): LibraryBinaryLocator {
        val locators: MutableList<LibraryBinaryLocator?> = ArrayList<LibraryBinaryLocator?>()
        locators.add(ProjectLibraryBinaryLocator(projectModelResolver, domainObjectCollectionFactory))
        locators.add(PrebuiltLibraryBinaryLocator(projectModelResolver))
        return CachingLibraryBinaryLocator(ChainedLibraryBinaryLocator(locators), domainObjectCollectionFactory)
    }

    @Provides
    fun createResolver(locator: LibraryBinaryLocator?, fileCollectionFactory: FileCollectionFactory?): NativeDependencyResolver {
        var resolver: NativeDependencyResolver = LibraryNativeDependencyResolver(locator)
        resolver = ApiRequirementNativeDependencyResolver(resolver)
        resolver = RequirementParsingNativeDependencyResolver(resolver)
        resolver = SourceSetNativeDependencyResolver(resolver, fileCollectionFactory)
        return InputHandlingNativeDependencyResolver(resolver)
    }
}
