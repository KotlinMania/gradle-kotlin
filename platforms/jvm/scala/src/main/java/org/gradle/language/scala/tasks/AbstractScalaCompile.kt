/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.language.scala.tasks

import com.google.common.collect.ImmutableList
import com.google.common.io.Files
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.JavaVersion.Companion.current
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.api.Transformer
import org.gradle.api.file.FileTree
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler
import org.gradle.api.internal.tasks.compile.CompilerForkUtils.doNotCacheIfForkingViaExecutable
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec.setAnnotationProcessorPath
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setCompileClasspath
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setDestinationDir
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setSourceCompatibility
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setTargetCompatibility
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setTempDir
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setWorkingDir
import org.gradle.api.internal.tasks.compile.HasCompileOptions
import org.gradle.api.internal.tasks.scala.DefaultScalaJavaJointCompileSpec
import org.gradle.api.internal.tasks.scala.MinimalScalaCompileOptions
import org.gradle.api.internal.tasks.scala.ScalaCompileSpec
import org.gradle.api.internal.tasks.scala.ScalaJavaJointCompileSpec
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.scala.ScalaCompileOptions
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.buildevents.BuildStartedTime
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.util.internal.GFileUtils
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import javax.inject.Inject

/**
 * An abstract Scala compile task sharing common functionality for compiling scala.
 */
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
abstract class AbstractScalaCompile @Incubating protected constructor() : AbstractCompile(), HasCompileOptions {
    /**
     * Returns the Scala compilation options.
     */
    @get:Nested
    open val scalaCompileOptions: BaseScalaCompileOptions

    /**
     * Constructor.
     *
     * @since 7.6
     */
    init {
        val objectFactory = this.objectFactory
        this.scalaCompileOptions = objectFactory.newInstance<ScalaCompileOptions>(ScalaCompileOptions::class.java)

        val javaToolchainService = this.javaToolchainService
        this.javaLauncher.convention(javaToolchainService.launcherFor(Action { it: JavaToolchainSpec? -> }))

        doNotCacheIfForkingViaExecutable(this.options, getOutputs())
    }

    @get:Nested
    abstract val options: CompileOptions?

    protected abstract fun getCompiler(spec: ScalaJavaJointCompileSpec?): Compiler<ScalaJavaJointCompileSpec?>

    @TaskAction
    fun compile() {
        val spec = createSpec()
        configureIncrementalCompilation(spec)
        var compiler = getCompiler(spec)
        if (this.isNonIncrementalCompilation) {
            compiler = CleaningJavaCompiler<ScalaJavaJointCompileSpec?>(compiler, getOutputs(), this.deleter)
        }
        compiler.execute(spec)
    }

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher?>?

    private val isNonIncrementalCompilation: Boolean
        get() {
            val analysisFile = this.scalaCompileOptions.incrementalOptions.analysisFile.getAsFile().get()
            if (!analysisFile.exists()) {
                LOGGER!!.info("Zinc is doing a full recompile since the analysis file doesn't exist")
                return true
            }
            return false
        }

    @get:Internal
    protected val toolchain: JavaInstallationMetadata
        get() = this.javaLauncher.map<JavaInstallationMetadata>(Transformer { obj: JavaLauncher? -> obj!!.metadata }).get()

    protected open fun createSpec(): ScalaJavaJointCompileSpec {
        val javaExecutable = this.javaLauncher.get().getExecutablePath().getAsFile()
        val spec = DefaultScalaJavaJointCompileSpec(javaExecutable)
        spec.setSourceFiles(getSource().getFiles())
        spec.setDestinationDir(destinationDirectory.getAsFile().get())
        spec.setWorkingDir(this.projectLayout.getProjectDirectory().getAsFile())
        spec.setTempDir(getTemporaryDir())
        val effectiveClasspath: MutableList<File?>?
        if (scalaCompileOptions.keepAliveMode.get() == KeepAliveMode.DAEMON) {
            effectiveClasspath = this.cachedClasspathTransformer.copyingTransform(DefaultClassPath.of(classpath)).getAsFiles()
        } else {
            effectiveClasspath = ImmutableList.copyOf<File?>(classpath)
        }
        spec.setCompileClasspath(effectiveClasspath)
        configureCompatibilityOptions(spec)
        spec.setCompileOptions(this.options)
        spec.setScalaCompileOptions(MinimalScalaCompileOptions(scalaCompileOptions))
        spec.setAnnotationProcessorPath(
            if (this.options.annotationProcessorPath == null)
                ImmutableList.of<File?>()
            else
                ImmutableList.copyOf<File?>(this.options.annotationProcessorPath)
        )
        spec.setBuildStartTimestamp(getServices().get<BuildStartedTime?>(BuildStartedTime::class.java)!!.getStartTime())
        return spec
    }

