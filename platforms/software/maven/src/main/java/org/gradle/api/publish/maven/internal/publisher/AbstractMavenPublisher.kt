/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.publish.maven.internal.publisher

import org.apache.maven.artifact.repository.metadata.Metadata
import org.apache.maven.artifact.repository.metadata.Versioning
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer
import org.gradle.api.Action
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver.Companion.disableExtraChecksums
import org.gradle.api.internal.artifacts.repositories.transport.NetworkOperationBackOffAndRetry
import org.gradle.internal.Factory
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.HashFunction
import org.gradle.internal.hash.Hashing
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceReadResult
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.ReadableContent
import org.gradle.internal.resource.local.ByteArrayReadableContent
import org.gradle.internal.resource.local.FileReadableContent
import org.gradle.internal.xml.XmlTransformer
import org.jspecify.annotations.NullMarked
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.Writer
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.regex.Pattern

@NullMarked
internal abstract class AbstractMavenPublisher(private val temporaryDirFactory: Factory<File?>) : MavenPublisher {
    private val metadataRetryCaller = NetworkOperationBackOffAndRetry<ExternalResourceReadResult<Metadata>?>()
    private val xmlTransformer = XmlTransformer()

    protected fun publish(publication: MavenNormalizedPublication, repository: ExternalResourceRepository, rootUri: URI, localRepo: Boolean) {
        val groupId = publication.getGroupId()
        val artifactId = publication.getArtifactId()
        val version = publication.getVersion()

        val artifactPublisher = ModuleArtifactPublisher(repository, localRepo, rootUri, groupId, artifactId, version)
        val snapshotMetadataResult = computeSnapshotMetadata(publication, repository, version, artifactPublisher, groupId, artifactId)

        if (snapshotMetadataResult != null && !localRepo) {
            // Use the timestamped version for all published artifacts
            artifactPublisher.artifactVersion = snapshotMetadataResult.version
        }

        publishArtifactsAndMetadata(publication, artifactPublisher)

        publishPublicationMetadata(repository, version, artifactPublisher, groupId, artifactId, snapshotMetadataResult)
    }

    private fun computeSnapshotMetadata(
        publication: MavenNormalizedPublication,
        repository: ExternalResourceRepository,
        version: String,
        artifactPublisher: ModuleArtifactPublisher,
        groupId: String,
        artifactId: String
    ): SnapshotMetadataResult? {
        if (isSnapshot(version)) {
            val snapshotMetadataPath = artifactPublisher.snapshotMetadataLocation
            val snapshotMetadata = createSnapshotMetadata(publication, groupId, artifactId, version, repository, snapshotMetadataPath)
            return SnapshotMetadataResult(snapshotMetadataPath, snapshotMetadata)
        }
        return null
    }

    private fun publishPublicationMetadata(
        repository: ExternalResourceRepository,
        version: String,
        artifactPublisher: ModuleArtifactPublisher,
        groupId: String,
        artifactId: String,
        snapshotMetadataResult: SnapshotMetadataResult?
    ) {
        if (snapshotMetadataResult != null) {
            artifactPublisher.publish(snapshotMetadataResult.snapshotMetadataPath, writeMetadataToTmpFile(snapshotMetadataResult.snapshotMetadata, "snapshot-maven-metadata.xml"))
        }

        val externalResource = artifactPublisher.metadataLocation
        val metadata = createMetadata(groupId, artifactId, version, repository, externalResource)
        artifactPublisher.publish(externalResource, writeMetadataToTmpFile(metadata, "module-maven-metadata.xml"))
    }

    private fun createMetadata(groupId: String, artifactId: String, version: String, repository: ExternalResourceRepository, metadataResource: ExternalResourceName): Metadata {
        val versioning = getExistingVersioning(repository, metadataResource)
        if (!versioning.getVersions().contains(version)) {
            versioning.addVersion(version)
        }
        versioning.setLatest(version)
        if (!isSnapshot(version)) {
            versioning.setRelease(version)
        }
        versioning.updateTimestamp()

        val metadata = Metadata()
        metadata.setGroupId(groupId)
        metadata.setArtifactId(artifactId)
        metadata.setVersioning(versioning)
        return metadata
    }

