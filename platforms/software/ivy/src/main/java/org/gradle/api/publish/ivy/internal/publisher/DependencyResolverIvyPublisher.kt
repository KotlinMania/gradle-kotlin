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
package org.gradle.api.publish.ivy.internal.publisher

import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResolver
import org.gradle.api.internal.artifacts.repositories.transport.NetworkOperationBackOffAndRetry
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import java.util.concurrent.Callable

class DependencyResolverIvyPublisher : IvyPublisher {
    private val networkOperationBackOffAndRetry = NetworkOperationBackOffAndRetry<Void?>()

    override fun publish(publication: IvyNormalizedPublication, repository: IvyArtifactRepository) {
        val publisher = (repository as DefaultIvyArtifactRepository).createPublisher()
        val moduleVersionIdentifier = newId(publication.getCoordinates())

        for (artifact in publication.getAllArtifacts()) {
            val artifactMetadata: ModuleComponentArtifactMetadata = DefaultModuleComponentArtifactMetadata(moduleVersionIdentifier, createIvyArtifact(artifact))
            publish(publisher, artifact, artifactMetadata)
        }
    }

    private fun publish(publisher: IvyResolver, artifact: IvyArtifact, artifactMetadata: ModuleComponentArtifactMetadata) {
        networkOperationBackOffAndRetry.withBackoffAndRetry(object : Callable<Void?> {
            override fun call(): Void? {
                publisher.publish(artifactMetadata, artifact.file)
                return null
            }

            override fun toString(): String {
                return "Publish " + artifactMetadata
            }
        })
    }

    private fun createIvyArtifact(artifact: IvyArtifact): IvyArtifactName {
        return DefaultIvyArtifactName(artifact.getName(), artifact.getType(), artifact.getExtension(), artifact.getClassifier())
    }
}
