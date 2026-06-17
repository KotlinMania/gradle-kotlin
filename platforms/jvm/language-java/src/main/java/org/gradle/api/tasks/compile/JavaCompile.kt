/*
 * Copyright 2012 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.Transformer
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler
import org.gradle.api.internal.tasks.compile.CommandLineJavaCompileSpec
import org.gradle.api.internal.tasks.compile.CompilationSourceDirs
import org.gradle.api.internal.tasks.compile.CompileJavaBuildOperationReportingCompiler
import org.gradle.api.internal.tasks.compile.CompilerForkUtils
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpecFactory
import org.gradle.api.internal.tasks.compile.HasCompileOptions
import org.gradle.api.internal.tasks.compile.JavaCompileExecutableUtils
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.internal.tasks.compile.incremental.recomp.JavaRecompilationSpecProvider
import org.gradle.api.jvm.ModularitySpec
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.CompileClasspath
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
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.internal.jvm.DefaultModularitySpec
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.jvm.toolchain.JavaCompiler
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.internal.DefaultToolchainJavaCompiler
import org.gradle.jvm.toolchain.internal.JavaExecutableUtils
import org.gradle.language.base.internal.compile.CompileSpec
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.work.NormalizeLineEndings
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * Compiles Java source files.
 *
 * <pre class='autoTested'>
 * plugins {
 * id 'java'
 * }
 *
 * tasks.withType(JavaCompile).configureEach {
 * //enable compilation in a separate daemon process
 * options.fork = true
 * }
</pre> *
 */
@CacheableTask
abstract class JavaCompile : AbstractCompile(), HasCompileOptions {
    /**
     * The sources for incremental change detection.
     *
     * @since 6.0
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:NormalizeLineEndings
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    protected val stableSources: FileCollection

    /**
     * Returns the module path handling of this compile task.
     *
     * @since 6.4
     */
    @JvmField
    @get:Nested
    val modularity: ModularitySpec
    private var previousCompilationDataFile: File? = null

    init {
        val objectFactory = this.objectFactory
        this.stableSources = objectFactory.fileCollection().from(Callable { this.getSource() })
        this.modularity = objectFactory.newInstance<DefaultModularitySpec>(DefaultModularitySpec::class.java)

        val javaToolchainService = this.javaToolchainService
        val javaCompilerConvention = this.providerFactory
            .provider<JavaToolchainSpec?>(Callable {
                JavaCompileExecutableUtils.getExecutableOverrideToolchainSpec(
                    this,
                    this.propertyFactory
                )
            })
            .flatMap<JavaCompiler?>(Transformer { spec: JavaToolchainSpec? -> javaToolchainService.compilerFor(spec!!) })
            .orElse(javaToolchainService.compilerFor(Action { it: JavaToolchainSpec? -> }))

        this.javaCompiler.convention(javaCompilerConvention)
        this.javaCompiler.finalizeValueOnRead()

        getOptions()!!.incrementalAfterFailure.convention(true)
        CompilerForkUtils.doNotCacheIfForkingViaExecutable(getOptions(), getOutputs())
    }

    /**
     * {@inheritDoc}
     */
    @Internal("tracked via stableSources")
    @ToBeReplacedByLazyProperty
    override fun getSource(): FileTree {
        return super.getSource()
    }

    @JvmField
    @get:Nested
    abstract val javaCompiler: Property<JavaCompiler>?

    /**
     * Compile the sources, taking into account the changes reported by inputs.
     *
     * @since 6.0
     */
    @TaskAction
    protected fun compile(inputs: InputChanges) {
        val spec = createSpec()
        if (!getOptions()!!.isIncremental) {
            performFullCompilation(spec)
        } else {
            performIncrementalCompilation(inputs, spec)
        }
    }

    private fun performIncrementalCompilation(inputs: InputChanges, spec: DefaultJavaCompileSpec) {
        val isUsingCliCompiler = isUsingCliCompiler(spec)
        if (isUsingCliCompiler) {
            spec.getCompileOptions().setSupportsIncrementalCompilationAfterFailure(false)
        }
        spec.getCompileOptions().setSupportsCompilerApi(!isUsingCliCompiler)
        spec.getCompileOptions().setSupportsConstantAnalysis(!isUsingCliCompiler)
        spec.getCompileOptions().previousCompilationDataFile = this.previousCompilationData

        var compiler: Compiler<JavaCompileSpec?>? = createCompiler()
        compiler = makeIncremental(inputs, compiler as CleaningJavaCompiler<JavaCompileSpec?>?, this.stableSources)
        performCompilation(spec, compiler)
    }

