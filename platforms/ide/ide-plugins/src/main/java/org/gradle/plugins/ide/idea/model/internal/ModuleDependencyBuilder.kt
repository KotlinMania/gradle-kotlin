/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.idea.model.internal

import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.plugins.ide.idea.internal.IdeaModuleMetadata
import org.gradle.plugins.ide.idea.model.ModuleDependency
import org.gradle.plugins.ide.internal.IdeArtifactRegistry

internal class ModuleDependencyBuilder(private val ideArtifactRegistry: IdeArtifactRegistry) {
    fun create(id: ProjectComponentIdentifier, scope: String?): ModuleDependency {
        return ModuleDependency(determineProjectName(id)!!, scope)
    }

    private fun determineProjectName(id: ProjectComponentIdentifier): String? {
        val moduleMetadata = ideArtifactRegistry.getIdeProject<IdeaModuleMetadata?>(IdeaModuleMetadata::class.java, id)
        return if (moduleMetadata == null) id.getProjectName() else moduleMetadata.getName()
    }
}
