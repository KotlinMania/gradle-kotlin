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
import org.gradle.api.Transformer
import org.gradle.api.XmlProvider
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.ide.visualstudio.VisualStudioProject
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioProject
import org.gradle.ide.visualstudio.internal.VisualStudioProjectConfiguration
import org.gradle.ide.visualstudio.tasks.internal.RelativeFileNameTransformer
import org.gradle.ide.visualstudio.tasks.internal.VisualStudioProjectFile
import org.gradle.internal.serialization.Cached
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.internal.IdePlugin.Companion.toGradleCommand
import org.gradle.util.internal.CollectionUtils.collect
import org.gradle.util.internal.VersionNumber
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * Task for generating a Visual Studio project file (e.g. `foo.vcxproj`).
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class GenerateProjectFileTask @Inject constructor(visualStudioProject: DefaultVisualStudioProject?) : XmlGeneratorTask<VisualStudioProjectFile?>() {
    @Transient
    private var visualStudioProject: DefaultVisualStudioProject? = null
    private val spec = Cached.of({ this.calculateSpec() })
    private val outputFile = getProject().provider<File?>(SerializableLambdas.callable<File?>(SerializableLambdas.SerializableCallable { visualStudioProject!!.getProjectFile().getLocation() }))
    private val transformer = Cached.of({ this.getTransformer() })

    @get:Internal
    var gradleExe: String? = null

    @get:Internal
    var gradleArgs: String? = null

    init {
        setVisualStudioProject(visualStudioProject)
    }

    val incremental: Boolean
        get() = true

    fun initGradleCommand() {
        val gradlew = File(toGradleCommand(getProject()))
        getConventionMapping().map("gradleExe", object : Callable<Any?> {
            override fun call(): Any {
                val rootDir: String? = transformer.get()!!.transform(getProject().getRootDir())
                var args = ""
                if (rootDir != ".") {
                    args = " -p \"" + rootDir + "\""
                }

                if (gradlew.isFile()) {
                    return "\"" + transformer.get()!!.transform(gradlew) + "\"" + args
                }

                return "\"gradle\"" + args
            }
        })
    }

    @Internal
    fun getTransformer(): Transformer<String, File?> {
        return RelativeFileNameTransformer.Companion.forFile(getProject().getRootDir(), visualStudioProject!!.getProjectFile().getLocation())
    }

    fun setVisualStudioProject(vsProject: VisualStudioProject?) {
        this.visualStudioProject = vsProject as DefaultVisualStudioProject
    }

    @Internal
    fun getVisualStudioProject(): VisualStudioProject {
        return visualStudioProject!!
    }

    /**
     * Returns the [ProjectSpec] for this task.
     *
     * @since 8.11
     */
    @Nested
    @Incubating
    protected fun getSpec(): ProjectSpec? {
        return spec.get()
    }

    @get:Internal
    val inputFile: File?
        get() = null

    @OutputFile
    public override fun getOutputFile(): File? {
        return outputFile.get()
    }

    override fun create(): VisualStudioProjectFile? {
        return VisualStudioProjectFile(xmlTransformer, transformer.get())
    }

    override fun configure(projectFile: VisualStudioProjectFile) {
        val spec = this.spec.get()

        projectFile.setGradleCommand(spec!!.gradleCommand)
        projectFile.setProjectUuid(DefaultVisualStudioProject.Companion.getUUID(outputFile.get()))
        projectFile.setVisualStudioVersion(spec.visualStudioVersion.get()!!)
        projectFile.setSdkVersion(spec.sdkVersion.get()!!)

        for (sourceFile in spec.sourceFiles) {
            projectFile.addSourceFile(sourceFile)
        }

        for (resourceFile in spec.resourceFiles) {
            projectFile.addResource(resourceFile)
        }

        for (headerFile in spec.headerFiles) {
            projectFile.addHeaderFile(headerFile)
        }

        if (spec.warning != null) {
            getLogger().warn(spec.warning)
        }
        for (configuration in spec.configurations) {
            projectFile.addConfiguration(configuration)
        }

        for (xmlAction in spec.xmlActions) {
            xmlTransformer.addAction(xmlAction)
        }
    }

    private fun buildGradleCommand(): String? {
        val exe = this.gradleExe
        val args = this.gradleArgs
        if (args == null || args.trim { it <= ' ' }.length == 0) {
            return exe
        } else {
            return exe + " " + args.trim { it <= ' ' }
        }
    }

    private fun calculateSpec(): ProjectSpec {
        val warning: String?
        if (visualStudioProject!!.getConfigurations().stream().noneMatch { it: VisualStudioProjectConfiguration? -> it!!.isBuildable() }) {
            warning = "'" + visualStudioProject!!.getComponentName() + "' component in project '" + getProject().getPath() + "' is not buildable."
        } else {
            warning = null
        }

        val configurations: MutableList<VisualStudioProjectFile.ConfigurationSpec> = ArrayList<VisualStudioProjectFile.ConfigurationSpec>()
        for (configuration in visualStudioProject!!.getConfigurations()) {
            val targetBinary = configuration.getTargetBinary()
            if (targetBinary != null) {
                configurations.add(
                    VisualStudioProjectFile.ConfigurationSpec(
                        configuration.getName(),
                        configuration.getConfigurationName(),
                        configuration.getProject().getName(),
                        configuration.getPlatformName(),
                        configuration.getType(),
                        configuration.isBuildable(),
                        targetBinary.isDebuggable(),
                        targetBinary.getIncludePaths(),
                        targetBinary.getBuildTaskPath(),
                        targetBinary.getCleanTaskPath(),
                        targetBinary.getCompilerDefines(),
                        targetBinary.getOutputFile(),
                        targetBinary.getLanguageStandard()
                    )
                )
            } else {
                configurations.add(
                    VisualStudioProjectFile.ConfigurationSpec(
                        configuration.getName(),
                        configuration.getConfigurationName(),
                        configuration.getProject().getName(),
                        configuration.getPlatformName(),
                        configuration.getType(),
                        configuration.isBuildable(),
                        false,
                        mutableSetOf<File?>(),
                        null,
                        null,
                        mutableListOf<String?>(),
                        null,
                        null
                    )
                )
            }
        }

        return GenerateProjectFileTask.ProjectSpec(
            visualStudioProject!!.getVisualStudioVersion(),
            visualStudioProject!!.getSdkVersion(),
            visualStudioProject!!.getSourceFiles(),
            visualStudioProject!!.getResourceFiles(),
            visualStudioProject!!.getHeaderFiles(),
            buildGradleCommand()!!,
            warning,
            configurations,
            visualStudioProject!!.getProjectFile().getXmlActions()
        )
    }

    /**
     * The data to use to generate the project file.
     *
     * @since 8.11
     */
    @Incubating
    protected class ProjectSpec private constructor(
        val visualStudioVersion: Provider<VersionNumber?>,
        val sdkVersion: Provider<VersionNumber?>,
        val sourceFiles: FileCollection,
        val resourceFiles: MutableSet<File>,
        val headerFiles: FileCollection,
        /**
         * Command to use to run Gradle from the project.
         *
         * @since 8.11
         */
        @get:Input @get:Incubating val gradleCommand: String,
        @get:Input @get:Optional @get:Incubating val warning: String?,
        /**
         * Configurations to include in the project.
         *
         * @since 8.11
         */
        @get:Nested @get:Incubating val configurations: MutableList<VisualStudioProjectFile.ConfigurationSpec>,
        /**
         * Additional XML generation actions.
         *
         * @since 8.11
         */
        @get:Nested @get:Incubating val xmlActions: MutableList<Action<in XmlProvider?>?>
    ) {
        /**
         * The VS version for this project.
         *
         * @since 8.11
         */
        @Input
        @Incubating
        fun getVisualStudioVersion(): Provider<String?> {
            return visualStudioVersion.map<String?>(Transformer { obj: VersionNumber? -> obj.toString() })
        }

        /**
         * The SDK version for this project.
         * @since 8.11
         */
        @Input
        @Incubating
        fun getSdkVersion(): Provider<String?> {
            return sdkVersion.map<String?>(Transformer { obj: VersionNumber? -> obj.toString() })
        }

        @get:Incubating
        @get:Input
        val sourceFilePaths: Provider<MutableSet<String?>?>
            /**
             * The source files for this project.
             *
             * @since 8.11
             */
            get() = sourceFiles.getElements()
                .map<MutableSet<String?>?>(Transformer { files: MutableSet<FileSystemLocation?>? ->
                    collect(
                        files,
                        { file -> file.getAsFile().getAbsolutePath() })
                })

        @get:Incubating
        @get:Input
        val resourceFilePaths: MutableSet<String?>
            /**
             * The resource files for this project.
             *
             * @since 8.11
             */
            get() = collect(resourceFiles, { obj: File? -> obj!!.getAbsolutePath() })

        @get:Incubating
        @get:Input
        val headerFilesPaths: Provider<MutableSet<String?>?>
            /**
             * The header files for this project.
             * @since 8.11
             */
            get() = headerFiles.getElements()
                .map<MutableSet<String?>?>(Transformer { files: MutableSet<FileSystemLocation?>? ->
                    collect(
                        files,
                        { file -> file.getAsFile().getAbsolutePath() })
                })
    }
}
