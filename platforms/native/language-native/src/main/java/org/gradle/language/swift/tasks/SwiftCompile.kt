/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.language.swift.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.Cast
import org.gradle.internal.file.Deleter
import org.gradle.internal.operations.logging.BuildOperationLogger
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.base.internal.compile.VersionAwareCompiler
import org.gradle.language.swift.tasks.internal.DefaultSwiftCompileSpec
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.compilespec.SwiftCompileSpec
import org.gradle.nativeplatform.toolchain.internal.swift.IncrementalSwiftCompiler
import org.gradle.work.ChangeType
import org.gradle.work.InputChanges
import java.io.File
import javax.inject.Inject

/**
 * Compiles Swift source files into object files.
 *
 * @since 4.1
 */
@CacheableTask
abstract class SwiftCompile @Inject constructor(
    private val compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory?,
    private val deleter: Deleter?
) : DefaultTask() {
    private val debuggable: Property<Boolean?>
    private val optimize: Property<Boolean?>

    init {
        val objectFactory = getProject().getObjects()
        // TODO: There is something wrong in the ASM class generator that does not allow us to create
        // this as a managed property as long as we have isDebuggable.
        this.debuggable = objectFactory.property<Boolean?>(Boolean::class.java).value(false)
        this.optimize = objectFactory.property<Boolean?>(Boolean::class.java).value(false)
    }

    @get:Internal
    abstract val toolChain: Property<NativeToolChain?>?

    @get:Nested
    abstract val targetPlatform: Property<NativePlatform?>?

    @JvmField
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    @get:InputFiles
    abstract val source: ConfigurableFileCollection?

    @get:Input
    abstract val macros: ListProperty<String?>?

    /**
     * Should the compiler generate debuggable code?
     *
     * @since 4.7
     */
    @Internal
    fun isDebuggable(): Boolean {
        return getDebuggable().get()!!
    }

    /**
     * Should the compiler generate debuggable code?
     *
     * @since 4.7
     */
    @Input
    fun getDebuggable(): Property<Boolean?> {
        return debuggable
    }

    @get:Internal
    val isOptimized: Boolean
        /**
         * Should the compiler generate debuggable code?
         *
         * @since 4.7
         */
        get() = getOptimized().get()!!

    /**
     * Should the compiler generate optimized code?
     *
     * @since 4.7
     */
    @Input
    fun getOptimized(): Property<Boolean?> {
        return optimize
    }

    @JvmField
    @get:Input
    abstract val compilerArgs: ListProperty<String?>?

    @get:OutputDirectory
    abstract val objectFileDir: DirectoryProperty?

    @get:OutputFile
    abstract val moduleFile: RegularFileProperty?

    @get:Input
    @get:Optional
    abstract val moduleName: Property<String?>?

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputFiles
    abstract val modules: ConfigurableFileCollection?

    @get:Input
    abstract val sourceCompatibility: Property<SwiftVersion?>?

    @get:Nested
    protected val compilerVersion: CompilerVersion?
        /**
         * The compiler used, including the type and the version.
         *
         * @since 4.4
         */
        get() = (createCompiler() as VersionAwareCompiler<*>).getVersion()

    private fun createCompiler(): Compiler<SwiftCompileSpec?> {
        val targetPlatform = Cast.cast<NativePlatformInternal?, NativePlatform?>(NativePlatformInternal::class.java, this.targetPlatform.get())
        val toolChain = Cast.cast<NativeToolChainInternal?, NativeToolChain?>(NativeToolChainInternal::class.java, this.toolChain.get())
        val toolProvider = toolChain!!.select(targetPlatform!!)
        return toolProvider.newCompiler<SwiftCompileSpec?>(SwiftCompileSpec::class.java)
    }

    @TaskAction
    protected fun compile(inputs: InputChanges) {
        val removedFiles: MutableList<File?> = ArrayList<File?>()
        val changedFiles: MutableSet<File?> = HashSet<File?>()
        val isIncremental = inputs.isIncremental()

        // TODO: This should become smarter and move into the compiler infrastructure instead
        //   of the task, similar to how the other native languages are done.
        //   For now, this does a rudimentary incremental build analysis by looking at
        //   which files changed .
        if (isIncremental) {
            for (fileChange in inputs.getFileChanges(this.source)) {
                if (fileChange.getChangeType() == ChangeType.REMOVED) {
                    removedFiles.add(fileChange.getFile())
                } else {
                    changedFiles.add(fileChange.getFile())
                }
            }
        }

        val operationLogger = getServices().get<BuildOperationLoggerFactory?>(BuildOperationLoggerFactory::class.java)!!.newOperationLogger(getName(), getTemporaryDir())

        val targetPlatform = Cast.cast<NativePlatformInternal?, NativePlatform?>(NativePlatformInternal::class.java, this.targetPlatform.get())
        val spec = createSpec(operationLogger, isIncremental, changedFiles, removedFiles, targetPlatform)
        val baseCompiler: Compiler<SwiftCompileSpec?> = IncrementalSwiftCompiler(
            createCompiler(),
            getOutputs(),
            compilerOutputFileNamingSchemeFactory,
            deleter
        )
        val loggingCompiler = BuildOperationLoggingCompilerDecorator.wrap<SwiftCompileSpec?>(baseCompiler)
        val result = loggingCompiler.execute(spec)
        setDidWork(result.getDidWork())
    }

    private fun createSpec(
        operationLogger: BuildOperationLogger?,
        isIncremental: Boolean,
        changedFiles: MutableCollection<File?>?,
        removedFiles: MutableCollection<File?>?,
        targetPlatform: NativePlatformInternal?
    ): SwiftCompileSpec {
        val spec: SwiftCompileSpec = DefaultSwiftCompileSpec()
        spec.setModuleName(this.moduleName.getOrNull())
        spec.setModuleFile(this.moduleFile.get().getAsFile())
        for (file in this.modules.getFiles()) {
            if (file.isFile()) {
                spec.include(file.getParentFile())
            } else {
                spec.include(file)
            }
        }

        spec.setTargetPlatform(targetPlatform)
        spec.setTempDir(getTemporaryDir())
        spec.setObjectFileDir(this.objectFileDir.get().getAsFile())
        spec.source(this.source)
        spec.setRemovedSourceFiles(removedFiles)
        spec.setChangedFiles(changedFiles)

        // Convert Swift-like macros to a Map like NativeCompileSpec expects
        val macros: MutableMap<String?, String?> = LinkedHashMap<String?, String?>()
        for (macro in this.macros.get()) {
            macros.put(macro, null)
        }
        spec.setMacros(macros)
        spec.args(this.compilerArgs.get())
        spec.setDebuggable(getDebuggable().get()!!)
        spec.setOptimized(getOptimized().get()!!)
        spec.setIncrementalCompile(isIncremental)
        spec.setOperationLogger(operationLogger)
        spec.setSourceCompatibility(this.sourceCompatibility.get())
        return spec
    }
}
