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
package org.gradle.nativeplatform.internal.prebuilt

import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor
import org.gradle.api.internal.AbstractNamedDomainObjectContainer
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.nativeplatform.PrebuiltLibraries
import org.gradle.nativeplatform.PrebuiltLibrary

class DefaultPrebuiltLibraries(
    private var name: String,
    instantiator: Instantiator,
    private val objectFactory: ObjectFactory?,
    private val libraryInitializer: Action<PrebuiltLibrary?>,
    collectionCallbackActionDecorator: CollectionCallbackActionDecorator,
    private val domainObjectCollectionFactory: DomainObjectCollectionFactory?
) : AbstractNamedDomainObjectContainer<PrebuiltLibrary?>(
    PrebuiltLibrary::class.java, instantiator, collectionCallbackActionDecorator
), PrebuiltLibraries {
    override fun getName(): String {
        return name
    }

    override fun setName(name: String) {
        this.name = name
    }

    override fun content(configureAction: Action<in RepositoryContentDescriptor?>) {
        throw UnsupportedOperationException()
    }

    override fun doCreate(name: String): PrebuiltLibrary {
        return getInstantiator().newInstance<DefaultPrebuiltLibrary>(DefaultPrebuiltLibrary::class.java, name, objectFactory, domainObjectCollectionFactory)
    }

    override fun resolveLibrary(name: String): PrebuiltLibrary? {
        val library = findByName(name)
        if (library != null) {
            synchronized(library.getBinaries()) {
                if (library.getBinaries().isEmpty()) {
                    libraryInitializer.execute(library)
                }
            }
        }
        return library
    }
}