    private fun makeIncremental(inputs: InputChanges, compiler: CleaningJavaCompiler<JavaCompileSpec?>?, stableSources: FileCollection): Compiler<JavaCompileSpec?>? {
        val sources = stableSources.getAsFileTree()
        return this.incrementalCompilerFactory.makeIncremental<JavaCompileSpec?>(
            compiler,
            sources,
            createRecompilationSpec(inputs, sources)
        )
    }

    private fun createRecompilationSpec(inputs: InputChanges, sources: FileTree?): JavaRecompilationSpecProvider {
        return JavaRecompilationSpecProvider(
            this.deleter,
            getServices().get<FileOperations?>(FileOperations::class.java),
            sources,
            inputs.isIncremental(),
            Iterable { inputs.getFileChanges(this.stableSources).iterator() }
        )
    }

    private fun isUsingCliCompiler(spec: DefaultJavaCompileSpec): Boolean {
        return CommandLineJavaCompileSpec::class.java.isAssignableFrom(spec.javaClass)
    }

    private fun performFullCompilation(spec: DefaultJavaCompileSpec) {
        val compiler: Compiler<JavaCompileSpec?>?
        spec.setSourceFiles(this.stableSources)
        compiler = createCompiler()
        performCompilation(spec, compiler)
    }

    fun createCompiler(): CleaningJavaCompiler<JavaCompileSpec?> {
        val javaCompiler = createToolchainCompiler<JavaCompileSpec?>()
        return CleaningJavaCompiler<JavaCompileSpec?>(javaCompiler, getOutputs(), this.deleter)
    }

    private fun <T : CompileSpec?> createToolchainCompiler(): Compiler<T?> {
        return Compiler { spec: T? ->
            val compiler = this.javaCompiler.get() as DefaultToolchainJavaCompiler
            compiler.execute<T?>(spec)
        }
    }

    @get:OutputFile
    protected val previousCompilationData: File
        /**
         * The previous compilation analysis. Internal use only.
         *
         * @since 7.1
         */
        get() {
            if (previousCompilationDataFile == null) {
                previousCompilationDataFile = File(this.temporaryDirWithoutCreating, "previous-compilation-data.bin")
            }
            return previousCompilationDataFile
        }

    private fun performCompilation(spec: JavaCompileSpec?, compiler: Compiler<JavaCompileSpec?>?) {
        val result = CompileJavaBuildOperationReportingCompiler(this, compiler, getServices().get<BuildOperationRunner?>(BuildOperationRunner::class.java)).execute(spec)
        setDidWork(result.getDidWork())
    }

    @VisibleForTesting
    fun createSpec(): DefaultJavaCompileSpec {
        validateForkOptionsMatchToolchain()
        val sourcesRoots: MutableList<File?> = CompilationSourceDirs.inferSourceRoots(this.stableSources.getAsFileTree() as FileTreeInternal)
        val javaModuleDetector: JavaModuleDetector = this.javaModuleDetector
        val isModule: Boolean = JavaModuleDetector.isModuleSource(this.modularity.getInferModulePath().get(), sourcesRoots)
        val isSourcepathUserDefined = getOptions()!!.sourcepath != null && !getOptions()!!.sourcepath!!.isEmpty()

        val spec: DefaultJavaCompileSpec = DefaultJavaCompileSpecFactory(getOptions(), this.toolchain).create()!!

        spec.setDestinationDir(destinationDirectory.getAsFile().get())
        spec.setWorkingDir(this.projectLayout.getProjectDirectory().getAsFile())
        spec.setTempDir(getTemporaryDir())
        spec.setCompileClasspath(ImmutableList.copyOf(javaModuleDetector.inferClasspath(isModule, getClasspath())))
        spec.setModulePath(ImmutableList.copyOf(javaModuleDetector.inferModulePath(isModule, getClasspath())))

        if (isModule && !isSourcepathUserDefined) {
            getOptions()!!.sourcepath = this.projectLayout.files(sourcesRoots)
        }
        spec.setAnnotationProcessorPath(if (getOptions()!!.annotationProcessorPath == null) ImmutableList.of<File?>() else ImmutableList.copyOf<File?>(getOptions()!!.annotationProcessorPath))
        configureCompileOptions(spec)
        spec.setSourcesRoots(sourcesRoots)

        if (!this.isToolchainCompatibleWithJava8) {
            spec.getCompileOptions().headerOutputDirectory = null
        }
        return spec
    }

