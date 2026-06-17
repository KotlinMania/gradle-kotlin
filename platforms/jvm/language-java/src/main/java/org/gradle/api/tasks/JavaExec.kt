/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.tasks

import org.codehaus.plexus.util.cli.CommandLineUtils
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.Transformer
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.api.jvm.ModularitySpec
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.internal.JavaExecExecutableUtils
import org.gradle.api.tasks.options.Option
import org.gradle.internal.UncheckedException
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.internal.jvm.DefaultModularitySpec
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.internal.DefaultJavaLanguageVersion
import org.gradle.jvm.toolchain.internal.JavaExecutableUtils
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.ExecResult
import org.gradle.process.JavaDebugOptions
import org.gradle.process.JavaExecSpec
import org.gradle.process.JavaForkOptions
import org.gradle.process.ProcessForkOptions
import org.gradle.process.internal.DefaultJavaExecSpec
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Arrays
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * Executes a Java application in a child process.
 *
 *
 * Similar to [Exec], but starts a JVM with the given classpath and application class.
 *
 * <pre class='autoTested'>
 * plugins {
 * id 'java'
 * }
 *
 * task runApp(type: JavaExec) {
 * classpath = sourceSets.main.runtimeClasspath
 *
 * mainClass = 'package.Main'
 *
 * // arguments to pass to the application
 * args 'appArg1'
 * }
 *
 * // Using and creating an Executable Jar
 * jar {
 * manifest {
 * attributes('Main-Class': 'package.Main')
 * }
 * }
 *
 * task runExecutableJar(type: JavaExec) {
 * // Executable jars can have only _one_ jar on the classpath.
 * classpath = files(tasks.jar)
 *
 * // 'main' does not need to be specified
 *
 * // arguments to pass to the application
 * args 'appArg1'
 * }
 *
</pre> *
 *
 *
 * The process can be started in debug mode (see [.getDebug]) in an ad-hoc manner by supplying the `--debug-jvm` switch when invoking the build.
 * <pre>
 * gradle someJavaExecTask --debug-jvm
</pre> *
 *
 *
 * Also, debug configuration can be explicitly set in [.debugOptions]:
 * <pre>
 * task runApp(type: JavaExec) {
 * ...
 *
 * debugOptions {
 * enabled = true
 * port = 5566
 * server = true
 * suspend = false
 * }
 * }
</pre> *
 */
@DisableCachingByDefault(because = "Gradle would require more information to cache this task")
abstract class JavaExec : ConventionTask(), JavaExecSpec {
    private val javaExecSpec: DefaultJavaExecSpec
    private val modularity: ModularitySpec
    private val execResult: Property<ExecResult?>

    init {
        val objectFactory = this.objectFactory
        modularity = objectFactory.newInstance<DefaultModularitySpec>(DefaultModularitySpec::class.java)
        execResult = objectFactory.property<ExecResult?>(ExecResult::class.java)
        javaExecSpec = objectFactory.newInstance<DefaultJavaExecSpec>(DefaultJavaExecSpec::class.java)

        val jvmArgumentsConvention = this.providerFactory.provider<Iterable<String?>?>(Callable { this.jvmArgsConventionValue() })
        javaExecSpec.getJvmArguments().convention(jvmArgumentsConvention)

        javaExecSpec.getMainClass().convention(getMainClass()!!)
        javaExecSpec.getMainModule().convention(getMainModule()!!)
        javaExecSpec.getModularity().getInferModulePath().convention(modularity.getInferModulePath())

        val javaToolchainService = this.javaToolchainService
        val propertyFactory: PropertyFactory? = this.propertyFactory
        val javaLauncherConvention = this.providerFactory
            .provider<JavaToolchainSpec?>(Callable { JavaExecExecutableUtils.getExecutableOverrideToolchainSpec(this, propertyFactory) })
            .flatMap<JavaLauncher?>(Transformer { spec: JavaToolchainSpec? -> javaToolchainService.launcherFor(spec!!) })
            .orElse(javaToolchainService.launcherFor(Action { it: JavaToolchainSpec? -> }))
        this.javaLauncher.convention(javaLauncherConvention)
        this.javaLauncher.finalizeValueOnRead()

        // The task will only be up-to-date if it has outputs, those outputs are up-to-date,
        // and the Java launcher can be probed (i.e. javaLanguageVersion is not UNKNOWN)
        doNotTrackStateIf(
            "Java launcher cannot be probed",
            org.gradle.api.specs.Spec { task: TaskInternal? ->
                this.javaLauncher.map<JavaLanguageVersion?>(Transformer { launcher: JavaLauncher? -> launcher!!.metadata.languageVersion }).get() === DefaultJavaLanguageVersion.UNKNOWN
            })
    }

