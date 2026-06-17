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
package org.gradle.api.publish.maven.internal.publisher

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.repositories.AbstractArtifactRepository.getName
import org.gradle.api.publish.internal.PublicationArtifactInternal
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import java.util.stream.Collectors

class MavenNormalizedPublication(
    val name: String?,
    projectIdentity: MavenPublicationCoordinates,
    val packaging: String?,
    val pomArtifact: MavenArtifact?,
    private val mainArtifact: MavenArtifact?,
    val allArtifacts: MutableSet<MavenArtifact?>
) {
    val projectIdentity: ModuleComponentIdentifier

    init {
        this.projectIdentity = newId(DefaultModuleIdentifier.newId(projectIdentity.getGroupId().get(), projectIdentity.getArtifactId().get()), projectIdentity.getVersion().get())
    }

    val groupId: String
        get() = projectIdentity.getGroup()

    val artifactId: String
        get() = projectIdentity.getModule()

    val version: String
        get() = projectIdentity.getVersion()

    fun getMainArtifact(): MavenArtifact? {
        check(!(mainArtifact != null && !(mainArtifact as PublicationArtifactInternal).shouldBePublished())) { "Artifact " + mainArtifact!!.file.getName() + " wasn't produced by this build." }
        return mainArtifact
    }

    val additionalArtifacts: MutableSet<MavenArtifact?>
        get() = allArtifacts.stream()
            .filter { artifact: MavenArtifact? -> artifact !== pomArtifact && artifact !== mainArtifact }
            .collect(Collectors.toSet())
}
