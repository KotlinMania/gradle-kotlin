/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.testkit.runner.internal

import org.apache.commons.io.output.WriterOutputStream
import org.gradle.api.Action
import org.gradle.api.internal.file.temp.DefaultTemporaryFileProvider
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.Factory
import org.gradle.internal.FileUtils
import org.gradle.internal.SystemProperties
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.InvalidRunnerConfigurationException
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.gradle.testkit.runner.UnexpectedBuildSuccess
import org.gradle.testkit.runner.internal.io.SynchronizedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Writer
import java.net.URI
import java.nio.charset.Charset
import java.util.Arrays
import java.util.Collections
import kotlin.Exception
import kotlin.Suppress
import kotlin.requireNotNull

class DefaultGradleRunner internal constructor(private val gradleExecutor: GradleExecutor, var testKitDirProvider: TestKitDirProvider) : GradleRunner() {
    private var gradleProvider: GradleProvider? = null
    private var projectDirectory: File? = null
    private var buildArguments = mutableListOf<String?>()
    var jvmArguments: MutableList<String?> = mutableListOf<String?>()
        private set
    private var classpath: ClassPath = ClassPath.EMPTY
    private var debug: Boolean
    private var standardOutput: OutputStream? = null
    private var standardError: OutputStream? = null
    private var standardInput: InputStream? = null
    private var forwardingSystemStreams = false
    private var environmentVariables: MutableMap<String?, String?>? = null

    constructor() : this(ToolingApiGradleExecutor(), calculateTestKitDirProvider(SystemProperties.getInstance()))

    init {
        this.debug = java.lang.Boolean.getBoolean(DEBUG_SYS_PROP)
    }

    override fun withGradleVersion(versionNumber: String?): GradleRunner {
        this.gradleProvider = GradleProvider.version(versionNumber)
        return this
    }

    override fun withGradleInstallation(installation: File?): GradleRunner {
        this.gradleProvider = GradleProvider.installation(installation)
        return this
    }

    override fun withGradleDistribution(distribution: URI?): GradleRunner {
        this.gradleProvider = GradleProvider.uri(distribution)
        return this
    }

    override fun withTestKitDir(testKitDir: File?): DefaultGradleRunner {
        validateArgumentNotNull(testKitDir, "testKitDir")
        this.testKitDirProvider = ConstantTestKitDirProvider(testKitDir)
        return this
    }

    fun withJvmArguments(jvmArguments: MutableList<String?>): DefaultGradleRunner {
        this.jvmArguments = Collections.unmodifiableList<String?>(ArrayList<String?>(jvmArguments))
        return this
    }

    fun withJvmArguments(vararg jvmArguments: String?): DefaultGradleRunner {
        return withJvmArguments(Arrays.asList<String?>(*jvmArguments))
    }

    override val projectDir: File?
        get() = projectDirectory

    override fun withProjectDir(projectDir: File?): DefaultGradleRunner {
        this.projectDirectory = projectDir
        return this
    }

    override val arguments: MutableList<String?>?
        get() = buildArguments

    override fun withArguments(arguments: MutableList<String?>?): DefaultGradleRunner {
        validateArgumentNotNull(arguments, "arguments")
        this.buildArguments = Collections.unmodifiableList<String?>(ArrayList<String?>(arguments!!))
        return this
    }

    override fun withArguments(vararg arguments: String?): DefaultGradleRunner {
        return withArguments(Arrays.asList<String?>(*arguments))
    }

    override val pluginClasspath: MutableList<out File?>?
        get() = classpath.getAsFiles()

    override fun withPluginClasspath(): GradleRunner {
        this.classpath = DefaultClassPath.of(PluginUnderTestMetadataReading.readImplementationClasspath().filterNotNull())
        return this
    }

    override fun withPluginClasspath(classpath: Iterable<out File?>?): GradleRunner {
        validateArgumentNotNull(classpath, "classpath")
        val f: MutableList<File> = ArrayList<File>()
        for (file in classpath!!) {
            // These objects are going across the wire.
            // 1. Convert any subclasses back to File in case the subclass isn't available in Gradle.
            // 2. Make them absolute here to deal with a different root at the server
            f.add(File(file!!.absolutePath))
        }
        if (!f.isEmpty()) {
            this.classpath = DefaultClassPath.of(f)
        }
        return this
    }

    override val isDebug: Boolean
        get() = debug

    override fun withDebug(flag: Boolean): GradleRunner {
        this.debug = flag
        return this
    }

