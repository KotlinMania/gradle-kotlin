/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories

import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.repositories.descriptor.FlatDirRepositoryDescriptor
import org.gradle.api.internal.artifacts.repositories.descriptor.IvyRepositoryDescriptor
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultArtifactMetadataSource
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultImmutableMetadataSources
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMetadataArtifactProvider
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataSource
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResolver
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resolve.caching.ImplicitInputRecorder
import org.gradle.internal.resolve.caching.ImplicitInputsProvidingService
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.util.internal.CollectionUtils
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.Arrays
import javax.inject.Inject

abstract class DefaultFlatDirArtifactRepository @Inject constructor(
    private val fileCollectionFactory: FileCollectionFactory,
    private val transportFactory: RepositoryTransportFactory,
    private val locallyAvailableResourceFinder: LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata>,
    private val artifactFileStore: FileStore<ModuleComponentArtifactIdentifier>,
    private val metadataFactory: IvyMutableModuleMetadataFactory,
    private val instantiatorFactory: InstantiatorFactory,
    objectFactory: ObjectFactory,
    private val checksumService: ChecksumService,
    versionParser: VersionParser
) : AbstractResolutionAwareArtifactRepository<FlatDirRepositoryDescriptor>(objectFactory, versionParser), FlatDirectoryArtifactRepository, ResolutionAwareRepository {
    private val dirs: MutableList<Any> = ArrayList<Any>()

    override fun getDisplayName(): String {
        val dirs = getDirs()
        if (dirs.isEmpty()) {
            return super.getDisplayName()
        }
        return super.getDisplayName() + '(' + Joiner.on(", ").join(dirs) + ')'
    }

    override fun getDirs(): MutableSet<File> {
        return fileCollectionFactory.resolving(dirs).getFiles()
    }

    override fun setDirs(dirs: MutableSet<File>) {
        setDirs(dirs as Iterable<*>)
    }

    override fun setDirs(dirs: Iterable<*>) {
        invalidateDescriptor()
        this.dirs.clear()
        CollectionUtils.addAll(this.dirs, dirs)
    }

    override fun dir(dir: Any) {
        dirs(dir)
    }

    override fun dirs(vararg dirs: Any) {
        invalidateDescriptor()
        this.dirs.addAll(Arrays.asList<Any>(*dirs))
    }

    override fun createResolver(): ConfiguredModuleComponentRepository {
        return createRealResolver()
    }

    override fun createDescriptor(): FlatDirRepositoryDescriptor {
        val builder = IvyRepositoryDescriptor.Builder(getName(), null)
        builder.setM2Compatible(false)
        builder.setLayoutType("Unknown")
        builder.setMetadataSources(ImmutableList.of<String>())
        builder.setAuthenticated(false)
        builder.setAuthenticationSchemes(ImmutableList.of<String>())
        for (root in getDirs()) {
            builder.addArtifactResource(root.toURI(), "/[artifact]-[revision](-[classifier]).[ext]")
            builder.addArtifactResource(root.toURI(), "/[artifact](-[classifier]).[ext]")
        }
        val ivyDescriptor = builder.create()
        return FlatDirRepositoryDescriptor(getName(), getDirs(), ivyDescriptor)
    }

    override fun createRepositoryAccessor(transport: RepositoryTransport, rootUri: URI?, externalResourcesFileStore: FileStore<String>): RepositoryResourceAccessor {
        return NoOpRepositoryResourceAccessor()
    }

    private fun createRealResolver(): IvyResolver {
        val descriptor = getDescriptor()
        val dirs: MutableList<File> = descriptor.getDirs()
        if (dirs.isEmpty()) {
            throw InvalidUserDataException("You must specify at least one directory for a flat directory repository.")
        }

        val transport = transportFactory.createFileTransport(getName())
        val injector: Instantiator = createInjectorForMetadataSuppliers(transport, instantiatorFactory, null, null)
        return IvyResolver(
            descriptor.getBackingDescriptor(),
            transport,
            locallyAvailableResourceFinder,
            false,
            artifactFileStore,
            null,
            null,
            createMetadataSources(),
            IvyMetadataArtifactProvider.Companion.INSTANCE,
            injector,
            checksumService,
            false
        )
    }

    private fun createMetadataSources(): ImmutableMetadataSources {
        val artifactMetadataSource: MetadataSource<MutableModuleComponentResolveMetadata> = DefaultArtifactMetadataSource(metadataFactory)
        return DefaultImmutableMetadataSources(mutableListOf<MetadataSource<*>>(artifactMetadataSource))
    }

    private class NoOpRepositoryResourceAccessor : RepositoryResourceAccessor, ImplicitInputsProvidingService<String?, Long?, RepositoryResourceAccessor?> {
        override fun withResource(relativePath: String, action: Action<in InputStream>) {
            // No-op
        }

        override fun withImplicitInputRecorder(registrar: ImplicitInputRecorder): RepositoryResourceAccessor {
            // Service calls have no effect, no need to register them
            return this
        }

        override fun isUpToDate(s: String, oldValue: Long?): Boolean {
            // Nothing accessible, always up to date
            return true
        }
    }
}
