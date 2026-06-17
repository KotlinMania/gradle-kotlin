/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskDependency
import org.gradle.ide.visualstudio.TextConfigFile
import org.gradle.ide.visualstudio.TextProvider
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.serialization.Cached.get
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.util.internal.CollectionUtils
import java.io.File
import java.util.concurrent.Callable
import java.util.function.Function
import javax.inject.Inject

class DefaultVisualStudioSolution @Inject constructor(
    private val name: String?,
    objectFactory: ObjectFactory,
    private val ideArtifactRegistry: IdeArtifactRegistry,
    providers: ProviderFactory,
    projectLayout: ProjectLayout
) : VisualStudioSolutionInternal {
    private val solutionFile: SolutionFile
    private val buildDependencies = DefaultTaskDependency()
    val location: Provider<RegularFile?>

    init {
        this.solutionFile = objectFactory.newInstance<SolutionFile>(SolutionFile::class.java, getName() + ".sln")
        this.location = projectLayout.file(providers.provider<File?>(object : Callable<File?> {
            override fun call(): File? {
                return solutionFile.getLocation()
            }
        }))
        builtBy(ideArtifactRegistry.getIdeProjectFiles(VisualStudioProjectMetadata::class.java))
    }

    override fun getDisplayName(): String {
        return "Visual Studio solution"
    }

    override fun getName(): String? {
        return name
    }

    override fun getSolutionFile(): SolutionFile {
        return solutionFile
    }

    override fun getProjects(): MutableList<VisualStudioProjectMetadata?> {
        return CollectionUtils.collect<VisualStudioProjectMetadata?, IdeArtifactRegistry.Reference<VisualStudioProjectMetadata?>?>(
            ideArtifactRegistry.getIdeProjects<VisualStudioProjectMetadata?>(VisualStudioProjectMetadata::class.java),
            Function { IdeArtifactRegistry.Reference.get() }
        )
    }

    override fun builtBy(vararg tasks: Any?) {
        buildDependencies.add(*tasks)
    }

    override fun getBuildDependencies(): TaskDependency {
        return buildDependencies
    }

    class SolutionFile @Inject constructor(private val fileResolver: PathToFileResolver, defaultLocation: String?) : TextConfigFile {
        @get:Nested
        val textActions: MutableList<Action<in TextProvider?>?> = ArrayList<Action<in TextProvider?>?>()
        private var location: Any?

        init {
            this.location = defaultLocation
        }

        @Internal
        override fun getLocation(): File? {
            return fileResolver.resolve(location)
        }

        override fun setLocation(location: Any?) {
            this.location = location
        }

        override fun withContent(action: Action<in TextProvider?>?) {
            textActions.add(action)
        }
    }
}
