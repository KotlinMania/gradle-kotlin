/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.plugins.antlr

import org.gradle.api.Incubating
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileType
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.antlr.internal.AntlrExecuter
import org.gradle.api.plugins.antlr.internal.AntlrResult
import org.gradle.api.plugins.antlr.internal.AntlrSourceGenerationException
import org.gradle.api.plugins.antlr.internal.AntlrSpec
import org.gradle.api.plugins.antlr.internal.AntlrSpecFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.file.Deleter.ensureEmptyDirectory
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.process.internal.JavaExecHandleBuilder.maxHeapSize
import org.gradle.process.internal.JavaExecHandleBuilder.redirectErrorStream
import org.gradle.process.internal.JavaExecHandleBuilder.systemProperty
import org.gradle.process.internal.worker.MultiRequestClient
import org.gradle.work.ChangeType
import org.gradle.work.InputChanges
import org.jspecify.annotations.NullMarked
import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * Generates parsers from Antlr grammars.
 */
@NullMarked
@CacheableTask
abstract class AntlrTask : SourceTask() {
    /**
     * Specifies that all rules call `traceIn`/`traceOut`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isTrace: Boolean = false

    /**
     * Specifies that all lexer rules call `traceIn`/`traceOut`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isTraceLexer: Boolean = false

    /**
     * Specifies that all parser rules call `traceIn`/`traceOut`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isTraceParser: Boolean = false

    /**
     * Specifies that all tree walker rules call `traceIn`/`traceOut`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isTraceTreeWalker: Boolean = false

    /**
     * List of command-line arguments passed to the antlr process
     *
     * @return The antlr command-line arguments
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var arguments: MutableList<String> = ArrayList<String>()
        set(arguments) {
            if (arguments != null) {
                field = arguments
            }
        }

    /**
     * Returns the classpath containing the Ant ANTLR task implementation.
     *
     * @return The Ant task implementation classpath.
     */
    /**
     * Specifies the classpath containing the Ant ANTLR task implementation.
     *
     * @param antlrClasspath The Ant task implementation classpath. Must not be null.
     */
    @get:ToBeReplacedByLazyProperty(unreported = true, comment = "Setter has protected access")
    @get:Classpath
    var antlrClasspath: FileCollection? = null
        /**
         * Specifies the classpath containing the Ant ANTLR task implementation.
         *
         * @param antlrClasspath The Ant task implementation classpath. Must not be null.
         */
        protected set

    /**
     * Returns the directory to generate the parser source files into.
     *
     * @return The output directory.
     */
    /**
     * Specifies the directory to generate the parser source files into.
     *
     * @param outputDirectory The output directory. Must not be null.
     */
    @get:ToBeReplacedByLazyProperty
    @get:OutputDirectory
    var outputDirectory: File? = null

    /**
     * The maximum heap size for the forked antlr process (ex: '1g').
     */
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var maxHeapSize: String? = null
    private var sourceSetDirectories: FileCollection? = null

    /**
     * The sources for incremental change detection.
     *
     * @since 6.0
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    protected val stableSources: FileCollection = getProject().files(Callable { this.getSource() } as Callable<Any?>)


    @get:Inject
    protected abstract val workerProcessBuilderFactory: WorkerProcessFactory?

    @get:Inject
    protected abstract val projectLayout: ProjectLayout?

    /**
     * Generate the parsers.
     *
     * @since 6.0
     */
    @TaskAction
    fun execute(inputChanges: InputChanges) {
        val grammarFiles: MutableSet<File> = HashSet<File>()
        val stableSources = this.stableSources
        if (inputChanges.isIncremental()) {
            var rebuildRequired = false
            for (fileChange in inputChanges.getFileChanges(stableSources)) {
                if (fileChange.getFileType() == FileType.FILE) {
                    if (fileChange.getChangeType() == ChangeType.REMOVED) {
                        rebuildRequired = true
                        break
                    }
                    grammarFiles.add(fileChange.getFile())
                }
            }
            if (rebuildRequired) {
                try {
                    this.deleter.ensureEmptyDirectory(this.outputDirectory)
                } catch (ex: IOException) {
                    throw throwAsUncheckedException(ex)
                }
                grammarFiles.addAll(stableSources.getFiles())
            }
        } else {
            grammarFiles.addAll(stableSources.getFiles())
        }

        val spec = AntlrSpecFactory().create(this, grammarFiles, sourceSetDirectories)
        val client = getAntlrWorkerClient(spec)

        var result: AntlrResult?
        try {
            client.start()
            result = client.run(spec)
        } finally {
            client.stop()
        }

        evaluate(result)
    }

    private fun getAntlrWorkerClient(spec: AntlrSpec): MultiRequestClient<AntlrSpec, AntlrResult> {
        val builder =
            this.workerProcessBuilderFactory.multiRequestWorker<AntlrSpec, AntlrResult>(AntlrExecuter::class.java)

        builder.setBaseName("Gradle ANTLR Worker")
        builder.applicationClasspath(this.antlrClasspath)
        builder.sharedPackages("antlr", "org.antlr")

        val javaCommand = builder.getJavaCommand()
        javaCommand.setWorkingDir(projectDir())
        javaCommand.maxHeapSize = spec.getMaxHeapSize()
        javaCommand.systemProperty("ANTLR_DO_NOT_EXIT", "true")
        javaCommand.redirectErrorStream()

        return builder.build()
    }

    private fun evaluate(result: AntlrResult) {
        val errorCount = result.getErrorCount()
        if (errorCount < 0) {
            throw AntlrSourceGenerationException("There were errors during grammar generation", result.getException())
        } else if (errorCount == 1) {
            throw AntlrSourceGenerationException("There was 1 error during grammar generation", result.getException())
        } else if (errorCount > 1) {
            throw AntlrSourceGenerationException(
                ("There were "
                        + errorCount
                        + " errors during grammar generation"), result.getException()
            )
        }
    }

    private fun projectDir(): File {
        return this.projectLayout.getProjectDirectory().getAsFile()
    }

    /**
     * Sets the source for this task. Delegates to [.setSource].
     *
     * If the source is of type [SourceDirectorySet], then the relative path of each source grammar files
     * is used to determine the relative output path of the generated source
     * If the source is not of type [SourceDirectorySet], then the generated source files end up
     * flattened in the specified output directory.
     *
     * @param source The source.
     * @since 4.0
     */
    override fun setSource(source: FileTree) {
        setSource(source as Any)
    }

    /**
     * Sets the source for this task. Delegates to [SourceTask.setSource].
     *
     * If the source is of type [SourceDirectorySet], then the relative path of each source grammar files
     * is used to determine the relative output path of the generated source
     * If the source is not of type [SourceDirectorySet], then the generated source files end up
     * flattened in the specified output directory.
     *
     * @param source The source.
     */
    override fun setSource(source: Any) {
        super.setSource(source)
        if (source is SourceDirectorySet) {
            this.sourceSetDirectories = source.getSourceDirectories()
        }
    }

    /**
     * {@inheritDoc}
     */
    @Internal("tracked via stableSources")
    @ToBeReplacedByLazyProperty
    override fun getSource(): FileTree {
        return super.getSource()
    }

    @get:Inject
    protected abstract val deleter: Deleter?

    @get:Incubating
    @get:Optional
    @get:Input
    abstract val packageName: Property<String>?
}
