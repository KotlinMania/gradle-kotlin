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
package org.gradle.language.rc.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Incubating
import org.gradle.api.internal.provider.Providers
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
import org.gradle.language.base.internal.compile.CompileSpec
import org.gradle.language.base.internal.compile.CompilerUtil
import org.gradle.language.nativeplatform.internal.incremental.IncrementalCompilerBuilder
import org.gradle.language.rc.internal.DefaultWindowsResourceCompileSpec
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.work.DisableCachingByDefault
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * Compiles Windows Resource scripts into .res files.
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class WindowsResourceCompile : DefaultTask() {
    /**
     * The directory where object files will be generated.
     */
    @JvmField
    @get:OutputDirectory
    var outputDir: File? = null

    /**
     * Macros that should be defined for the compiler.
     */
    @get:Input
    var macros: MutableMap<String?, String?>? = LinkedHashMap<String?, String?>()

    // Don't serialize the compiler. It holds state that is mostly only required at execution time and that can be calculated from the other fields of this task
    // after being deserialized. However, it is also required to calculate the producers of the header files to calculate the work graph.
    // It would be better to provide some way for a task to express these things separately.
    @Transient
    private var incrementalCompiler: IncrementalCompilerBuilder.IncrementalCompiler? = null
        get() {
            if (field == null) {
                field = this.incrementalCompilerBuilder.newCompiler(this, this.source, this.includes, macros, Providers.FALSE)
            }
            return field
        }

    init {
        getInputs().property("outputType", object : Callable<String?> {
            override fun call(): String {
                val nativeToolChain = this.toolChain.get() as NativeToolChainInternal
                val nativePlatform = this.targetPlatform.get() as NativePlatformInternal
                return NativeToolChainInternal.Identifier.identify(nativeToolChain, nativePlatform)
            }
        })
    }

    @get:Inject
    abstract val incrementalCompilerBuilder: IncrementalCompilerBuilder?

    @get:Inject
    abstract val operationLoggerFactory: BuildOperationLoggerFactory?

    @TaskAction
    fun compile(inputs: InputChanges) {
        val operationLogger = this.operationLoggerFactory.newOperationLogger(getName(), getTemporaryDir())

        val spec: NativeCompileSpec = DefaultWindowsResourceCompileSpec()
        spec.setTempDir(getTemporaryDir())
        spec.setObjectFileDir(this.outputDir)
        spec.include(this.includes)
        spec.source(this.source)
        spec.setMacros(this.macros)
        spec.args(this.compilerArgs.get())
        spec.setIncrementalCompile(inputs.isIncremental())
        spec.setOperationLogger(operationLogger)

        val nativeToolChain = this.toolChain.get() as NativeToolChainInternal
        val nativePlatform = this.targetPlatform.get() as NativePlatformInternal
        val platformToolProvider = nativeToolChain.select(nativePlatform)
        val result = doCompile<NativeCompileSpec?>(spec, platformToolProvider)
        setDidWork(result.getDidWork())
    }

    private fun <T : NativeCompileSpec?> doCompile(spec: T?, platformToolProvider: PlatformToolProvider): WorkResult {
        val specType: Class<T?> = uncheckedCast<Class<T?>?>(spec!!.javaClass)!!
        val baseCompiler = platformToolProvider.newCompiler<T?>(specType)
        val incrementalCompiler = this.incrementalCompiler!!.createCompiler<T?>(baseCompiler)
        val loggingCompiler = BuildOperationLoggingCompilerDecorator.wrap<T?>(incrementalCompiler)
        return CompilerUtil.castCompiler<CompileSpec?>(loggingCompiler).execute(spec)
    }

    @JvmField
    @get:Internal
    abstract val toolChain: Property<NativeToolChain?>?

    @JvmField
    @get:Nested
    abstract val targetPlatform: Property<NativePlatform?>?

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Incremental
    abstract val includes: ConfigurableFileCollection?

    /**
     * Add directories where the compiler should search for header files.
     */
    fun includes(includeRoots: Any) {
        this.includes.from(includeRoots)
    }

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    @get:InputFiles
    abstract val source: ConfigurableFileCollection?

    /**
     * Adds a set of source files to be compiled. The provided sourceFiles object is evaluated as per [Project.files].
     */
    fun source(sourceFiles: Any) {
        this.source.from(sourceFiles)
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
         * @since 4.5
         */
        get() = this.incrementalCompiler!!.getHeaderFiles()
}
