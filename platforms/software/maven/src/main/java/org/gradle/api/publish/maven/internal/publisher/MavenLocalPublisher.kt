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
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.internal.Factory
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceRepository
import org.jspecify.annotations.NullMarked
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI

@NullMarked
class MavenLocalPublisher(temporaryDirFactory: Factory<File?>, private val repositoryTransportFactory: RepositoryTransportFactory, private val mavenRepositoryLocator: LocalMavenRepositoryLocator) :
    AbstractMavenPublisher(temporaryDirFactory) {
    override fun publish(publication: MavenNormalizedPublication, artifactRepository: MavenArtifactRepository?) {
        LOGGER.info("Publishing to maven local repository")

        val rootUri: URI = mavenRepositoryLocator.localMavenRepository.toURI()
        val transport = repositoryTransportFactory.createFileTransport("mavenLocal")
        val repository: ExternalResourceRepository = transport.repository

        publish(publication, repository, rootUri, true)
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

        val snapshot = Snapshot()
        snapshot.setLocalCopy(true)
        val versioning = Versioning()
        versioning.updateTimestamp()
        versioning.setSnapshot(snapshot)

        for (artifact in publication.getAllArtifacts()) {
            val sv = SnapshotVersion()
            sv.setClassifier(artifact.getClassifier())
            sv.setExtension(artifact.getExtension())
            sv.setVersion(version)
            sv.setUpdated(versioning.getLastUpdated())

            versioning.getSnapshotVersions().add(sv)
        }

        metadata.setVersioning(versioning)

        return metadata
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(MavenLocalPublisher::class.java)
    }
}