    override val environment: MutableMap<String?, String?>?
        get() = environmentVariables

    override fun withEnvironment(environment: MutableMap<String?, String?>?): GradleRunner {
        this.environmentVariables = environment
        return this
    }

    override fun forwardStdOutput(writer: Writer?): GradleRunner {
        if (forwardingSystemStreams) {
            forwardingSystemStreams = false
            this.standardError = null
        }
        validateArgumentNotNull(writer, "standardOutput")
        this.standardOutput = toOutputStream(writer)
        return this
    }

    override fun forwardStdError(writer: Writer?): GradleRunner {
        if (forwardingSystemStreams) {
            forwardingSystemStreams = false
            this.standardOutput = null
        }
        validateArgumentNotNull(writer, "standardError")
        this.standardError = toOutputStream(writer)
        return this
    }

    override fun forwardOutput(): GradleRunner {
        forwardingSystemStreams = true
        val systemOut: OutputStream = SynchronizedOutputStream(System.out)
        this.standardOutput = systemOut
        this.standardError = systemOut
        return this
    }

    fun withStandardInput(standardInput: InputStream?): GradleRunner {
        this.standardInput = standardInput
        return this
    }

    private fun validateArgumentNotNull(argument: Any?, argumentName: String?) {
        requireNotNull(argument) { String.format("%s argument cannot be null", argumentName) }
    }

    override fun build(): BuildResult {
        return run(Action { gradleExecutionResult: GradleExecutionResult? ->
            if (!gradleExecutionResult!!.isSuccessful) {
                throw UnexpectedBuildFailure(createDiagnosticsMessage("Unexpected build execution failure", gradleExecutionResult), Companion.createBuildResult(gradleExecutionResult))
            }
        })
    }

    override fun buildAndFail(): BuildResult {
        return run(Action { gradleExecutionResult: GradleExecutionResult? ->
            if (gradleExecutionResult!!.isSuccessful) {
                throw UnexpectedBuildSuccess(createDiagnosticsMessage("Unexpected build execution success", gradleExecutionResult), Companion.createBuildResult(gradleExecutionResult))
            }
        })
    }

    override fun run(): BuildResult {
        return run(Action { gradleExecutionResult: GradleExecutionResult? -> })
    }

    fun createDiagnosticsMessage(trailingMessage: String?, gradleExecutionResult: GradleExecutionResult): String {
        val lineBreak = SystemProperties.getInstance().getLineSeparator()
        val message = StringBuilder()
        message.append(trailingMessage)
        message.append(" in ")
        message.append(projectDir!!.absolutePath)
        message.append(" with arguments ")
        message.append(arguments)

        var output: String?
        try {
            output = gradleExecutionResult.outputSource.asCharSource(Charset.defaultCharset()).read()
        } catch (e: IOException) {
            output = "<Error fetching output: " + e.message + ">"
        }

        if (output != null && !output.isEmpty()) {
            message.append(lineBreak)
            message.append(lineBreak)
            message.append("Output:")
            message.append(lineBreak)
            message.append(output)
        }

        return message.toString()
    }

    private fun run(resultVerification: Action<GradleExecutionResult?>): BuildResult {
        if (projectDirectory == null) {
            throw InvalidRunnerConfigurationException("Please specify a project directory before executing the build")
        }

        if (environmentVariables != null && debug) {
            throw InvalidRunnerConfigurationException(
                "Debug mode is not allowed when environment variables are specified. " +
                        "Debug mode runs 'in process' but we need to fork a separate process to pass environment variables. " +
                        "To run with debug mode, please remove environment variables."
            )
        }

        val testKitDir = createTestKitDir(testKitDirProvider)

        val effectiveDistribution = (if (gradleProvider == null) org.gradle.testkit.runner.internal.DefaultGradleRunner.Companion.findGradleInstallFromGradleRunner() else gradleProvider)!!

        val effectiveArguments: MutableList<String?> = ArrayList<String?>()
        var effectiveEnvironment: MutableMap<String?, String?>? = HashMap<String?, String?>()

        if (current()!!.isWindows) {
            // When using file system watching in Windows tests it becomes harder to delete the project directory,
            // since file system watching on Windows adds a lock on the watched directory, which is currently the project directory.
            // After deleting the contents of the watched directory, Gradle will stop watching the directory and release the file lock.
            // That may require a retry to delete the watched directory.
            // To avoid those problems for TestKit tests on Windows, we disable file system watching there.
            effectiveArguments.add("-D" + StartParameterBuildOptions.WatchFileSystemOption.GRADLE_PROPERTY + "=false")
            // Without the SystemRoot environment variable been defined, Gradle Runner doesn't work on Windows when requiring network
            // connections.
            effectiveEnvironment!!.put("SystemRoot", System.getenv("SystemRoot"))
        }

        effectiveArguments.addAll(buildArguments)
        if (environmentVariables != null) {
            effectiveEnvironment!!.putAll(environmentVariables!!)
        } else {
            // environment can be null, which means that all the existing defined environment variables are used instead
            effectiveEnvironment = null
        }

        val execResult = gradleExecutor.run(
            GradleExecutionParameters(
                effectiveDistribution,
                testKitDir,
                projectDirectory,
                effectiveArguments,
                jvmArguments,
                classpath,
                debug,
                standardOutput,
                standardError,
                standardInput,
                effectiveEnvironment
            )
        )!!

        resultVerification.execute(execResult)
        return createBuildResult(execResult)
    }

