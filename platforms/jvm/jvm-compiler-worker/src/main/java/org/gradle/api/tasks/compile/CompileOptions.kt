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
package org.gradle.api.tasks.compile

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor
import org.gradle.internal.instrumentation.api.annotations.ReplacedDeprecation
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.util.internal.CollectionUtils
import java.io.Serializable
import javax.inject.Inject

/**
 * Main options for Java compilation.
 */
abstract class CompileOptions @Inject @Suppress("unused") constructor(objectFactory: ObjectFactory?) : Serializable {
    /**
     * Tells whether to fail the build when compilation fails. Defaults to `true`.
     */
    /**
     * Sets whether to fail the build when compilation fails. Defaults to `true`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isFailOnError: Boolean = true

    /**
     * Tells whether to produce verbose output. Defaults to `false`.
     */
    /**
     * Sets whether to produce verbose output. Defaults to `false`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Console
    var isVerbose: Boolean = false

    /**
     * Tells whether to log the files to be compiled. Defaults to `false`.
     */
    /**
     * Sets whether to log the files to be compiled. Defaults to `false`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Console
    var isListFiles: Boolean = false

    /**
     * Tells whether to log details of usage of deprecated members or classes. Defaults to `false`.
     */
    /**
     * Sets whether to log details of usage of deprecated members or classes. Defaults to `false`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Console
    var isDeprecation: Boolean = false

    /**
     * Tells whether to log warning messages. The default is `true`.
     */
    /**
     * Sets whether to log warning messages. The default is `true`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Console
    var isWarnings: Boolean = true

    /**
     * Returns the character encoding to be used when reading source files. Defaults to `null`, in which
     * case the platform default encoding will be used.
     */
    /**
     * Sets the character encoding to be used when reading source files. Defaults to `null`, in which
     * case the platform default encoding will be used.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var encoding: String? = null

    /**
     * Tells whether to include debugging information in the generated class files. Defaults
     * to `true`. See [DebugOptions.getDebugLevel] for which debugging information will be generated.
     */
    /**
     * Sets whether to include debugging information in the generated class files. Defaults
     * to `true`. See [DebugOptions.getDebugLevel] for which debugging information will be generated.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isDebug: Boolean = true

    /**
     * Tells whether to run the compiler in its own process. Note that this does
     * not necessarily mean that a new process will be created for each compile task.
     * Defaults to `false`.
     */
    /**
     * Sets whether to run the compiler in its own process. Note that this does
     * not necessarily mean that a new process will be created for each compile task.
     * Defaults to `false`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isFork: Boolean = false

    /**
     * Returns the bootstrap classpath to be used for the compiler process. Defaults to `null`.
     *
     * @since 4.3
     */
    /**
     * Sets the bootstrap classpath to be used for the compiler process. Defaults to `null`.
     *
     * @since 4.3
     */
    @get:ToBeReplacedByLazyProperty
    @get:CompileClasspath
    @get:Optional
    var bootstrapClasspath: FileCollection? = null

    /**
     * Returns the extension dirs to be used for the compiler process. Defaults to `null`.
     */
    /**
     * Sets the extension dirs to be used for the compiler process. Defaults to `null`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var extensionDirs: String? = null

    /**
     * Returns any additional arguments to be passed to the compiler.
     * Defaults to the empty list.
     *
     * Compiler arguments not supported by the DSL can be added here.
     *
     * For example, it is possible to pass the `--enable-preview` option that was added in newer Java versions:
     * <pre>`compilerArgs.add("--enable-preview")`</pre>
     *
     * Note that if `--release` is added then `-target` and `-source`
     * are ignored.
     */
    /**
     * Sets any additional arguments to be passed to the compiler.
     * Defaults to the empty list.
     */
    @JvmField
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var compilerArgs: MutableList<String?>? = ArrayList<String?>()

    /**
     * Compiler argument providers.
     *
     * @since 4.5
     */
    @get:ToBeReplacedByLazyProperty(comment = "Should this be lazy?")
    @get:Nested
    val compilerArgumentProviders: MutableList<CommandLineArgumentProvider> = ArrayList<CommandLineArgumentProvider>()

    /**
     * informs whether to use incremental compilation feature. See [.setIncremental]
     */
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var isIncremental: Boolean = true
        private set

