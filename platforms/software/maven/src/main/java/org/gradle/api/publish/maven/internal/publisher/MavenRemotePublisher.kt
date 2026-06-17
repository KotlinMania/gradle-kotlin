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
import org.apache.maven.artifact.repository.metadata.Snapshot
import org.apache.maven.artifact.repository.metadata.SnapshotVersion
import org.apache.maven.artifact.repository.metadata.Versioning
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository
import org.gradle.internal.Factory
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceReadResult
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.util.internal.BuildCommencedTimeProvider
import org.jspecify.annotations.NullMarked
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

@NullMarked
class MavenRemotePublisher(temporaryDirFactory: Factory<File?>, private val timeProvider: BuildCommencedTimeProvider) : AbstractMavenPublisher(temporaryDirFactory) {
    override fun publish(publication: MavenNormalizedPublication, artifactRepository: MavenArtifactRepository?) {
        checkNotNull(artifactRepository)
        val repositoryUrl = artifactRepository.getUrl()
        LOGGER.info("Publishing to repository '{}' ({})", artifactRepository.getName(), repositoryUrl)

        val protocol = repositoryUrl.getScheme().lowercase()
        val realRepository = artifactRepository as DefaultMavenArtifactRepository
        val transport = realRepository.getTransport(protocol)
        val repository: ExternalResourceRepository = transport.repository

        publish(publication, repository, repositoryUrl, false)
    }

    override fun createSnapshotMetadata(
        publication: MavenNormalizedPublication,
        groupId: String,
        artifactId: String,
        version: String,
        repository: ExternalResourceRepository,
        metadataResource: ExternalResourceName
    ): Metadata {
        val metadata = Metadata()
        metadata.setModelVersion("1.1.0")
        metadata.setGroupId(groupId)
        metadata.setArtifactId(artifactId)
        metadata.setVersion(version)

        val timestamp = createSnapshotTimestamp()

        val snapshot = Snapshot()
        snapshot.setBuildNumber(getNextBuildNumber(repository, metadataResource))
        snapshot.setTimestamp(timestamp)

        val versioning = Versioning()
        versioning.setSnapshot(snapshot)
        versioning.setLastUpdated(snapshot.getTimestamp().replace(".", ""))

        val timestampVersion = version.replace("SNAPSHOT", snapshot.getTimestamp() + "-" + snapshot.getBuildNumber())
        for (artifact in publication.getAllArtifacts()) {
            val sv = SnapshotVersion()
            sv.setClassifier(artifact.getClassifier())
            sv.setExtension(artifact.getExtension())
            sv.setVersion(timestampVersion)
            sv.setUpdated(versioning.getLastUpdated())

            versioning.getSnapshotVersions().add(sv)
        }

        metadata.setVersioning(versioning)

        return metadata
    }

    private fun createSnapshotTimestamp(): String {
        val utcDateFormatter: DateFormat = SimpleDateFormat("yyyyMMdd.HHmmss")
        utcDateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"))
        val currentTime = Date(timeProvider.getCurrentTime())
        return utcDateFormatter.format(currentTime)
    }

    private fun getNextBuildNumber(repository: ExternalResourceRepository, metadataResource: ExternalResourceName): Int {
        val existing: ExternalResourceReadResult<Metadata?>? = readExistingMetadata(repository, metadataResource)

        if (existing != null) {
            val recessive = existing.result
            if (recessive != null) {
                val versioning = recessive.getVersioning()
                if (versioning != null) {
                    val snapshot = versioning.getSnapshot()
                    if (snapshot != null && snapshot.getBuildNumber() > 0) {
                        return snapshot.getBuildNumber() + 1
                    }
                }
            }
        }
        return 1
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(MavenRemotePublisher::class.java)
    }
}
