/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.language.nativeplatform.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Transformer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.WorkResult
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.language.nativeplatform.internal.incremental.IncrementalCompilerBuilder
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.Clang
import org.gradle.nativeplatform.toolchain.Gcc
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.work.DisableCachingByDefault
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import javax.inject.Inject

/**
 * Compiles native source files into object files.
 */
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
abstract class AbstractNativeCompileTask : DefaultTask() {
    /**
     * Should the compiler generate position independent code?
     */
    @get:Input
    var isPositionIndependentCode: Boolean = false
    /**
     * Should the compiler generate debuggable code?
     *
     * @since 4.3
     */
    /**
     * Should the compiler generate debuggable code?
     *
     * @since 4.3
     */
    @get:Input
    var isDebuggable: Boolean = false
    /**
     * Should the compiler generate optimized code?
     *
     * @since 4.3
     */
    /**
     * Should the compiler generate optimized code?
     *
     * @since 4.3
     */
    @get:Input
    var isOptimized: Boolean = false

    /**
     * Returns the source files to be compiled.
     */
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    @get:InputFiles
    val source: ConfigurableFileCollection
    private val macros: MutableMap<String?, String?> = LinkedHashMap<String?, String?>()

    // Don't serialize the compiler. It holds state that is mostly only required at execution time and that can be calculated from the other fields of this task
    // after being deserialized. However, it is also required to calculate the producers of the header files to calculate the work graph.
    // It would be better to provide some way for a task to express these things separately.
    @Transient
    private var incrementalCompiler: IncrementalCompilerBuilder.IncrementalCompiler? = null
        get() {
            if (field == null) {
                field = this.incrementalCompilerBuilder.newCompiler(
                    this,
                    source,
                    this.includes.plus(this.systemIncludes),
                    macros,
                    this.toolChain.map<Boolean?>(Transformer { nativeToolChain: NativeToolChain? -> nativeToolChain is Gcc || nativeToolChain is Clang })
                )
            }
            return field
        }

    init {
        dependsOn(this.includes)
        dependsOn(this.systemIncludes)

        this.source = this.taskFileVarFactory.newInputFileCollection(this)
    }

    @get:Inject
    protected abstract val taskFileVarFactory: TaskFileVarFactory?

    @get:Inject
    protected abstract val incrementalCompilerBuilder: IncrementalCompilerBuilder?

    @get:Inject
    protected abstract val operationLoggerFactory: BuildOperationLoggerFactory?

    @get:Inject
    protected abstract val fileCollectionFactory: FileCollectionFactory?

    @TaskAction
    protected fun compile(inputs: InputChanges) {
        val operationLogger = this.operationLoggerFactory.newOperationLogger(getName(), getTemporaryDir())
        val spec = createCompileSpec()
        spec.setTargetPlatform(this.targetPlatform.get())
        spec.setTempDir(getTemporaryDir())
        spec.objectFileDir = this.objectFileDir.get().getAsFile()
        spec.include(this.includes)
        spec.systemInclude(this.systemIncludes)
        spec.source(this.source)
        spec.setMacros(getMacros())
        spec.args(this.compilerArgs.get())
        spec.isPositionIndependentCode = this.isPositionIndependentCode
        spec.isDebuggable = this.isDebuggable
        spec.isOptimized = this.isOptimized
        spec.isIncrementalCompile = inputs.isIncremental()
        spec.setOperationLogger(operationLogger)

        configureSpec(spec)

        val nativeToolChain = this.toolChain.get() as NativeToolChainInternal
        val nativePlatform = this.targetPlatform.get() as NativePlatformInternal
        val platformToolProvider = nativeToolChain.select(nativePlatform)
        setDidWork(doCompile<NativeCompileSpec?>(spec, platformToolProvider)!!.getDidWork())
    }

    protected open fun configureSpec(spec: NativeCompileSpec?) {
    }

    private fun <T : NativeCompileSpec?> doCompile(spec: T?, platformToolProvider: PlatformToolProvider): WorkResult? {
        val specType: Class<T?> = uncheckedCast<Class<T?>?>(spec!!.javaClass)!!
        val baseCompiler = platformToolProvider.newCompiler<T?>(specType)
        val incrementalCompiler = this.incrementalCompiler!!.createCompiler<T?>(baseCompiler)
        val loggingCompiler = BuildOperationLoggingCompilerDecorator.wrap<T?>(incrementalCompiler)
        return loggingCompiler.execute(spec)
    }

    protected abstract fun createCompileSpec(): NativeCompileSpec

    @JvmField
    @get:Internal
    abstract val toolChain: Property<NativeToolChain?>?

    @JvmField
    @get:Nested
    abstract val targetPlatform: Property<NativePlatform?>?

    @JvmField
    @get:OutputDirectory
    abstract val objectFileDir: DirectoryProperty?

    @JvmField
    @get:Internal("The paths for include directories are tracked via the includePaths property, the contents are tracked via discovered inputs")
    abstract val includes: ConfigurableFileCollection?

    /**
     * Add directories where the compiler should search for header files.
     */
    fun includes(includeRoots: Any) {
        this.includes.from(includeRoots)
    }

    @JvmField
    @get:Internal("The paths for include directories are tracked via the includePaths property, the contents are tracked via discovered inputs")
    abstract val systemIncludes: ConfigurableFileCollection?

    /**
     * Adds a set of source files to be compiled. The provided sourceFiles object is evaluated as per [Project.files].
     */
    fun source(sourceFiles: Any) {
        source.from(sourceFiles)
    }

    /**
     * Macros that should be defined for the compiler.
     */
    @Input
    fun getMacros(): MutableMap<String?, String?> {
        return macros
    }

    fun setMacros(macros: MutableMap<String?, String?>) {
        this.macros.clear()
        this.macros.putAll(macros)
    }

    @JvmField
    @get:Input
    abstract val compilerArgs: ListProperty<String?>?

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputFiles
    @get:Incremental
    protected val headerDependencies: FileCollection?
        /**
         * The set of dependent headers. This is used for up-to-date checks only.
         *
         * @since 4.3
         */
        get() = this.incrementalCompiler!!.getHeaderFiles()
}
