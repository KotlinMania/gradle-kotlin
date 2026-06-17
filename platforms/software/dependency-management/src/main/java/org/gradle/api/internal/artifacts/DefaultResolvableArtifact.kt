/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.artifacts

import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.internal.tasks.WorkNodeAction
import org.gradle.internal.component.local.model.TransformedComponentFileArtifactIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName.Companion.forFile
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.model.CalculatedValueFactory
import java.io.File

class DefaultResolvableArtifact(
    private val owner: ModuleVersionIdentifier?,
    private val artifact: IvyArtifactName,
    private val artifactId: ComponentArtifactIdentifier,
    private val buildDependencies: TaskDependencyContainer?,
    private val fileSource: CalculatedValue<File>,
    calculatedValueFactory: CalculatedValueFactory
) : ResolvableArtifact {
    private val resolvedArtifactDependency: WorkNodeAction?
    private val calculatedValueFactory: CalculatedValueFactory
    private val publicView: ResolvedArtifact

    init {
        if (this.isProjectArtifact) {
            // Use a node to eagerly calculate the file if this artifact will be used as a dependency of some other node
            // This is to avoid having to lock the producing project when a consuming task in another project runs
            this.resolvedArtifactDependency = ResolveAction(this)
        } else {
            this.resolvedArtifactDependency = null
        }
        this.calculatedValueFactory = calculatedValueFactory
        this.publicView = DefaultResolvedArtifact(artifactId, fileSource, owner, artifact)
    }

    override fun visitDependencies(context: TaskDependencyResolveContext) {
        context.add(buildDependencies)
        if (resolvedArtifactDependency != null) {
            context.add(resolvedArtifactDependency)
        }
    }

    override fun getArtifactName(): IvyArtifactName {
        return artifact
    }

    override fun getId(): ComponentArtifactIdentifier {
        return artifactId
    }

    override fun equals(obj: Any?): Boolean {
        if (obj === this) {
            return true
        }
        if (obj == null || obj.javaClass != javaClass) {
            return false
        }
        val other = obj as DefaultResolvableArtifact
        return other.artifactId == artifactId
    }

    override fun hashCode(): Int {
        return artifactId.hashCode()
    }

    override fun toPublicView(): ResolvedArtifact {
        return publicView
    }

    override fun transformedTo(file: File): ResolvableArtifact {
        val artifactName: IvyArtifactName = forFile(file, artifact.classifier)

        val originalFileName: String?
        if (artifactId is TransformedComponentFileArtifactIdentifier) {
            originalFileName = artifactId.getOriginalFileName()
        } else {
            originalFileName = fileSource.get().getName()
        }

        val newId: ComponentArtifactIdentifier = TransformedComponentFileArtifactIdentifier(artifactId.getComponentIdentifier(), file.getName(), originalFileName)
        return PreResolvedResolvableArtifact(owner, artifactName, newId, file, TaskDependencyContainer.EMPTY, calculatedValueFactory)
    }

    override fun isResolveSynchronously(): Boolean {
        if (this.isProjectArtifact) {
            // Don't bother resolving local components asynchronously
            return true
        }
        return fileSource.isFinalized()
    }

    private val isProjectArtifact: Boolean
        get() = artifactId.getComponentIdentifier() is ProjectComponentIdentifier

    override fun getFileSource(): CalculatedValue<File> {
        return fileSource
    }

    override fun getFile(): File {
        fileSource.finalizeIfNotAlready()
        return fileSource.get()
    }

    class ResolveAction(val artifact: DefaultResolvableArtifact) : WorkNodeAction {
        override fun toString(): String {
            return "resolve " + artifact.artifactId
        }

        override fun usesMutableProjectState(): Boolean {
            return true
        }

        override fun getOwningProject(): Project? {
            if (artifact.fileSource.getResourceToLock() is ProjectState) {
                return (artifact.fileSource.getResourceToLock() as ProjectState).getMutableModel()
            } else {
                return null
            }
        }

        override fun visitDependencies(context: TaskDependencyResolveContext) {
            context.add(artifact.buildDependencies)
        }

        override fun run(context: NodeExecutionContext?) {
            artifact.fileSource.finalizeIfNotAlready()
        }
    }
}
