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
package org.gradle.internal.component.local.model

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.component.model.IvyArtifactName
import java.io.File

/**
 * Represents an unknown local artifact, referenced from a dependency definition.
 */
class MissingLocalArtifactMetadata(componentIdentifier: ComponentIdentifier, artifactName: IvyArtifactName) : LocalComponentArtifactMetadata, ComponentArtifactIdentifier {
    private val componentIdentifier: ComponentIdentifier
    private val name: IvyArtifactName

    init {
        this.componentIdentifier = componentIdentifier
        this.name = artifactName
    }

    override fun getDisplayName(): String {
        return name.displayName + " (" + componentIdentifier.getDisplayName() + ")"
    }

    override fun getCapitalizedDisplayName(): String {
        return getDisplayName()
    }

    override fun getFile(): File {
        return null
    }

    override fun getName(): IvyArtifactName {
        return name
    }

    override fun getComponentIdentifier(): ComponentIdentifier {
        return componentIdentifier
    }

    override fun getId(): ComponentArtifactIdentifier {
        return this
    }

    override fun getComponentId(): ComponentIdentifier {
        return componentIdentifier
    }

    override fun toString(): String {
        return getDisplayName()
    }

    override fun getBuildDependencies(): TaskDependency {
        return TaskDependencyInternal.EMPTY
    }

    override fun hashCode(): Int {
        return componentIdentifier.hashCode() xor name.hashCode()
    }

    override fun equals(obj: Any): Boolean {
        if (obj === this) {
            return true
        }
        if (obj == null || obj.javaClass != javaClass) {
            return false
        }
        val other = obj as MissingLocalArtifactMetadata
        return other.componentIdentifier == componentIdentifier && other.name == name
    }
}
