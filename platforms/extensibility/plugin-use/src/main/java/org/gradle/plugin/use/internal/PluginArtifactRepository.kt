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
package org.gradle.plugin.use.internal

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal
import org.gradle.api.internal.artifacts.repositories.ArtifactResolutionDetails
import org.gradle.api.internal.artifacts.repositories.ContentFilteringRepository
import org.gradle.api.internal.artifacts.repositories.RepositoryContentDescriptorInternal
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.internal.artifacts.repositories.descriptor.RepositoryDescriptor

internal class PluginArtifactRepository(delegate: ArtifactRepository) : ArtifactRepositoryInternal, ContentFilteringRepository, ResolutionAwareRepository {
    private val delegate: ArtifactRepositoryInternal
    private val resolutionAwareDelegate: ResolutionAwareRepository
    private val repositoryContentDescriptor: RepositoryContentDescriptorInternal

    init {
        this.delegate = delegate as ArtifactRepositoryInternal
        this.resolutionAwareDelegate = delegate as ResolutionAwareRepository
        this.repositoryContentDescriptor = this.delegate.getRepositoryDescriptorCopy()
    }

    override fun getName(): String {
        return REPOSITORY_NAME_PREFIX + delegate.getName()
    }

    override fun setName(name: String) {
        delegate.setName(name)
    }

    override fun content(configureAction: Action<in RepositoryContentDescriptor>) {
        configureAction.execute(repositoryContentDescriptor)
    }

    override fun getContentFilter(): Action<in ArtifactResolutionDetails> {
        return repositoryContentDescriptor.toContentFilter()
    }

    override fun getIncludedConfigurations(): MutableSet<String>? {
        return repositoryContentDescriptor.getIncludedConfigurations()
    }

    override fun getExcludedConfigurations(): MutableSet<String>? {
        return repositoryContentDescriptor.getExcludedConfigurations()
    }

    override fun getRequiredAttributes(): MutableMap<Attribute<Any>, MutableSet<Any>>? {
        return repositoryContentDescriptor.getRequiredAttributes()
    }

    override fun getDisplayName(): String {
        return delegate.getDisplayName()
    }

    override fun createResolver(): ConfiguredModuleComponentRepository {
        return resolutionAwareDelegate.createResolver()
    }

    override fun getDescriptor(): RepositoryDescriptor {
        return resolutionAwareDelegate.getDescriptor()
    }

    override fun onAddToContainer(container: NamedDomainObjectCollection<ArtifactRepository>) {
        delegate.onAddToContainer(container)
    }

    override fun createRepositoryDescriptor(versionParser: VersionParser): RepositoryContentDescriptorInternal {
        return delegate.createRepositoryDescriptor(versionParser)
    }

    override fun getRepositoryDescriptorCopy(): RepositoryContentDescriptorInternal {
        return repositoryContentDescriptor.asMutableCopy()
    }

    companion object {
        private const val REPOSITORY_NAME_PREFIX = "__plugin_repository__"
    }
}