    private fun getExistingVersioning(repository: ExternalResourceRepository, metadataResource: ExternalResourceName): Versioning {
        val existing: ExternalResourceReadResult<Metadata?>? = readExistingMetadata(repository, metadataResource)

        if (existing != null) {
            val recessive = existing.result
            if (recessive != null && recessive.getVersioning() != null) {
                return recessive.getVersioning()
            }
        }

        return Versioning()
    }

    private fun writeMetadataToTmpFile(metadata: Metadata, fileName: String): File {
        val metadataFile = File(temporaryDirFactory.create(), fileName)
        xmlTransformer.transform(metadataFile, POM_FILE_ENCODING, Action { writer: Writer? ->
            try {
                MetadataXpp3Writer().write(writer, metadata)
            } catch (e: IOException) {
                throw throwAsUncheckedException(e)
            }
        })
        return metadataFile
    }

    private fun isSnapshot(version: String?): Boolean {
        if (version != null) {
            if (version.regionMatches(
                    version.length - SNAPSHOT_VERSION.length,
                    SNAPSHOT_VERSION, 0, SNAPSHOT_VERSION.length, ignoreCase = true
                )
            ) {
                return true
            } else {
                return VERSION_FILE_PATTERN.matcher(version).matches()
            }
        }
        return false
    }

    fun readExistingMetadata(repository: ExternalResourceRepository, metadataResource: ExternalResourceName): ExternalResourceReadResult<Metadata?>? {
        return metadataRetryCaller.withBackoffAndRetry(object : Callable<ExternalResourceReadResult<Metadata>> {
            override fun call(): ExternalResourceReadResult<Metadata?>? {
                return repository.resource(metadataResource).withContentIfPresent<Metadata?>(ExternalResource.ContentAction { inputStream: InputStream? ->
                    try {
                        return@withContentIfPresent MetadataXpp3Reader().read(inputStream, false)
                    } catch (e: Exception) {
                        throw throwAsUncheckedException(e)
                    }
                })
            }

            override fun toString(): String {
                return "GET " + metadataResource.getDisplayName()
            }
        })
    }

    protected abstract fun createSnapshotMetadata(
        publication: MavenNormalizedPublication,
        groupId: String,
        artifactId: String,
        version: String,
        repository: ExternalResourceRepository,
        metadataResource: ExternalResourceName
    ): Metadata

    private class SnapshotMetadataResult(val snapshotMetadataPath: ExternalResourceName, val snapshotMetadata: Metadata) {
        val version: String
            /**
             * The timestamped version is hidden deep in `Metadata.versioning.snapshotVersions`
             *
             * @return The snapshot timestamped version
             */
            get() = snapshotMetadata.getVersioning().getSnapshotVersions().get(0).getVersion()
    }

