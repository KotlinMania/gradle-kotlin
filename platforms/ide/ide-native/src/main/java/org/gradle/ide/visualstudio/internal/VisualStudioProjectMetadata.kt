/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.ide.visualstudio.internal

import org.gradle.api.internal.tasks.TaskDependencyUtil
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.plugins.ide.internal.IdeProjectMetadata
import org.gradle.util.internal.CollectionUtils.collect
import java.util.function.Function

class VisualStudioProjectMetadata(private val project: DefaultVisualStudioProject) : IdeProjectMetadata {
    override fun getDisplayName(): DisplayName {
        return Describables.withTypeAndName("Visual Studio project", project.getName())
    }

    val name: String?
        get() = project.getName()

    val file: File
        get() = project.getProjectFile().getLocation()

    val generatorTasks: MutableSet<out Task?>?
        get() = TaskDependencyUtil.getDependenciesForInternalUse(project)

    val configurations: MutableList<VisualStudioProjectConfigurationMetadata?>
        get() = collect<VisualStudioProjectConfigurationMetadata?, VisualStudioProjectConfiguration?>(
            project.getConfigurations(),
            Function { configuration: VisualStudioProjectConfiguration? ->
                VisualStudioProjectConfigurationMetadata(
                    configuration!!.getName(),
                    configuration.isBuildable()
                )
            }
        )
}
