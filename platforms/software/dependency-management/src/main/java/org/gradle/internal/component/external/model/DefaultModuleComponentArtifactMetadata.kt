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
package org.gradle.internal.component.external.model

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.IvyArtifactName
import java.util.Optional

open class DefaultModuleComponentArtifactMetadata @JvmOverloads constructor(
    moduleComponentArtifactIdentifier: ModuleComponentArtifactIdentifier?,
    private val alternativeArtifact: ComponentArtifactMetadata? = null
) : ModuleComponentArtifactMetadata {
    private val id: DefaultModuleComponentArtifactIdentifier

    @JvmOverloads
    constructor(componentIdentifier: ModuleComponentIdentifier, artifact: IvyArtifactName?, alternativeArtifact: ComponentArtifactMetadata? = null) : this(
        DefaultModuleComponentArtifactIdentifier(
            componentIdentifier,
            artifact
        ), alternativeArtifact
    )

    init {
        this.id = moduleComponentArtifactIdentifier as DefaultModuleComponentArtifactIdentifier
    }

    override fun toString(): String {
        return id.toString()
    }

    override fun getId(): ModuleComponentArtifactIdentifier {
        return id
    }

    override fun getComponentId(): ComponentIdentifier? {
        return id.getComponentIdentifier()
    }

    override fun getName(): IvyArtifactName? {
        return id.getName()
    }

    override fun getBuildDependencies(): TaskDependency? {
        return TaskDependencyInternal.EMPTY
    }

    override fun getAlternativeArtifact(): Optional<ComponentArtifactMetadata?> {
        return Optional.ofNullable<ComponentArtifactMetadata?>(alternativeArtifact)
    }
}
