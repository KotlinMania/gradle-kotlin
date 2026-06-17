/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.component.external.model

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.dsl.ArtifactFile
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName

/**
 * An artifact located relative to some module.
 */
class UrlBackedArtifactMetadata(private val componentIdentifier: ModuleComponentIdentifier, val fileName: String, val relativeUrl: String) : ModuleComponentArtifactMetadata {
    private val id: ModuleComponentArtifactIdentifier

    private var ivyArtifactName: IvyArtifactName? = null

    init {
        id = createArtifactId(componentIdentifier, fileName)
    }

    private fun createArtifactId(componentIdentifier: ModuleComponentIdentifier, fileName: String): ModuleComponentArtifactIdentifier {
        if (componentIdentifier is MavenUniqueSnapshotComponentIdentifier) {
            // This special case is for Maven snapshots with Gradle Module Metadata when we need to remap the file name, which
            // corresponds to the unique timestamp, to the SNAPSHOT version, for backwards compatibility
            return DefaultModuleComponentArtifactIdentifier(
                componentIdentifier,
                getName()
            )
        }
        return ModuleComponentFileArtifactIdentifier(componentIdentifier, fileName)
    }

    override fun getComponentId(): ModuleComponentIdentifier {
        return componentIdentifier
    }

    override fun getId(): ModuleComponentArtifactIdentifier {
        return id
    }

    override fun getName(): IvyArtifactName {
        if (ivyArtifactName == null) {
            val names = ArtifactFile(relativeUrl, uniqueVersion())
            ivyArtifactName = DefaultIvyArtifactName(names.name, names.getExtension(), names.getExtension(), names.getClassifier())
        }
        return ivyArtifactName!!
    }

    override fun getBuildDependencies(): TaskDependency {
        return TaskDependencyInternal.EMPTY
    }

    private fun uniqueVersion(): String {
        if (componentIdentifier is MavenUniqueSnapshotComponentIdentifier) {
            return componentIdentifier.getTimestampedVersion()
        }
        return componentIdentifier.getVersion()
    }
}
