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
package org.gradle.api.internal.artifacts

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.internal.Describables
import org.gradle.internal.component.local.model.TransformedComponentFileArtifactIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName.Companion.forFile
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.model.CalculatedValueFactory
import java.io.File

class PreResolvedResolvableArtifact(
    private val owner: ModuleVersionIdentifier?,
    private val artifact: IvyArtifactName,
    private val artifactId: ComponentArtifactIdentifier,
    private val file: File,
    private val builtBy: TaskDependencyContainer,
    private val calculatedValueFactory: CalculatedValueFactory
) : ResolvableArtifact {
    private val fileSource: CalculatedValue<File>
    private val publicView: DefaultResolvedArtifact

    init {
        this.fileSource = calculatedValueFactory.create<File>(Describables.of(artifactId), file)
        this.publicView = DefaultResolvedArtifact(artifactId, fileSource, owner, artifact)
    }

    override fun hashCode(): Int {
        return artifactId.hashCode()
    }

    override fun equals(obj: Any): Boolean {
        if (obj === this) {
            return true
        }
        if (obj == null || obj.javaClass != javaClass) {
            return false
        }
        val other = obj as PreResolvedResolvableArtifact
        return other.artifactId == artifactId
    }

    override fun getId(): ComponentArtifactIdentifier {
        return artifactId
    }

    override fun getFileSource(): CalculatedValue<File> {
        return fileSource
    }

    override fun getFile(): File {
        return file
    }

    override fun toPublicView(): ResolvedArtifact {
        return publicView
    }

    override fun visitDependencies(context: TaskDependencyResolveContext) {
        builtBy.visitDependencies(context)
    }

    override fun isResolveSynchronously(): Boolean {
        return true
    }

    override fun transformedTo(file: File): ResolvableArtifact {
        val artifactName: IvyArtifactName = forFile(file, artifact.classifier)

        val originalFileName: String?
        if (artifactId is TransformedComponentFileArtifactIdentifier) {
            originalFileName = artifactId.getOriginalFileName()
        } else {
            originalFileName = this.file.getName()
        }

        val newId: ComponentArtifactIdentifier = TransformedComponentFileArtifactIdentifier(artifactId.getComponentIdentifier(), file.getName(), originalFileName)
        return PreResolvedResolvableArtifact(owner, artifactName, newId, file, builtBy, calculatedValueFactory)
    }

    override fun getArtifactName(): IvyArtifactName {
        return artifact
    }
}