    /**
     * Publishes artifacts for a single Maven module.
     */
    private class ModuleArtifactPublisher(
        repository: ExternalResourceRepository,
        private val localRepo: Boolean,
        private val rootUri: URI,
        groupId: String,
        private val artifactId: String,
        private val moduleVersion: String
    ) {
        private val networkOperationCaller = NetworkOperationBackOffAndRetry<Void?>()
        private val repository: ExternalResourceRepository
        private val groupPath: String
        private var artifactVersion: String

        init {
            this.repository = repository.withProgressLogging()!!
            this.groupPath = groupId.replace('.', '/')
            this.artifactVersion = moduleVersion
        }

        val metadataLocation: ExternalResourceName
            /**
             * Return the location of the module `maven-metadata.xml`, which lists all published versions for a Maven module.
             */
            get() {
                val path = groupPath + '/' + artifactId + '/' + this.metadataFileName
                return ExternalResourceName(rootUri, path)
            }

        val snapshotMetadataLocation: ExternalResourceName
            /**
             * Return the location of the snapshot `maven-metadata.xml`, which contains details of the latest published snapshot for a Maven module.
             */
            get() {
                val path = groupPath + '/' + artifactId + '/' + moduleVersion + '/' + this.metadataFileName
                return ExternalResourceName(rootUri, path)
            }

        val metadataFileName: String
            get() {
                if (localRepo) {
                    return "maven-metadata-local.xml"
                }
                return "maven-metadata.xml"
            }

        /**
         * Publishes a single module artifact, based on classifier and extension.
         */
        fun publish(classifier: String?, extension: String, content: File) {
            val path = StringBuilder(128)
            path.append(groupPath).append('/')
            path.append(artifactId).append('/')
            path.append(moduleVersion).append('/')
            path.append(artifactId).append('-').append(artifactVersion)

            if (classifier != null) {
                path.append('-').append(classifier)
            }
            if (extension.length > 0) {
                path.append('.').append(extension)
            }

            val externalResource = ExternalResourceName(rootUri, path.toString())
            publish(externalResource, content)
        }

        fun publish(externalResource: ExternalResourceName, content: File) {
            if (!localRepo) {
                LOGGER.info("Uploading {} to {}", externalResource.shortDisplayName, externalResource.path)
            }
            putResource(externalResource, FileReadableContent(content))
            if (!localRepo) {
                publishChecksums(externalResource, content)
            }
        }

        fun publishChecksums(destination: ExternalResourceName, content: File) {
            publishChecksum(destination, content, Hashing.sha1())
            publishChecksum(destination, content, Hashing.md5())
            if (!disableExtraChecksums()) {
                publishPossiblyUnsupportedChecksum(destination, content, Hashing.sha256())
                publishPossiblyUnsupportedChecksum(destination, content, Hashing.sha512())
            }
        }

        fun publishPossiblyUnsupportedChecksum(destination: ExternalResourceName, content: File, hashFunction: HashFunction) {
            try {
                publishChecksum(destination, content, hashFunction)
            } catch (ex: Exception) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.warn("Cannot upload checksum for " + content.getName() + " because the remote repository doesn't support " + hashFunction + ". This will not fail the build.", ex)
                } else {
                    LOGGER.warn("Cannot upload checksum for " + content.getName() + " because the remote repository doesn't support " + hashFunction + ". This will not fail the build.")
                }
            }
        }

        fun publishChecksum(destination: ExternalResourceName, content: File, hashFunction: HashFunction) {
            val checksum = createChecksumFile(content, hashFunction)
            putResource(destination.append("." + hashFunction.getAlgorithm().lowercase().replace("-".toRegex(), "")), ByteArrayReadableContent(checksum))
        }

        fun createChecksumFile(src: File, hashFunction: HashFunction): ByteArray {
            val hash: HashCode?
            try {
                hash = hashFunction.hashFile(src)
            } catch (e: IOException) {
                throw throwAsUncheckedException(e)
            }
            val formattedHashString = hash.toZeroPaddedString(hashFunction.getHexDigits())
            return formattedHashString.toByteArray(StandardCharsets.US_ASCII)
        }

        fun putResource(externalResource: ExternalResourceName, readableContent: ReadableContent) {
            networkOperationCaller.withBackoffAndRetry(object : Callable<Void> {
                override fun call(): Void {
                    repository.resource(externalResource)!!.put(readableContent)
                    return null
                }

                override fun toString(): String {
                    return "PUT " + externalResource.getDisplayName()
                }
            })
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(MavenPublisher::class.java)

        private const val POM_FILE_ENCODING = "UTF-8"
        private const val SNAPSHOT_VERSION = "SNAPSHOT"
        private val VERSION_FILE_PATTERN: Pattern = Pattern.compile("^(.*)-([0-9]{8}.[0-9]{6})-([0-9]+)$")
        private fun publishArtifactsAndMetadata(publication: MavenNormalizedPublication, artifactPublisher: ModuleArtifactPublisher) {
            if (publication.getMainArtifact() != null) {
                artifactPublisher.publish(null, publication.getMainArtifact().getExtension(), publication.getMainArtifact().file)
            }
            artifactPublisher.publish(null, "pom", publication.getPomArtifact().file)
            for (artifact in publication.getAdditionalArtifacts()) {
                artifactPublisher.publish(artifact.getClassifier(), artifact.getExtension(), artifact.file)
            }
        }
    }
}
