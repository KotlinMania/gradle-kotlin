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
import org.gradle.api.Transformer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.Cast
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.base.internal.compile.VersionAwareCompiler
import org.gradle.language.base.internal.tasks.StaleOutputCleaner
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator
import org.gradle.nativeplatform.internal.LinkerSpec
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Base task for linking a native binary from object files and libraries.
 */
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
abstract class AbstractLinkTask : DefaultTask(), ObjectFilesToBinary {
    private val source: ConfigurableFileCollection
    private val libs: ConfigurableFileCollection
    private val debuggable: Property<Boolean?>

    init {
        val objectFactory = getProject().getObjects()
        this.libs = getProject().files()
        this.source = getProject().files()
        this.destinationDirectory.convention(this.linkedFile.getLocationOnly().map<Directory?>(Transformer { regularFile: RegularFile? ->
            // TODO: Get rid of destinationDirectory entirely and replace it with a
            // collection of link outputs
            val dirProp = objectFactory.directoryProperty()
            dirProp.set(regularFile!!.getAsFile().getParentFile())
            dirProp.get()
        }))
        // TODO: There is something wrong in the ASM class generator that does not allow us to create
        // this as a managed property as long as we have isDebuggable.
        this.debuggable = objectFactory.property<Boolean?>(Boolean::class.java).value(false)
    }

    @JvmField
    @get:Internal
    abstract val toolChain: Property<NativeToolChain?>?

    @JvmField
    @get:Nested
    abstract val targetPlatform: Property<NativePlatform?>?

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty?

    @JvmField
    @get:OutputFile
    abstract val linkedFile: RegularFileProperty?

    @JvmField
    @get:Input
    abstract val linkerArgs: ListProperty<String?>?

    /**
     * Create a debuggable binary?
     *
     * @since 4.7
     */
    @Internal
    fun isDebuggable(): Boolean {
        return getDebuggable().get()!!
    }

    /**
     * Create a debuggable binary?
     *
     * @since 4.7
     */
    @Input
    fun getDebuggable(): Property<Boolean?> {
        return debuggable
    }

    /**
     * The source object files to be passed to the linker.
     */
    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getSource(): ConfigurableFileCollection {
        return source
    }

    fun setSource(source: FileCollection) {
        this.source.setFrom(source)
    }

    /**
     * The library files to be passed to the linker.
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    fun getLibs(): ConfigurableFileCollection {
        return libs
    }

    fun setLibs(libs: FileCollection) {
        this.libs.setFrom(libs)
    }

    /**
     * Adds a set of object files to be linked. The provided source object is evaluated as per [Project.files].
     */
    override fun source(source: Any) {
        this.getSource().from(source)
    }

    /**
     * Adds a set of library files to be linked. The provided libs object is evaluated as per [Project.files].
     */
    fun lib(libs: Any) {
        this.getLibs().from(libs)
    }

    @get:Nested
    protected val compilerVersion: CompilerVersion?
        /**
         * The linker used, including the type and the version.
         *
         * @since 4.7
         */
        get() = (createCompiler() as VersionAwareCompiler<*>).getVersion()

    @get:Inject
    protected abstract val operationLoggerFactory: BuildOperationLoggerFactory?

    @get:Inject
    protected abstract val deleter: Deleter?

    @TaskAction
    protected fun link() {
        val cleanedOutputs = StaleOutputCleaner.cleanOutputs(
            this.deleter,
            getOutputs().getPreviousOutputFiles(),
            this.destinationDirectory.get().getAsFile()
        )

        if (getSource().isEmpty()) {
            setDidWork(cleanedOutputs)
            return
        }

        val spec = createLinkerSpec()
        spec.setTargetPlatform(this.targetPlatform.get())
        spec.setTempDir(getTemporaryDir())
        spec.setOutputFile(this.linkedFile.get().getAsFile())

        spec.objectFiles(getSource())
        spec.libraries(getLibs())
        spec.args(this.linkerArgs.get())
        spec.setDebuggable(getDebuggable().get()!!)

        val operationLogger = this.operationLoggerFactory.newOperationLogger(getName(), getTemporaryDir())
        spec.setOperationLogger(operationLogger)

        var compiler = createCompiler()
        compiler = BuildOperationLoggingCompilerDecorator.wrap<LinkerSpec?>(compiler)
        val result = compiler.execute(spec)
        setDidWork(result.getDidWork() || cleanedOutputs)
    }

    private fun createCompiler(): Compiler<LinkerSpec?>? {
        val targetPlatform = Cast.cast<NativePlatformInternal?, NativePlatform?>(NativePlatformInternal::class.java, this.targetPlatform.get())
        val toolChain = Cast.cast<NativeToolChainInternal?, NativeToolChain?>(NativeToolChainInternal::class.java, this.toolChain.get())
        val toolProvider = toolChain!!.select(targetPlatform)
        val linkerSpecType = createLinkerSpec().javaClass as Class<LinkerSpec?>
        return toolProvider!!.newCompiler<LinkerSpec?>(linkerSpecType)
    }

    protected abstract fun createLinkerSpec(): LinkerSpec
}
