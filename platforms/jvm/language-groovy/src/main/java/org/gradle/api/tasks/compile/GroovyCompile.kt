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
package org.gradle.api.tasks.compile

import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.api.Transformer
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.FeaturePreviews
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler
import org.gradle.api.internal.tasks.compile.CommandLineJavaCompileSpec
import org.gradle.api.internal.tasks.compile.CompilationSourceDirs.Companion.inferSourceRoots
import org.gradle.api.internal.tasks.compile.CompilerForkUtils
import org.gradle.api.internal.tasks.compile.DefaultGroovyJavaJointCompileSpec
import org.gradle.api.internal.tasks.compile.DefaultGroovyJavaJointCompileSpecFactory
import org.gradle.api.internal.tasks.compile.GroovyCompilerFactory
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompileSpec
import org.gradle.api.internal.tasks.compile.HasCompileOptions
import org.gradle.api.internal.tasks.compile.MinimalGroovyCompileOptions
import org.gradle.api.internal.tasks.compile.incremental.IncrementalCompilerFactory
import org.gradle.api.internal.tasks.compile.incremental.recomp.GroovyRecompilationSpecProvider
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpecProvider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
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
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.util.internal.GFileUtils
import org.gradle.util.internal.IncubationLogger.incubatingFeatureUsed
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * Compiles Groovy source files, and optionally, Java source files.
 */
@CacheableTask
abstract class GroovyCompile : AbstractCompile(), HasCompileOptions {
    /**
     * Returns the classpath containing the version of Groovy to use for compilation.
     *
     * @return The classpath.
     */
    /**
     * Sets the classpath containing the version of Groovy to use for compilation.
     *
     * @param groovyClasspath The classpath. Must not be null.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Classpath
    var groovyClasspath: FileCollection? = null// Java source files are supported, too. Therefore, we should care about the relative path.

    /**
     * The sources for incremental change detection.
     *
     * @since 5.6
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    protected val stableSources: FileCollection

    /**
     * The sources for incremental change detection.
     *
     * @since 5.6
     */
    // Java source files are supported, too. Therefore, we should care about the relative path. get
    private var previousCompilationDataFile: File? = null

    init {
        this.stableSources = this.objectFactory.fileCollection().from(Callable { this.getSource() })

        getOptions()!!.setIncremental(false)
        getOptions()!!.incrementalAfterFailure.convention(true)

        this.javaLauncher.convention(this.javaToolchainService.launcherFor(Action { it: JavaToolchainSpec? -> }))

        if (!experimentalCompilationAvoidanceEnabled()) {
            this.astTransformationClasspath.from(Callable { this.getClasspath() })
        }

        CompilerForkUtils.doNotCacheIfForkingViaExecutable(getOptions(), getOutputs())
    }

    @CompileClasspath
    @Incremental
    @ToBeReplacedByLazyProperty
    override fun getClasspath(): FileCollection? {
        // Note that @CompileClasspath here is an approximation and must be fixed before de-incubating getAstTransformationClasspath()
        // See https://github.com/gradle/gradle/pull/9513
        return super.classpath
    }

    @get:Classpath
    abstract val astTransformationClasspath: ConfigurableFileCollection?

    private fun experimentalCompilationAvoidanceEnabled(): Boolean {
        return this.featureFlags.isEnabled(FeaturePreviews.Feature.GROOVY_COMPILATION_AVOIDANCE)
    }

    @TaskAction
    protected fun compile(inputChanges: InputChanges) {
        checkGroovyClasspathIsNonEmpty()
        warnIfCompileAvoidanceEnabled()
        val spec = createSpec()
        maybeDisableIncrementalCompilationAfterFailure(spec)
        val result = createCompiler(spec, inputChanges)!!.execute(spec)
        setDidWork(result.getDidWork())
    }

