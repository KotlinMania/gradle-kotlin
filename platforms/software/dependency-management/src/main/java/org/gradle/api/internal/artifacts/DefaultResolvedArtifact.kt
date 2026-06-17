/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.DefaultResolvedModuleVersion
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.model.CalculatedValue
import java.io.File

/**
 * Default implementation of [ResolvedArtifact], the artifact type used by the legacy
 * [org.gradle.api.artifacts.ResolvedConfiguration] API. This class presents a file extension, type,
 * classifier via its [IvyArtifactName]. This name is tracked on a best-effort basis, and may not
 * always represent the actual file name.
 */
class DefaultResolvedArtifact(
    private val id: ComponentArtifactIdentifier,
    private val fileSource: CalculatedValue<File>,
    private val owner: ModuleVersionIdentifier?,
    private val artifactName: IvyArtifactName
) : ResolvedArtifact {
    override fun getFile(): File {
        fileSource.finalizeIfNotAlready()
        return fileSource.get()
    }

    override fun getId(): ComponentArtifactIdentifier {
        return id
    }

    override fun toString(): String {
        return id.getDisplayName()
    }

    override fun getModuleVersion(): ResolvedModuleVersion {
        if (owner == null) {
            // Local file dependencies do not have an owner
            throw UnsupportedOperationException()
        }
        return DefaultResolvedModuleVersion(owner)
    }

    override fun getName(): String {
        return artifactName.name!!
    }

    override fun getType(): String {
        return artifactName.type!!
    }

    override fun getExtension(): String? {
        return artifactName.extension
    }

    override fun getClassifier(): String? {
        return artifactName.classifier
    }
}