    private fun configureCompatibilityOptions(spec: DefaultScalaJavaJointCompileSpec) {
        val toolchainVersion = toVersion(this.toolchain.languageVersion.asInt()).toString()
        var sourceCompatibility = sourceCompatibility
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

    private fun configureIncrementalCompilation(spec: ScalaCompileSpec) {
        val incrementalOptions = scalaCompileOptions.incrementalOptions

        val analysisFile = incrementalOptions.analysisFile.getAsFile().get()
        val classpathBackupDir = incrementalOptions.classfileBackupDir.getAsFile().get()
        val globalAnalysisMap = resolveAnalysisMappingsForOtherProjects()
        spec.setAnalysisMap(globalAnalysisMap)
        spec.analysisFile = analysisFile
        spec.classfileBackupDir = classpathBackupDir

        // If this Scala compile is published into a jar, generate a analysis mapping file
        if (incrementalOptions.publishedCode.isPresent()) {
            val publishedCode = incrementalOptions.publishedCode.getAsFile().get()

            if (LOGGER!!.isDebugEnabled()) {
                LOGGER.debug("scala-incremental Analysis file: {}", analysisFile)
                LOGGER.debug("scala-incremental Classfile backup dir: {}", classpathBackupDir)
                LOGGER.debug("scala-incremental Published code: {}", publishedCode)
            }
            val analysisMapping = this.analysisMappingFile.getAsFile().get()
            GFileUtils.writeFile(publishedCode.getAbsolutePath() + "\n" + analysisFile.getAbsolutePath(), analysisMapping)
        }
        if (LOGGER!!.isDebugEnabled()) {
            LOGGER.debug("scala-incremental Analysis map: {}", globalAnalysisMap)
        }
    }

    private fun resolveAnalysisMappingsForOtherProjects(): MutableMap<File?, File?> {
        val analysisMap: MutableMap<File?, File?> = HashMap<File?, File?>()
        for (mapping in this.analysisFiles.getFiles()) {
            if (mapping.exists()) {
                try {
                    val lines: MutableList<String?> = Files.readLines(mapping, Charset.defaultCharset())
                    assert(lines.size == 2)
                    analysisMap.put(File(lines.get(0)), File(lines.get(1)))
                } catch (e: IOException) {
                    throw throwAsUncheckedException(e)
                }
            }
        }
        return analysisMap
    }

    /**
     * {@inheritDoc}
     */
    // Java source files are supported, too. Therefore, we should care about the relative path.
    @PathSensitive(PathSensitivity.RELATIVE)
    @ToBeReplacedByLazyProperty
    override fun getSource(): FileTree {
        return super.getSource()
    }

    @get:Input
    protected val jvmVersion: String
        /**
         * The Java major version of the JVM the Scala compiler is running on.
         *
         * @since 4.6
         */
        // We track this as an input since the Scala compiler output may depend on it.
        get() {
            if (this.javaLauncher.isPresent()) {
                return this.javaLauncher.get().getMetadata().getLanguageVersion().toString()
            }
            return current()!!.majorVersion
        }

    @get:Internal
    abstract val analysisFiles: ConfigurableFileCollection?

    @get:LocalState
    abstract val analysisMappingFile: RegularFileProperty?

    @get:Inject
    protected abstract val deleter: Deleter?

    @get:Inject
    protected abstract val projectLayout: ProjectLayout?

    @get:Inject
    protected abstract val objectFactory: ObjectFactory

    @get:Inject
    protected abstract val javaToolchainService: JavaToolchainService

    @get:Inject
    protected abstract val cachedClasspathTransformer: CachedClasspathTransformer?

    companion object {
        protected val LOGGER: Logger? = getLogger(AbstractScalaCompile::class.java)
    }
}