    private fun createTestKitDir(testKitDirProvider: TestKitDirProvider): File {
        val dir = testKitDirProvider.dir!!
        if (dir.isDirectory) {
            if (!dir.canWrite()) {
                throw InvalidRunnerConfigurationException("Unable to write to test kit directory: " + dir.absolutePath)
            }
            return dir
        } else if (dir.exists() && !dir.isDirectory) {
            throw InvalidRunnerConfigurationException("Unable to use non-directory as test kit directory: " + dir.absolutePath)
        } else if (dir.mkdirs() || dir.isDirectory) {
            return dir
        } else {
            throw InvalidRunnerConfigurationException("Unable to create test kit directory: " + dir.absolutePath)
        }
    }

    companion object {
        const val TEST_KIT_DIR_SYS_PROP: String = "org.gradle.testkit.dir"
        const val DEBUG_SYS_PROP: String = "org.gradle.testkit.debug"

        private fun calculateTestKitDirProvider(systemProperties: SystemProperties): TestKitDirProvider {
            return systemProperties.withSystemProperties(object : Factory<TestKitDirProvider> {
                override fun create(): TestKitDirProvider {
                    if (System.getProperties().containsKey(TEST_KIT_DIR_SYS_PROP)) {
                        return ConstantTestKitDirProvider(File(System.getProperty(TEST_KIT_DIR_SYS_PROP)))
                    } else {
                        val temporaryFileProvider: TemporaryFileProvider = DefaultTemporaryFileProvider(object : Factory<File> {
                            override fun create(): File {
                                var rootTmpDir = SystemProperties.getInstance().getWorkerTmpDir()
                                if (rootTmpDir == null) {
                                    @Suppress("deprecation") val javaIoTmpDir = SystemProperties.getInstance().getJavaIoTmpDir()
                                    rootTmpDir = javaIoTmpDir
                                }
                                return FileUtils.canonicalize(File(rootTmpDir!!))
                            }
                        })
                        return ConstantTestKitDirProvider(temporaryFileProvider.newTemporaryFile(".gradle-test-kit"))
                    }
                }
            })
        }

        private fun toOutputStream(standardOutput: Writer?): OutputStream? {
            try {
                return WriterOutputStream.builder().setWriter(standardOutput).get()
            } catch (e: IOException) {
                throw throwAsUncheckedException(e)
            }
        }

        private fun createBuildResult(execResult: GradleExecutionResult): BuildResult {
            return FeatureCheckBuildResult(
                execResult.buildOperationParameters!!,
                execResult.outputSource,
                execResult.tasks!!
            )
        }

        private fun findGradleInstallFromGradleRunner(): GradleProvider {
            val gradleInstallation = CurrentGradleInstallation.get()
            if (gradleInstallation == null) {
                var messagePrefix = "Could not find a Gradle installation to use based on the location of the GradleRunner class"
                try {
                    val classpathForClass = ClasspathUtil.getClasspathForClass(GradleRunner::class.java)
                    messagePrefix += ": " + classpathForClass.getAbsolutePath()
                } catch (ignore: Exception) {
                    // ignore
                }
                throw InvalidRunnerConfigurationException(messagePrefix + ". Please specify a Gradle runtime to use via GradleRunner.withGradleVersion() or similar.")
            }
            return GradleProvider.installation(gradleInstallation.getGradleHome())
        }
    }
}