    private fun validateForkOptionsMatchToolchain() {
        if (!getOptions()!!.isFork) {
            return
        }

        val javaCompilerTool = this.javaCompiler.get()
        val toolchainJavaHome = javaCompilerTool.getMetadata().getInstallationPath().getAsFile()

        val forkOptions: ForkOptions = getOptions()!!.forkOptions!!
        @Suppress("deprecation") val customJavaHome = forkOptions.javaHome
        if (customJavaHome != null) {
            JavaExecutableUtils.validateMatchingFiles(
                customJavaHome, "Toolchain from `javaHome` property on `ForkOptions`",
                toolchainJavaHome, "toolchain from `javaCompiler` property"
            )
        }

        val customExecutablePath = forkOptions.executable
        if (customExecutablePath != null) {
            // We do not match the custom executable against the compiler executable from the toolchain (javac),
            // because the custom executable can be set to the path of another tool in the toolchain such as a launcher (java).
            val customExecutableJavaHome = JavaExecutableUtils.resolveJavaHomeOfExecutable(customExecutablePath)
            JavaExecutableUtils.validateMatchingFiles(
                customExecutableJavaHome, "Toolchain from `executable` property on `ForkOptions`",
                toolchainJavaHome, "toolchain from `javaCompiler` property"
            )
        }
    }

    private val isToolchainCompatibleWithJava8: Boolean
        get() = this.toolchain.getLanguageVersion().canCompileOrRun(8)

    @get:Input
    val javaVersion: JavaVersion?
        get() = JavaVersion.toVersion(this.toolchain.getLanguageVersion().asInt())

    private fun configureCompileOptions(spec: DefaultJavaCompileSpec) {
        if (getOptions()!!.release.isPresent()) {
            spec.setRelease(getOptions()!!.release.get())
        } else {
            val toolchainVersion = JavaVersion.toVersion(this.toolchain.getLanguageVersion().asInt()).toString()
            var sourceCompatibility = sourceCompatibility
            // Compatibility can be null if no convention was configured, e.g. when JavaBasePlugin is not applied
            if (sourceCompatibility == null) {
                sourceCompatibility = toolchainVersion
            }

            var targetCompatibility = targetCompatibility
            if (targetCompatibility == null) {
                targetCompatibility = sourceCompatibility
            }

            spec.setSourceCompatibility(sourceCompatibility)
            spec.setTargetCompatibility(targetCompatibility)
        }
        spec.setCompileOptions(getOptions()!!)
    }

    private val toolchain: JavaInstallationMetadata
        get() = this.javaCompiler.get().getMetadata()

    private val temporaryDirWithoutCreating: File?
        get() =// Do not create the temporary folder, since that causes problems.
            getServices().get<TemporaryFileProvider?>(TemporaryFileProvider::class.java)!!.newTemporaryFile(getName())

    /**
     * Returns the compilation options.
     *
     * @return The compilation options.
     */
    @Nested
    abstract override fun getOptions(): CompileOptions?

    @CompileClasspath
    @Incremental
    @ToBeReplacedByLazyProperty
    override fun getClasspath(): FileCollection? {
        return super.classpath
    }

    @get:Inject
    protected abstract val objectFactory: ObjectFactory

    @get:Inject
    protected abstract val propertyFactory: PropertyFactory?

    @get:Inject
    protected abstract val javaToolchainService: JavaToolchainService

    @get:Inject
    protected abstract val providerFactory: ProviderFactory?

    @get:Inject
    protected abstract val incrementalCompilerFactory: IncrementalCompilerFactory?

    @get:Inject
    protected abstract val javaModuleDetector: JavaModuleDetector

    @get:Inject
    protected abstract val deleter: Deleter?

    @get:Inject
    protected abstract val projectLayout: ProjectLayout?
}
