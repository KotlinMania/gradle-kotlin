/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.resolver

import org.gradle.api.artifacts.ComponentMetadataListerDetails
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess
import org.gradle.api.internal.artifacts.repositories.descriptor.MavenRepositoryDescriptor
import org.gradle.api.internal.artifacts.repositories.maven.MavenMetadata
import org.gradle.api.internal.artifacts.repositories.maven.MavenMetadataLoader
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataArtifactProvider
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.component.ArtifactType
import org.gradle.api.resources.MissingResourceException
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.component.model.MutableModuleSources.Companion.of
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.ResourceAwareResolveResult
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import java.net.URI
import java.util.regex.Matcher
import java.util.regex.Pattern

class MavenResolver(
    descriptor: MavenRepositoryDescriptor,
    val root: URI,
    transport: RepositoryTransport,
    locallyAvailableResourceFinder: LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata>,
    artifactFileStore: FileStore<ModuleComponentArtifactIdentifier>,
    metadataSources: ImmutableMetadataSources,
    metadataArtifactProvider: MetadataArtifactProvider,
    private val mavenMetaDataLoader: MavenMetadataLoader,
    componentMetadataSupplierFactory: InstantiatingAction<ComponentMetadataSupplierDetails>?,
    versionListerFactory: InstantiatingAction<ComponentMetadataListerDetails>?,
    injector: Instantiator,
    checksumService: ChecksumService,
    continueOnConnectionFailure: Boolean
) : ExternalResourceResolver(
    descriptor, transport.isLocal(),
    transport.getRepository(),
    transport.getResourceAccessor(),
    locallyAvailableResourceFinder,
    artifactFileStore,
    metadataSources,
    metadataArtifactProvider,
    componentMetadataSupplierFactory,
    versionListerFactory,
    injector,
    checksumService,
    continueOnConnectionFailure
) {
    private val localAccess: MavenLocalRepositoryAccess = MavenResolver.MavenLocalRepositoryAccess()
    private val remoteAccess: MavenRemoteRepositoryAccess = MavenResolver.MavenRemoteRepositoryAccess()

    override fun toString(): String {
        return "Maven repository '" + getName() + "'"
    }

    override fun doResolveComponentMetaData(
        moduleComponentIdentifier: ModuleComponentIdentifier,
        prescribedMetaData: ComponentOverrideMetadata,
        result: BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata?>
    ) {
        val uniqueSnapshotVersion = if (isNonUniqueSnapshot(moduleComponentIdentifier))
            findUniqueSnapshotVersion(moduleComponentIdentifier, result)
        else
            composeUniqueSnapshotVersion(moduleComponentIdentifier)

        if (uniqueSnapshotVersion != null) {
            val snapshotIdentifier = composeSnapshotIdentifier(moduleComponentIdentifier, uniqueSnapshotVersion)
            resolveUniqueSnapshotDependency(snapshotIdentifier, prescribedMetaData, result, uniqueSnapshotVersion)
        } else {
            resolveStaticDependency(moduleComponentIdentifier, prescribedMetaData, result, super.createArtifactResolver())
        }
    }

    override fun isMetaDataArtifact(artifactType: ArtifactType): Boolean {
        return artifactType == ArtifactType.MAVEN_POM
    }

    private fun resolveUniqueSnapshotDependency(
        module: MavenUniqueSnapshotComponentIdentifier,
        prescribedMetaData: ComponentOverrideMetadata,
        result: BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata?>,
        snapshotSource: MavenUniqueSnapshotModuleSource
    ) {
        resolveStaticDependency(module, prescribedMetaData, result, createArtifactResolver(of(snapshotSource)))
    }

    override fun createArtifactResolver(moduleSources: ModuleSources?): ExternalResourceArtifactResolver {
        if (moduleSources == null) {
            return super.createArtifactResolver(null)
        }

        return moduleSources.withSource<MavenUniqueSnapshotModuleSource?, ExternalResourceArtifactResolver?>(
            org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotModuleSource::class.java,
            java.util.function.Function { source: java.util.Optional<org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotModuleSource?>? ->
                if (source.isPresent()) {
                    return@withSource org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotExternalResourceArtifactResolver(
                        super.createArtifactResolver(moduleSources),
                        source.get()
                    )
                } else {
                    return@withSource super.createArtifactResolver(moduleSources)
                }
            })!!
    }

    private val wholePattern: M2ResourcePattern
        get() = M2ResourcePattern(root, MavenPattern.M2_PATTERN)

    private fun findUniqueSnapshotVersion(module: ModuleComponentIdentifier, result: ResourceAwareResolveResult): MavenUniqueSnapshotModuleSource? {
        val wholePattern = this.wholePattern
        if (!wholePattern.isComplete(module)) {
            //do not attempt to download maven-metadata.xml for incomplete identifiers
            return null
        }
        val metadataLocation = wholePattern.toModuleVersionPath(module).resolve("maven-metadata.xml")
        result.attempted(metadataLocation)
        val mavenMetadata = parseMavenMetadata(metadataLocation)

        if (mavenMetadata.timestamp != null) {
            // we have found a timestamp, so this is a snapshot unique version
            val timestamp = mavenMetadata.timestamp + "-" + mavenMetadata.buildNumber
            return MavenUniqueSnapshotModuleSource(timestamp)
        }
        return null
    }

    private fun composeUniqueSnapshotVersion(moduleComponentIdentifier: ModuleComponentIdentifier): MavenUniqueSnapshotModuleSource? {
        val matcher: Matcher = UNIQUE_SNAPSHOT.matcher(moduleComponentIdentifier.getVersion())
        if (!matcher.matches()) {
            return null
        }
        return MavenUniqueSnapshotModuleSource(matcher.group(1))
    }

    private fun parseMavenMetadata(metadataLocation: ExternalResourceName): MavenMetadata {
        try {
            return mavenMetaDataLoader.load(metadataLocation)
        } catch (e: MissingResourceException) {
            return MavenMetadata()
        }
    }

    override fun getLocalAccess(): ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> {
        return localAccess
    }

    override fun getRemoteAccess(): ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> {
        return remoteAccess
    }

    private inner class MavenLocalRepositoryAccess : LocalRepositoryAccess() {
        override fun resolveJavadocArtifacts(module: ComponentArtifactResolveMetadata, result: BuildableArtifactSetResolveResult) {
            // Javadoc artifacts are optional, so we need to probe for them remotely
        }

        override fun resolveSourceArtifacts(module: ComponentArtifactResolveMetadata, result: BuildableArtifactSetResolveResult) {
            // Source artifacts are optional, so we need to probe for them remotely
        }
    }

    private inner class MavenRemoteRepositoryAccess : RemoteRepositoryAccess()

    private fun composeSnapshotIdentifier(moduleComponentIdentifier: ModuleComponentIdentifier, uniqueSnapshotVersion: MavenUniqueSnapshotModuleSource): MavenUniqueSnapshotComponentIdentifier {
        return MavenUniqueSnapshotComponentIdentifier(
            moduleComponentIdentifier.getModuleIdentifier(),
            moduleComponentIdentifier.getVersion(),
            uniqueSnapshotVersion.getTimestamp()
        )
    }

    companion object {
        private val UNIQUE_SNAPSHOT: Pattern = Pattern.compile("(?:.+)-(\\d{8}\\.\\d{6}-\\d+)")
        fun processMetaData(metaData: MutableMavenModuleResolveMetadata): MutableMavenModuleResolveMetadata {
            val id: ModuleComponentIdentifier = metaData.id
            if (isNonUniqueSnapshot(id)) {
                metaData.isChanging = true
            }
            if (isUniqueSnapshot(id)) {
                val mus = id as MavenUniqueSnapshotComponentIdentifier
                metaData.snapshotTimestamp = mus.getTimestamp()
            }
            return metaData
        }

        private fun isUniqueSnapshot(id: ModuleComponentIdentifier): Boolean {
            return id is MavenUniqueSnapshotComponentIdentifier
        }

        protected fun isNonUniqueSnapshot(moduleComponentIdentifier: ModuleComponentIdentifier): Boolean {
            return moduleComponentIdentifier.getVersion().endsWith("-SNAPSHOT")
        }
    }
}
