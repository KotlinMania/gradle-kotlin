/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.internal.component.local.model

import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.DefaultIvyArtifactName.Companion.forPublishArtifact
import org.gradle.internal.component.model.IvyArtifactName
import java.io.File

class PublishArtifactLocalArtifactMetadata(componentIdentifier: ComponentIdentifier, publishArtifact: PublishArtifact) : LocalComponentArtifactMetadata, ComponentArtifactIdentifier {
    private val componentIdentifier: ComponentIdentifier
    val publishArtifact: PublishArtifact
    private val ivyArtifactName: DefaultIvyArtifactName

    init {
        this.componentIdentifier = componentIdentifier
        this.publishArtifact = publishArtifact
        // In case the publish artifact is backed by an ArchiveTask, this causes the task to be realized.
        // However, if we are at this point, we need the realized task to determine the archive extension/type later
        // to set the 'artifactType' attribute required in matching (even if the variant with the artifact is not selected in the end).
        ivyArtifactName = forPublishArtifact(publishArtifact)
    }

    override fun getDisplayName(): String {
        return publishArtifact.getFile().getName() + " (" + componentIdentifier.getDisplayName() + ")"
    }

    override fun getCapitalizedDisplayName(): String {
        return getDisplayName()
    }

    override fun toString(): String {
        return getDisplayName()
    }

    override fun getComponentIdentifier(): ComponentIdentifier {
        return componentIdentifier
    }

    override fun getFile(): File {
        return publishArtifact.getFile()
    }

    override fun getId(): ComponentArtifactIdentifier {
        return this
    }

    override fun getComponentId(): ComponentIdentifier {
        return componentIdentifier
    }

    override fun getName(): IvyArtifactName {
        return ivyArtifactName
    }

    override fun hashCode(): Int {
        return componentIdentifier.hashCode() xor publishArtifact.hashCode()
    }

    override fun equals(obj: Any): Boolean {
        if (obj === this) {
            return true
        }
        if (obj == null || obj.javaClass != javaClass) {
            return false
        }
        val other = obj as PublishArtifactLocalArtifactMetadata
        return other.componentIdentifier == componentIdentifier && other.publishArtifact == publishArtifact
    }

    override fun getBuildDependencies(): TaskDependency {
        return publishArtifact.getBuildDependencies()
    }
}