    /**
     * The source path to use for the compilation.
     *
     *
     * The source path indicates the location of source files that *may* be compiled if necessary.
     * It is effectively a complement to the class path, where the classes to be compiled against are in source form.
     * It does **not** indicate the actual primary source being compiled.
     *
     *
     * The source path feature of the Java compiler is rarely needed for modern builds that use dependency management.
     *
     *
     * The default value for the source path is `null`, which indicates an *empty* source path.
     * Note that this is different to the default value for the `-sourcepath` option for `javac`, which is to use the value specified by `-classpath`.
     * If you wish to use any source path, it must be explicitly set.
     *
     * @return the source path
     * @see .setSourcepath
     */
    /**
     * Sets the source path to use for the compilation.
     *
     * @param sourcepath the source path
     */
    @JvmField
    @get:ToBeReplacedByLazyProperty
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:Optional
    var sourcepath: FileCollection? = null

    /**
     * Returns the classpath to use to load annotation processors. This path is also used for annotation processor discovery.
     *
     * @return The annotation processor path, or `null` if annotation processing is disabled.
     * @since 3.4
     */
    /**
     * Set the classpath to use to load annotation processors. This path is also used for annotation processor discovery.
     *
     * @param annotationProcessorPath The annotation processor path, or `null` to disable annotation processing.
     * @since 3.4
     */
    @JvmField
    @get:ToBeReplacedByLazyProperty
    @get:Classpath
    @get:Optional
    var annotationProcessorPath: FileCollection? = null

    @get:Nested
    abstract val debugOptions: DebugOptions?

    /**
     * Execute the given action against [.getDebugOptions].
     *
     * @since 8.11
     */
    fun debugOptions(action: Action<in DebugOptions?>) {
        action.execute(this.debugOptions)
    }

    @JvmField
    @get:Nested
    abstract val forkOptions: ForkOptions?

    /**
     * Execute the given action against [.getForkOptions].
     *
     * @since 8.11
     */
    fun forkOptions(action: Action<in ForkOptions?>) {
        action.execute(this.forkOptions)
    }

    @get:ToBeReplacedByLazyProperty
    @get:Internal
    val allCompilerArgs: MutableList<String?>
        /**
         * Returns all compiler arguments, added to the [.getCompilerArgs] or the [.getCompilerArgumentProviders] property.
         *
         * @since 4.5
         */
        get() {
            val builder = ImmutableList.builder<String?>()
            builder.addAll(CollectionUtils.stringize(this.compilerArgs!!))
            for (compilerArgumentProvider in this.compilerArgumentProviders) {
                builder.addAll(CollectionUtils.toStringList(compilerArgumentProvider.asArguments()!!))
            }
            return builder.build()
        }

    /**
     * Configure the java compilation to be incremental (e.g. compiles only those java classes that were changed or that are dependencies to the changed classes).
     */
    fun setIncremental(incremental: Boolean): CompileOptions {
        this.isIncremental = incremental
        return this
    }

    @JvmField
    @get:Incubating
    @get:Optional
    @get:Input
    abstract val incrementalAfterFailure: Property<Boolean?>?

    @JvmField
    @get:Optional
    @get:Input
    abstract val release: Property<Int?>?


    @get:Input
    @get:Optional
    abstract val javaModuleVersion: Property<String?>?

    @JvmField
    @get:Input
    @get:Optional
    abstract val javaModuleMainClass: Property<String?>?

    @JvmField
    @get:ReplacesEagerProperty(
        replacedAccessors = [ReplacedAccessor(
            value = ReplacedAccessor.AccessorType.GETTER,
            name = "getAnnotationProcessorGeneratedSourcesDirectory"
        ), ReplacedAccessor(
            value = ReplacedAccessor.AccessorType.SETTER,
            name = "setAnnotationProcessorGeneratedSourcesDirectory"
        )],
        deprecation = ReplacedDeprecation(removedIn = ReplacedDeprecation.RemovedIn.GRADLE9)
    )
    @get:OutputDirectory
    @get:Optional
    abstract val generatedSourceOutputDirectory: DirectoryProperty?

    @JvmField
    @get:OutputDirectory
    @get:Optional
    abstract val headerOutputDirectory: DirectoryProperty?

    companion object {
        private const val serialVersionUID: Long = 0
    }
}
