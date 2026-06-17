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
package org.gradle.nativeplatform.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.Cast
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator
import org.gradle.nativeplatform.internal.DefaultStaticLibraryArchiverSpec
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Assembles a static library from object files.
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class CreateStaticLibrary : DefaultTask(), ObjectFilesToBinary {
    private val source: ConfigurableFileCollection

    init {
        this.source = getProject().files()
    }

    /**
     * The source object files to be passed to the archiver.
     */
    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getSource(): FileCollection {
        return source
    }

    /**
     * Adds a set of object files to be linked.
     *
     * The provided source object is evaluated as per [Project.files].
     */
    override fun source(source: Any) {
        this.source.from(source)
    }

    @get:Inject
    abstract val operationLoggerFactory: BuildOperationLoggerFactory?

    // TODO: Need to track version/implementation of ar tool.
    @TaskAction
    protected fun link() {
        val spec: StaticLibraryArchiverSpec = DefaultStaticLibraryArchiverSpec()
        spec.setTempDir(getTemporaryDir())
        spec.setOutputFile(this.outputFile.get().getAsFile())
        spec.objectFiles(getSource())
        spec.args(this.staticLibArgs.get())

        val operationLogger = this.operationLoggerFactory.newOperationLogger(getName(), getTemporaryDir())
        spec.setOperationLogger(operationLogger)

        val compiler = createCompiler()
        val result = BuildOperationLoggingCompilerDecorator.wrap<StaticLibraryArchiverSpec?>(compiler).execute(spec)
        setDidWork(result.getDidWork())
    }

    private fun createCompiler(): Compiler<StaticLibraryArchiverSpec?>? {
        val targetPlatform = Cast.cast<NativePlatformInternal?, NativePlatform?>(NativePlatformInternal::class.java, this.targetPlatform.get())
        val toolChain = Cast.cast<NativeToolChainInternal?, NativeToolChain?>(NativeToolChainInternal::class.java, this.toolChain.get())
        val toolProvider = toolChain!!.select(targetPlatform)
        return toolProvider!!.newCompiler<StaticLibraryArchiverSpec?>(StaticLibraryArchiverSpec::class.java)
    }

    @JvmField
    @get:Internal
    abstract val toolChain: Property<NativeToolChain?>?

    @JvmField
    @get:Nested
    abstract val targetPlatform: Property<NativePlatform?>?

    @JvmField
    @get:OutputFile
    abstract val outputFile: RegularFileProperty?

    @get:Internal
    val binaryFile: RegularFileProperty?
        /**
         * The file where the linked binary will be located.
         *
         * @since 4.5
         */
        get() = this.outputFile

    @JvmField
    @get:Input
    abstract val staticLibArgs: ListProperty<String?>?
}