    @TaskAction
    fun exec() {
        validateExecutableMatchesToolchain()

        val jvmArgs = javaExecSpec.getJvmArguments().getOrNull()
        if (jvmArgs != null) {
            javaExecSpec.setExtraJvmArgs(jvmArgs)
        }

        val javaExecAction = this.execActionFactory.newJavaExecAction()
        javaExecSpec.copyTo(javaExecAction!!)
        val effectiveExecutable: String? = this.javaLauncher.get().executablePath.toString()
        javaExecAction.setExecutable(effectiveExecutable)

        execResult.set(javaExecAction.execute())
    }

    private fun validateExecutableMatchesToolchain() {
        val toolchainExecutable = this.javaLauncher.get().executablePath.getAsFile()
        val customExecutable = getExecutable()
        JavaExecutableUtils.validateExecutable(
            customExecutable, "Toolchain from `executable` property",
            toolchainExecutable, "toolchain from `javaLauncher` property"
        )
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getAllJvmArgs(): MutableList<String?> {
        return javaExecSpec.getAllJvmArgs()
    }

    /**
     * {@inheritDoc}
     */
    override fun setAllJvmArgs(arguments: MutableList<String?>?) {
        javaExecSpec.setAllJvmArgs(arguments)
    }

    /**
     * {@inheritDoc}
     */
    override fun setAllJvmArgs(arguments: Iterable<*>?) {
        javaExecSpec.setAllJvmArgs(arguments)
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getJvmArgs(): MutableList<String?> {
        return javaExecSpec.getJvmArguments().get()
    }

    /**
     * {@inheritDoc}
     */
    override fun setJvmArgs(arguments: MutableList<String>) {
        javaExecSpec.getJvmArguments().empty()
        jvmArgs(arguments)
    }

    /**
     * {@inheritDoc}
     */
    override fun setJvmArgs(arguments: Iterable<*>) {
        javaExecSpec.getJvmArguments().empty()
        jvmArgs(arguments)
    }

    /**
     * {@inheritDoc}
     */
    override fun jvmArgs(arguments: Iterable<*>): JavaExec {
        addJvmArguments(arguments)
        javaExecSpec.checkDebugConfiguration(arguments)
        return this
    }

    private fun addJvmArguments(arguments: Iterable<*>) {
        for (arg in arguments) {
            javaExecSpec.getJvmArguments().add(arg.toString())
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun jvmArgs(vararg arguments: Any?): JavaExec {
        jvmArgs(Arrays.asList<Any?>(*arguments))
        return this
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getSystemProperties(): MutableMap<String?, Any?>? {
        return javaExecSpec.getSystemProperties()
    }

    /**
     * {@inheritDoc}
     */
    override fun setSystemProperties(properties: MutableMap<String?, out Any?>?) {
        javaExecSpec.setSystemProperties(properties)
    }

    /**
     * {@inheritDoc}
     */
    override fun systemProperties(properties: MutableMap<String?, out Any?>): JavaExec {
        javaExecSpec.systemProperties(properties)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun systemProperty(name: String?, value: Any?): JavaExec {
        javaExecSpec.systemProperty(name, value)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getBootstrapClasspath(): FileCollection? {
        return javaExecSpec.getBootstrapClasspath()
    }

    /**
     * {@inheritDoc}
     */
    override fun setBootstrapClasspath(classpath: FileCollection?) {
        javaExecSpec.setBootstrapClasspath(classpath)
    }

    /**
     * {@inheritDoc}
     */
    override fun bootstrapClasspath(vararg classpath: Any?): JavaExec {
        javaExecSpec.bootstrapClasspath(*classpath)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getMinHeapSize(): String? {
        return javaExecSpec.getMinHeapSize()
    }

    /**
     * {@inheritDoc}
     */
    override fun setMinHeapSize(heapSize: String?) {
        javaExecSpec.setMinHeapSize(heapSize)
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getDefaultCharacterEncoding(): String? {
        return javaExecSpec.getDefaultCharacterEncoding()
    }

    /**
     * {@inheritDoc}
     */
    override fun setDefaultCharacterEncoding(defaultCharacterEncoding: String?) {
        javaExecSpec.setDefaultCharacterEncoding(defaultCharacterEncoding)
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getMaxHeapSize(): String? {
        return javaExecSpec.getMaxHeapSize()
    }

    /**
     * {@inheritDoc}
     */
    override fun setMaxHeapSize(heapSize: String?) {
        javaExecSpec.setMaxHeapSize(heapSize)
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getEnableAssertions(): Boolean {
        return javaExecSpec.getEnableAssertions()
    }

    /**
     * {@inheritDoc}
     */
    override fun setEnableAssertions(enabled: Boolean) {
        javaExecSpec.setEnableAssertions(enabled)
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getDebug(): Boolean {
        return javaExecSpec.getDebug()
    }

    /**
     * {@inheritDoc}
     */
    @Option(option = "debug-jvm", description = "Enable debugging for the process. The process is started suspended and listening on port 5005.")
    override fun setDebug(enabled: Boolean) {
        javaExecSpec.setDebug(enabled)
    }

    /**
     * {@inheritDoc}
     */
    override fun getDebugOptions(): JavaDebugOptions {
        return javaExecSpec.getDebugOptions()
    }

    /**
     * {@inheritDoc}
     */
    override fun debugOptions(action: Action<JavaDebugOptions?>) {
        javaExecSpec.debugOptions(action)
    }

    /**
     * {@inheritDoc}
     */
    abstract override fun getMainModule(): Property<String?>?


    /**
     * {@inheritDoc}
     */
    abstract override fun getMainClass(): Property<String?>?

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getArgs(): MutableList<String?>? {
        return javaExecSpec.getArgs()
    }

    /**
     * Parses an argument list from `args` and passes it to [.setArgs].
     *
     *
     *
     * The parser supports both single quote (`'`) and double quote (`"`) as quote delimiters.
     * For example, to pass the argument `foo bar`, use `"foo bar"`.
     *
     *
     *
     * Note: the parser does **not** support using backslash to escape quotes. If this is needed,
     * use the other quote delimiter around it.
     * For example, to pass the argument `'singly quoted'`, use `"'singly quoted'"`.
     *
     *
     * @param args Args for the main class. Will be parsed into an argument list.
     * @return this
     * @since 4.9
     */
    @Option(option = "args", description = "Command line arguments passed to the main class.")
    fun setArgsString(args: String?): JavaExec {
        try {
            return setArgs(Arrays.asList<String?>(*CommandLineUtils.translateCommandline(args)))
        } catch (e: Exception) {
            throw UncheckedException.throwAsUncheckedException(e)
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun setArgs(applicationArgs: MutableList<String?>?): JavaExec {
        javaExecSpec.setArgs(applicationArgs)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun setArgs(applicationArgs: Iterable<*>?): JavaExec {
        javaExecSpec.setArgs(applicationArgs)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun args(vararg args: Any?): JavaExec {
        javaExecSpec.args(*args)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun args(args: Iterable<*>?): JavaExecSpec {
        javaExecSpec.args(args)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getArgumentProviders(): MutableList<CommandLineArgumentProvider?>? {
        return javaExecSpec.getArgumentProviders()
    }

    /**
     * {@inheritDoc}
     */
    override fun setClasspath(classpath: FileCollection): JavaExec {
        javaExecSpec.setClasspath(classpath)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun classpath(vararg paths: Any?): JavaExec {
        javaExecSpec.classpath(*paths)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getClasspath(): FileCollection {
        return javaExecSpec.getClasspath()
    }

    /**
     * {@inheritDoc}
     */
    override fun getModularity(): ModularitySpec {
        return modularity
    }

    /**
     * {@inheritDoc}
     */
    override fun copyTo(options: JavaForkOptions): JavaExec {
        javaExecSpec.copyTo(options)
        return this
    }

    @get:ToBeReplacedByLazyProperty
    @get:Internal("covered by getJavaLauncher().getMetadata().getLanguageVersion()")
    val javaVersion: JavaVersion?
        /**
         * Returns the version of the Java executable specified by [.getJavaLauncher].
         *
         * @since 5.2
         */
        get() = JavaVersion.toVersion(this.javaLauncher.get().metadata.languageVersion.asInt())

    /**
     * {@inheritDoc}
     */
    @Internal("covered by getJavaVersion")
    @ToBeReplacedByLazyProperty
    override fun getExecutable(): String? {
        return javaExecSpec.getExecutable()
    }

    /**
     * {@inheritDoc}
     */
    override fun setExecutable(executable: String?) {
        javaExecSpec.setExecutable(executable)
    }

    /**
     * {@inheritDoc}
     */
    override fun setExecutable(executable: Any?) {
        javaExecSpec.setExecutable(executable)
    }

    /**
     * {@inheritDoc}
     */
    override fun executable(executable: Any?): JavaExec {
        javaExecSpec.executable(executable)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @ToBeReplacedByLazyProperty
    override fun getWorkingDir(): File? {
        return javaExecSpec.getWorkingDir()
    }

    /**
     * {@inheritDoc}
     */
    override fun setWorkingDir(dir: File?) {
        javaExecSpec.setWorkingDir(dir)
    }

    /**
     * {@inheritDoc}
     */
    override fun setWorkingDir(dir: Any?) {
        javaExecSpec.setWorkingDir(dir)
    }

    /**
     * {@inheritDoc}
     */
    override fun workingDir(dir: Any?): JavaExec {
        javaExecSpec.workingDir(dir)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @ToBeReplacedByLazyProperty
    override fun getEnvironment(): MutableMap<String?, Any?>? {
        return javaExecSpec.getEnvironment()
    }

    /**
     * {@inheritDoc}
     */
    override fun setEnvironment(environmentVariables: MutableMap<String?, *>?) {
        javaExecSpec.setEnvironment(environmentVariables!!)
    }

    /**
     * {@inheritDoc}
     */
    override fun environment(name: String?, value: Any?): JavaExec {
        javaExecSpec.environment(name, value)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun environment(environmentVariables: MutableMap<String?, *>?): JavaExec {
        javaExecSpec.environment(environmentVariables!!)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun copyTo(target: ProcessForkOptions): JavaExec {
        javaExecSpec.copyTo(target)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun setStandardInput(inputStream: InputStream?): JavaExec {
        javaExecSpec.setStandardInput(inputStream)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @ToBeReplacedByLazyProperty
    override fun getStandardInput(): InputStream? {
        return javaExecSpec.getStandardInput()
    }

    /**
     * {@inheritDoc}
     */
    override fun setStandardOutput(outputStream: OutputStream?): JavaExec {
        javaExecSpec.setStandardOutput(outputStream)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @ToBeReplacedByLazyProperty
    override fun getStandardOutput(): OutputStream? {
        return javaExecSpec.getStandardOutput()
    }

    /**
     * {@inheritDoc}
     */
    override fun setErrorOutput(outputStream: OutputStream?): JavaExec {
        javaExecSpec.setErrorOutput(outputStream)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @ToBeReplacedByLazyProperty
    override fun getErrorOutput(): OutputStream? {
        return javaExecSpec.getErrorOutput()
    }

    /**
     * {@inheritDoc}
     */
    override fun setIgnoreExitValue(ignoreExitValue: Boolean): JavaExecSpec {
        javaExecSpec.setIgnoreExitValue(ignoreExitValue)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @Input
    @ToBeReplacedByLazyProperty
    override fun isIgnoreExitValue(): Boolean {
        return javaExecSpec.isIgnoreExitValue()
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @ToBeReplacedByLazyProperty
    override fun getCommandLine(): MutableList<String?>? {
        return javaExecSpec.getCommandLine()
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getJvmArgumentProviders(): MutableList<CommandLineArgumentProvider?> {
        return javaExecSpec.getJvmArgumentProviders()
    }

    /**
     * {@inheritDoc}
     */
    override fun getJvmArguments(): ListProperty<String?> {
        return javaExecSpec.getJvmArguments()
    }

    @get:Internal
    val executionResult: Provider<ExecResult?>
        /**
         * Returns the result for the command run by this task. The provider has no value if this task has not been executed yet.
         *
         * @return A provider of the result.
         * @since 6.1
         */
        get() = execResult

    @JvmField
    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher?>?

    @get:Inject
    protected abstract val objectFactory: ObjectFactory

    @get:Inject
    protected abstract val propertyFactory: PropertyFactory?

    @get:Inject
    protected abstract val execActionFactory: ExecActionFactory?

    @get:Inject
    protected abstract val javaToolchainService: JavaToolchainService

    @get:Inject
    protected abstract val providerFactory: ProviderFactory?

    private fun jvmArgsConventionValue(): Iterable<String?> {
        val jvmArgs = getConventionMapping().getConventionValue<Iterable<String?>?>(null, "jvmArgs", false)
        return if (jvmArgs != null) jvmArgs else mutableListOf<String?>()
    }
}
