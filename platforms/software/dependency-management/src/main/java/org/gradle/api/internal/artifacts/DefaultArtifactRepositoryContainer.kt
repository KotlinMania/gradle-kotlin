/*
 * Copyright 2010 the original author or authors.
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

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Namer
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.UnknownRepositoryException
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DefaultNamedDomainObjectList
import org.gradle.api.internal.InternalAction
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal
import org.gradle.internal.Actions
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.internal.ConfigureUtil
import org.gradle.util.internal.GUtil

open class DefaultArtifactRepositoryContainer(instantiator: Instantiator, callbackActionDecorator: CollectionCallbackActionDecorator) : DefaultNamedDomainObjectList<ArtifactRepository?>(
    ArtifactRepository::class.java, instantiator, RepositoryNamer(), callbackActionDecorator
), ArtifactRepositoryContainer {
    private val addLastAction = Action { toAdd: ArtifactRepository? -> super@DefaultArtifactRepositoryContainer.add(toAdd) }

    init {
        whenObjectAdded(InternalAction { artifactRepository: ArtifactRepository? ->
            if (artifactRepository is ArtifactRepositoryInternal) {
                val repository = artifactRepository
                repository.onAddToContainer(this@DefaultArtifactRepositoryContainer)
            }
        })
    }

    private class RepositoryNamer : Namer<ArtifactRepository?> {
        override fun determineName(r: ArtifactRepository): String {
            return r.getName()
        }
    }

    public override fun getTypeDisplayName(): String {
        return "repository"
    }

    override fun configure(closure: Closure<*>?): DefaultArtifactRepositoryContainer? {
        return ConfigureUtil.configureSelf<DefaultArtifactRepositoryContainer?>(closure, this)
    }

    override fun addFirst(repository: ArtifactRepository) {
        add(0, repository)
    }

    override fun addLast(repository: ArtifactRepository) {
        add(repository)
    }

    override fun createNotFoundException(name: String): UnknownDomainObjectException {
        return UnknownRepositoryException(String.format("Repository with name '%s' not found.", name))
    }

    fun <T : ArtifactRepository?> addRepository(repository: T?, defaultName: String): T? {
        return addRepository<T?>(repository, defaultName, Actions.doNothing<T?>())
    }

    fun <T : ArtifactRepository?> addRepository(repository: T?, defaultName: String, configureAction: Action<in T?>): T? {
        configureAction.execute(repository)
        return addWithUniqueName<T?>(repository, defaultName, addLastAction)
    }

    private fun <T : ArtifactRepository?> addWithUniqueName(repository: T?, defaultName: String, insertion: Action<in T?>): T? {
        val repositoryName = repository!!.getName()
        if (!GUtil.isTrue(repositoryName)) {
            repository.setName(uniquifyName(defaultName))
        } else {
            repository.setName(uniquifyName(repositoryName))
        }

        assertElementNotPresent(repository.getName())
        insertion.execute(repository)
        return repository
    }

    private fun uniquifyName(proposedName: String): String {
        if (findByName(proposedName) == null) {
            return proposedName
        }
        run {
            var index = 2
            while (true) {
                val candidate = proposedName + index
                if (findByName(candidate) == null) {
                    return candidate
                }
                index++
            }
        }
    }
}
