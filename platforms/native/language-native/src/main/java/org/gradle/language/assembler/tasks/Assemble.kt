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
package org.gradle.language.assembler.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Incubating
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
import org.gradle.language.assembler.internal.DefaultAssembleSpec
import org.gradle.language.base.internal.tasks.StaleOutputCleaner
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.compilespec.AssembleSpec
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * Translates Assembly language source files into object files.
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class Assemble @Inject constructor() : DefaultTask() {
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    @get:InputFiles
    val source: ConfigurableFileCollection

    /**
     * Returns the header directories to be used for compilation.
     *
     * @since 4.4
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val includes: ConfigurableFileCollection

    /**
     * The directory where object files will be generated.
     */
    @JvmField
    @get:OutputDirectory
    var objectFileDir: File? = null

    /**
     * Additional arguments to provide to the assembler.
     */
    @get:Input
    var assemblerArgs: MutableList<String?>? = null

    init {
        source = getProject().files()
        includes = getProject().files()
        getInputs().property("outputType", object : Callable<String?> {
            @Throws(Exception::class)
            override fun call(): String {
                val nativeToolChain = this.toolChain.get() as NativeToolChainInternal
                val nativePlatform = this.targetPlatform.get() as NativePlatformInternal
                return NativeToolChainInternal.Identifier.identify(nativeToolChain, nativePlatform)
            }
        })
    }

    @get:Inject
    abstract val operationLoggerFactory: BuildOperationLoggerFactory?

    @get:Inject
    protected abstract val deleter: Deleter?

    @TaskAction
    fun assemble() {
        val operationLogger = this.operationLoggerFactory.newOperationLogger(getName(), getTemporaryDir())

        val cleanedOutputs = StaleOutputCleaner.cleanOutputs(
            this.deleter,
            getOutputs().getPreviousOutputFiles(),
            this.objectFileDir
        )

        val spec = DefaultAssembleSpec()
        spec.setTempDir(getTemporaryDir())

        spec.setObjectFileDir(this.objectFileDir)
        spec.source(this.source)
        spec.include(this.includes)
        spec.args(this.assemblerArgs)
        spec.setOperationLogger(operationLogger)

        val nativeToolChain = this.toolChain.get() as NativeToolChainInternal
        val nativePlatform = this.targetPlatform.get() as NativePlatformInternal
        val compiler = nativeToolChain.select(nativePlatform).newCompiler<AssembleSpec?>(AssembleSpec::class.java)
        val result = BuildOperationLoggingCompilerDecorator.wrap<AssembleSpec?>(compiler).execute(spec)
        setDidWork(result.getDidWork() || cleanedOutputs)
    }

    /**
     * Adds a set of assembler sources files to be translated. The provided sourceFiles object is evaluated as per [Project.files].
     */
    fun source(sourceFiles: Any) {
        source.from(sourceFiles)
    }

    @JvmField
    @get:Internal
    abstract val toolChain: Property<NativeToolChain?>?

    @JvmField
    @get:Nested
    abstract val targetPlatform: Property<NativePlatform?>?

    /**
     * Add directories where the compiler should search for header files.
     *
     * @since 4.4
     */
    fun includes(includeRoots: Any) {
        includes.from(includeRoots)
    }
}