    private fun maybeDisableIncrementalCompilationAfterFailure(spec: GroovyJavaJointCompileSpec) {
        if (CommandLineJavaCompileSpec::class.java.isAssignableFrom(spec.javaClass)) {
            spec.compileOptions!!.setSupportsIncrementalCompilationAfterFailure(false)
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

    private fun warnIfCompileAvoidanceEnabled() {
        if (experimentalCompilationAvoidanceEnabled()) {
            incubatingFeatureUsed("Groovy compilation avoidance")
        }
    }

    private fun createCompiler(spec: GroovyJavaJointCompileSpec, inputChanges: InputChanges): Compiler<GroovyJavaJointCompileSpec?>? {
        val groovyCompilerFactory = this.groovyCompilerFactory
        val delegatingCompiler: Compiler<GroovyJavaJointCompileSpec?> = groovyCompilerFactory.newCompiler(spec)
        val cleaningGroovyCompiler = CleaningJavaCompiler<GroovyJavaJointCompileSpec?>(
            delegatingCompiler, getOutputs(),
            this.deleter
        )
        if (spec.incrementalCompilationEnabled()) {
            val factory = this.incrementalCompilerFactory
            return factory.makeIncremental<GroovyJavaJointCompileSpec?>(
                cleaningGroovyCompiler,
                this.stableSources.getAsFileTree(),
                createRecompilationSpecProvider(inputChanges)
            )
        } else {
            return cleaningGroovyCompiler
        }
    }

    private fun createRecompilationSpecProvider(inputChanges: InputChanges): RecompilationSpecProvider {
        val stableSources = this.stableSources
        return GroovyRecompilationSpecProvider(
            this.deleter,
            getServices().get<FileOperations?>(FileOperations::class.java),
            stableSources.getAsFileTree(),
            inputChanges.isIncremental(),
            Iterable { inputChanges.getFileChanges(stableSources).iterator() }
        )
    }

    private fun determineGroovyCompileClasspath(): FileCollection? {
        if (experimentalCompilationAvoidanceEnabled()) {
            return this.astTransformationClasspath.plus(getClasspath())
        } else {
            return getClasspath()
        }
    }

    private fun createSpec(): GroovyJavaJointCompileSpec {
        val spec = checkNotNull(
            DefaultGroovyJavaJointCompileSpecFactory(
                getOptions()!!,
                this.toolchain
            ).create()
        )
        val stableSourcesAsFileTree = this.stableSources.getAsFileTree() as FileTreeInternal
        val sourceRoots: MutableList<File?> = inferSourceRoots(stableSourcesAsFileTree)

        spec.setSourcesRoots(sourceRoots)
        spec.setSourceFiles(stableSourcesAsFileTree)
        spec.setDestinationDir(destinationDirectory.getAsFile().get())
        spec.setWorkingDir(this.projectLayout.getProjectDirectory().getAsFile())
        spec.setTempDir(getTemporaryDir())
        spec.setCompileClasspath(ImmutableList.copyOf<File?>(determineGroovyCompileClasspath()))
        configureCompatibilityOptions(spec)
        spec.setAnnotationProcessorPath(Lists.newArrayList<File?>(if (getOptions()!!.annotationProcessorPath == null) this.projectLayout.files() else getOptions()!!.annotationProcessorPath))
        spec.setGroovyClasspath(Lists.newArrayList<File?>(this.groovyClasspath))
        spec.setCompileOptions(getOptions()!!)
        spec.setGroovyCompileOptions(MinimalGroovyCompileOptions(this.groovyOptions!!))
        spec.getCompileOptions().setSupportsCompilerApi(true)
        if (getOptions()!!.isIncremental) {
            validateIncrementalCompilationOptions(sourceRoots, spec.annotationProcessingConfigured())
            spec.getCompileOptions().previousCompilationDataFile = this.previousCompilationData
        }
        if (spec.getGroovyCompileOptions()!!.stubDir == null) {
            val dir = File(getTemporaryDir(), "groovy-java-stubs")
            GFileUtils.mkdirs(dir)
            spec.getGroovyCompileOptions()!!.stubDir = dir
        }

        val executable = this.javaLauncher.get().getExecutablePath().getAsFile().getAbsolutePath()
        spec.getCompileOptions().forkOptions!!.executable = executable

        return spec
    }

    private fun configureCompatibilityOptions(spec: DefaultGroovyJavaJointCompileSpec) {
        val toolchainVersion = toVersion(this.toolchain.languageVersion.asInt()).toString()
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

    private val toolchain: JavaInstallationMetadata
        get() = this.javaLauncher.map<JavaInstallationMetadata>(Transformer { obj: JavaLauncher? -> obj!!.metadata }).get()

    private fun checkGroovyClasspathIsNonEmpty() {
        if (this.groovyClasspath!!.isEmpty()) {
            throw InvalidUserDataException(
                ("'" + getName() + ".groovyClasspath' must not be empty. If a Groovy compile dependency is provided, "
                        + "the 'groovy-base' plugin will attempt to configure 'groovyClasspath' automatically. Alternatively, you may configure 'groovyClasspath' explicitly.")
            )
        }
    }

    @get:Input
    protected val groovyCompilerJvmVersion: String
        /**
         * We need to track the Java version of the JVM the Groovy compiler is running on, since the Groovy compiler produces different results depending on it.
         *
         * This should be replaced by a property on the Groovy toolchain as soon as we model these.
         *
         * @since 4.0
         */
        get() = this.toolchain.languageVersion.toString()

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
    abstract val groovyOptions: GroovyCompileOptions?

    /**
     * Returns the options for Java compilation.
     *
     * @return The Java compile options. Never returns null.
     */
    @Nested
    abstract override fun getOptions(): CompileOptions?

    @JvmField
    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher?>?

    @get:Inject
    protected abstract val incrementalCompilerFactory: IncrementalCompilerFactory

    @get:Inject
    protected abstract val deleter: Deleter?

    @get:Inject
    protected abstract val projectLayout: ProjectLayout?

    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    @get:Inject
    protected abstract val groovyCompilerFactory: GroovyCompilerFactory

    @get:Inject
    protected abstract val featureFlags: FeatureFlags?

    @get:Inject
    protected abstract val javaToolchainService: JavaToolchainService?

    private val temporaryDirWithoutCreating: File?
        get() =// Do not create the temporary folder, since that causes problems.
            getServices().get<TemporaryFileProvider?>(TemporaryFileProvider::class.java)!!.newTemporaryFile(getName())

    companion object {
        private fun validateIncrementalCompilationOptions(sourceRoots: MutableList<File?>, annotationProcessingConfigured: Boolean) {
            if (sourceRoots.isEmpty()) {
                throw InvalidUserDataException("Unable to infer source roots. Incremental Groovy compilation requires the source roots. Change the configuration of your sources or disable incremental Groovy compilation.")
            }

            if (annotationProcessingConfigured) {
                throw InvalidUserDataException("Enabling incremental compilation and configuring Java annotation processors for Groovy compilation is not allowed. Disable incremental Groovy compilation or remove the Java annotation processor configuration.")
            }
        }
    }
}
