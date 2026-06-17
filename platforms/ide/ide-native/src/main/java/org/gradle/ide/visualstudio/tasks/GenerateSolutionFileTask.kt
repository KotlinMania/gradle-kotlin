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
package org.gradle.ide.visualstudio.tasks

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.ide.visualstudio.TextProvider
import org.gradle.ide.visualstudio.VisualStudioSolution
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioSolution
import org.gradle.ide.visualstudio.tasks.internal.VisualStudioSolutionFile
import org.gradle.internal.serialization.Cached
import org.gradle.plugins.ide.api.GeneratorTask
import org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObjectGenerator
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

/**
 * Task for generating a Visual Studio solution file (e.g. `foo.sln`).
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class GenerateSolutionFileTask @Inject constructor(solution: DefaultVisualStudioSolution?) : GeneratorTask<VisualStudioSolutionFile?>() {
    @Transient
    private var solution: DefaultVisualStudioSolution? = null
    private val outputFile = getProject().provider<File?>(SerializableLambdas.callable<File?>(SerializableLambdas.SerializableCallable { solution!!.getSolutionFile().getLocation() }))
    private val spec = Cached.of({ this.calculateSpec() })

    init {
        generator = GenerateSolutionFileTask.ConfigurationObjectGenerator()
        setVisualStudioSolution(solution)
    }

    val incremental: Boolean
        get() = true

    fun setVisualStudioSolution(solution: VisualStudioSolution?) {
        this.solution = solution as DefaultVisualStudioSolution
    }

    @Internal
    fun getSolution(): VisualStudioSolution {
        return solution!!
    }

    /**
     * The [SolutionSpec] for this task.
     *
     * @since 8.11
     */
    @Nested
    @Incubating
    protected fun getSpec(): SolutionSpec? {
        return spec.get()
    }

    @get:Internal
    val inputFile: File?
        get() = null

    @OutputFile
    public override fun getOutputFile(): File? {
        return outputFile.get()
    }

    private fun calculateSpec(): SolutionSpec {
        val solution = getSolution() as DefaultVisualStudioSolution
        val projects: MutableList<VisualStudioSolutionFile.ProjectSpec?> = ArrayList<VisualStudioSolutionFile.ProjectSpec?>()
        for (project in solution.getProjects()) {
            val configurations: MutableList<VisualStudioSolutionFile.ConfigurationSpec?> = ArrayList<VisualStudioSolutionFile.ConfigurationSpec?>()
            for (configuration in project.getConfigurations()) {
                configurations.add(VisualStudioSolutionFile.ConfigurationSpec(configuration.getName(), configuration.isBuildable()))
            }
            projects.add(VisualStudioSolutionFile.ProjectSpec(project.getName(), project.file!!, configurations))
        }
        return GenerateSolutionFileTask.SolutionSpec(projects, solution.getSolutionFile().getTextActions())
    }

    private inner class ConfigurationObjectGenerator : PersistableConfigurationObjectGenerator<VisualStudioSolutionFile?>() {
        override fun create(): VisualStudioSolutionFile {
            return VisualStudioSolutionFile()
        }

        override fun configure(solutionFile: VisualStudioSolutionFile) {
            val spec = this@GenerateSolutionFileTask.spec.get()

            solutionFile.setProjects(spec!!.projects)

            for (textAction in spec.textActions) {
                solutionFile.getActions().add(textAction!!)
            }
        }
    }

    /**
     * The data to use to generate the solution file.
     *
     * @since 8.11
     */
    @Incubating
    protected class SolutionSpec private constructor(
        /**
         * Projects to include in the solution.
         *
         * @since 8.11
         */
        @get:Nested @get:Incubating val projects: MutableList<VisualStudioSolutionFile.ProjectSpec?>,
        /**
         * Additional text generation actions.
         *
         * @since 8.11
         */
        @get:Nested @get:Incubating val textActions: MutableList<Action<in TextProvider?>?>
    )
}
