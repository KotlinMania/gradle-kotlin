/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.scala

import org.gradle.api.Action
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.scala.internal.GenerateScaladoc
import org.gradle.api.tasks.scala.internal.ScalaRuntimeHelper
import org.gradle.api.tasks.scala.internal.ScaladocParameters
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.util.internal.GUtil
import org.gradle.workers.ProcessWorkerSpec
import java.io.File
import javax.inject.Inject

/**
 * Generates HTML API documentation for Scala source files.
 */
@CacheableTask
abstract class ScalaDoc : SourceTask() {
    /**
     * Returns the directory to generate the API documentation into.
     */
    @get:ToBeReplacedByLazyProperty
    @get:OutputDirectory
    var destinationDir: File? = null

    /**
     *
     * Returns the classpath to use to locate classes referenced by the documented source.
     *
     * @return The classpath.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Classpath
    var classpath: FileCollection? = null

    /**
     * Returns the classpath to use to load the ScalaDoc tool.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Classpath
    var scalaClasspath: FileCollection? = null

    /**
     * Returns the documentation title.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var title: String? = null

    init {
        val javaToolchainService = this.javaToolchainService
        this.javaLauncher.convention(javaToolchainService.launcherFor(Action { it: JavaToolchainSpec? -> }))
    }

    /**
     * Returns the source for this task, after the include and exclude patterns have been applied. Ignores source files which do not exist.
     *
     *
     *
     * The [PathSensitivity] for the sources is configured to be [PathSensitivity.RELATIVE].
     *
     *
     * @return The source.
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @ToBeReplacedByLazyProperty
    override fun getSource(): FileTree {
        return super.getSource()
    }

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:InputFiles
    protected val filteredCompilationOutputs: FileTree
        /**
         * Returns the compilation outputs needed by Scaladoc filtered to include [TASTy](https://docs.scala-lang.org/scala3/guides/tasty-overview.html) files.
         *
         *
         * NOTE: This is only useful with Scala 3 or later. Scala 2 only processes source files.
         *
         * @return the compilation outputs produced from the sources
         * @since 7.3
         */
        get() = this.compilationOutputs.getAsFileTree().matching(getPatternSet())
            .matching(Action { pattern: PatternFilterable? -> pattern!!.include("**/*.tasty") })

    @get:Internal
    abstract val compilationOutputs: ConfigurableFileCollection?

    @get:Nested
    abstract val scalaDocOptions: ScalaDocOptions

    /**
     * Configures the ScalaDoc generation options.
     *
     * @since 8.11
     */
    fun scalaDocOptions(action: Action<in ScalaDocOptions?>) {
        action.execute(this.scalaDocOptions)
    }

    @get:Internal
    abstract val maxMemory: Property<String?>?

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher?>?

    @TaskAction
    protected fun generate() {
        val options = this.scalaDocOptions
        if (!GUtil.isTrue(options.getDocTitle())) {
            options.setDocTitle(this.title)
        }

        val queue = this.workerExecutor.processIsolation(Action { worker: ProcessWorkerSpec? ->
            worker!!.getClasspath().from(this.scalaClasspath)
            val forkOptions = worker.getForkOptions()
            if (this.maxMemory.isPresent()) {
                forkOptions.setMaxHeapSize(this.maxMemory.get())
            }
            forkOptions.setExecutable(this.javaLauncher.get().getExecutablePath().getAsFile().getAbsolutePath())
        })
        queue.submit<ScaladocParameters?>(GenerateScaladoc::class.java, Action { parameters: ScaladocParameters? ->
            val optionsFile = createOptionsFile()
            parameters!!.optionsFile.set(optionsFile)
            parameters.classpath.from(this.classpath)
            parameters.outputDirectory.set(this.destinationDir)
            val isScala3 = ScalaRuntimeHelper.findScalaJar(this.scalaClasspath, "library_3") != null
            parameters.isScala3.set(isScala3)
            if (isScala3) {
                parameters.sources.from(this.filteredCompilationOutputs)
            } else {
                parameters.sources.from(getSource())

                if (options.isDeprecation()) {
                    parameters.options.add("-deprecation")
                }

                if (options.isUnchecked()) {
                    parameters.options.add("-unchecked")
                }
            }

            val footer = options.getFooter()
            if (footer != null) {
                parameters.options.add("-doc-footer")
                parameters.options.add(footer)
            }

            val docTitle = options.getDocTitle()
            if (docTitle != null) {
                parameters.options.add("-doc-title")
                parameters.options.add(docTitle)
            }

            // None of these options work for Scala >=2.8
            // options.getBottom();;
            // options.getTop();
            // options.getHeader();
            // options.getWindowTitle();
            val additionalParameters = options.getAdditionalParameters()
            if (additionalParameters != null) {
                parameters.options.addAll(additionalParameters)
            }
        })
    }

    /**
     * Creates the file to hold the options for the scaladoc process.
     *
     * @implNote This file will be cleaned up by [GenerateScaladoc.execute].
     */
    private fun createOptionsFile(): File? {
        return File(getTemporaryDir(), "scaladoc.options")
    }

    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    @get:Inject
    protected abstract val workerExecutor: WorkerExecutor?

    @get:Inject
    protected abstract val javaToolchainService: JavaToolchainService
}
