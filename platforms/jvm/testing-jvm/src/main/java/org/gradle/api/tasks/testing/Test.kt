/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.tasks.testing

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.StartParameter
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.JavaVersion
import org.gradle.api.Transformer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTreeElement
import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec
import org.gradle.api.internal.tasks.testing.TestExecutableUtils
import org.gradle.api.internal.tasks.testing.TestExecuter
import org.gradle.api.internal.tasks.testing.TestFramework
import org.gradle.api.internal.tasks.testing.detection.DefaultTestExecuter
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework
import org.gradle.api.internal.tasks.testing.results.serializable.OutputRanges
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework
import org.gradle.api.internal.tasks.testing.worker.TestWorker
import org.gradle.api.jvm.ModularitySpec
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.testing.junit.JUnitOptions
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.api.tasks.testing.testng.TestNGOptions
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.internal.Actions
import org.gradle.internal.Cast
import org.gradle.internal.UncheckedException
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.internal.jvm.DefaultModularitySpec
import org.gradle.internal.jvm.SupportedJavaVersions
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException
import org.gradle.internal.scan.UsedByScanPlugin
import org.gradle.internal.time.Clock
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.internal.JavaExecutableUtils
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaDebugOptions
import org.gradle.process.JavaForkOptions
import org.gradle.process.ProcessForkOptions
import org.gradle.util.internal.ConfigureUtil
import org.jspecify.annotations.NullMarked
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Callable
import java.util.stream.Collectors
import javax.inject.Inject

/**
 * Executes JUnit (3.8.x, 4.x or 5.x) or TestNG tests. Test are always run in (one or more) separate JVMs.
 *
 *
 *
 * The sample below shows various configuration options.
 *
 * <pre class='autoTested'>
 * plugins {
 * id("java-library") // adds 'test' task
 * }
 *
 * test {
 * // discover and execute JUnit4-based tests
 * useJUnit()
 *
 * // discover and execute TestNG-based tests
 * useTestNG()
 *
 * // discover and execute JUnit Platform-based tests
 * useJUnitPlatform()
 *
 * // set a system property for the test JVM(s)
 * systemProperty 'some.prop', 'value'
 *
 * // explicitly include or exclude tests
 * include 'org/foo/ **'
 * exclude 'org/boo/ **'
 *
 * // show standard out and standard error of the test JVM(s) on the console
 * testLogging.showStandardStreams = true
 *
 * // set heap size for the test JVM(s)
 * minHeapSize = "128m"
 * maxHeapSize = "512m"
 *
 * // set JVM arguments for the test JVM(s)
 * jvmArgs('-XX:MaxPermSize=256m')
 *
 * // listen to events in the test execution lifecycle
 * addTestListener(new TestListener() {
 * void beforeTest(TestDescriptor descriptor) {
 * logger.lifecycle("Running test: " + descriptor)
 * }
 * })
 *
 * // fail the 'test' task on the first test failure
 * failFast = true
 *
 * // skip an actual test execution
 * dryRun = true
 *
 * // listen to standard out and standard error of the test JVM(s)
 * addTestOutputListener { descriptor, event -&gt;
 * logger.lifecycle("Test: " + descriptor + " produced standard out/err: " + event.message )
 * }
 * }
</pre> *
 *
 *
 * The test process can be started in debug mode (see [.getDebug]) in an ad-hoc manner by supplying the `--debug-jvm` switch when invoking the build.
 * <pre>
 * gradle someTestTask --debug-jvm
</pre> *
 */
@NullMarked
@CacheableTask
abstract class Test : AbstractTestTask(), JavaForkOptions, PatternFilterable {
    private val forkOptions: JavaForkOptions

    /**
     * Returns the module path handling of this test task.
     *
     * @since 6.4
     */
    @get:Nested
    val modularity: ModularitySpec

    /**
     * Returns the directories for the compiled test sources.
     *
     * @return All test class directories to be used.
     * @since 4.0
     */
    /**
     * Sets the directories to scan for compiled test sources.
     *
     * Typically, this would be configured to use the output of a source set:
     * <pre class='autoTested'>
     * plugins {
     * id 'java'
     * }
     *
     * sourceSets {
     * integrationTest {
     * compileClasspath += main.output
     * runtimeClasspath += main.output
     * }
     * }
     *
     * task integrationTest(type: Test) {
     * // Runs tests from src/integrationTest
     * testClassesDirs = sourceSets.integrationTest.output.classesDirs
     * classpath = sourceSets.integrationTest.runtimeClasspath
     * }
    </pre> *
     *
     * @param testClassesDirs All test class directories to be used.
     * @since 4.0
     */
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var testClassesDirs: FileCollection
    private val patternSet: PatternFilterable

    /**
     * Returns the classpath to use to execute the tests.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Internal("captured by stableClasspath")
    var classpath: FileCollection
    private val stableClasspath: ConfigurableFileCollection

    /**
     * Returns the configured [TestFramework].
     *
     * @since 7.3
     */
    @get:Nested
    val testFrameworkProperty: Property<TestFramework>

    /**
     * Specifies whether test classes should be detected. When `true` the classes which match the include and exclude patterns are scanned for test classes, and any found are executed. When
     * `false` the classes which match the include and exclude patterns are executed.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isScanForTestClasses: Boolean = true

    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var forkEvery: Long = 0
        /**
         * Returns the maximum number of test classes to execute in a forked test process. The forked test process will be restarted when this limit is reached.
         *
         *
         *
         * By default, Gradle automatically uses a separate JVM when executing tests.
         *
         *  * A value of `0` (no limit) means to reuse the test process for all test classes. This is the default.
         *  * A value of `1` means that a new test process is started for **every** test class. **This is very expensive.**
         *  * A value of `N` means that a new test process is started after `N` test classes.
         *
         * This property can have a large impact on performance due to the cost of stopping and starting each test process. It is unusual for this property to be changed from the default.
         *
         * @return The maximum number of test classes to execute in a test process. Returns 0 when there is no maximum.
         */
        get() = if (getDebug()) 0 else field
        /**
         * Sets the maximum number of test classes to execute in a forked test process.
         *
         *
         * By default, Gradle automatically uses a separate JVM when executing tests, so changing this property is usually not necessary.
         *
         *
         * @param forkEvery The maximum number of test classes. Use 0 to specify no maximum.
         * @since 8.1
         */
        set(forkEvery) {
            require(forkEvery >= 0) { "Cannot set forkEvery to a value less than 0." }
            field = forkEvery
        }

    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var maxParallelForks: Int = 1
        /**
         * Returns the maximum number of test processes to start in parallel.
         *
         *
         *
         * By default, Gradle executes a single test class at a time.
         *
         *  * A value of `1` means to only execute a single test class in a single test process at a time. This is the default.
         *  * A value of `N` means that up to `N` test processes will be started to execute test classes. **This can improve test execution time by running multiple test classes in parallel.**
         *
         *
         * This property cannot exceed the value of max-workers for the current build. Gradle will also limit the number of started test processes across all [Test] tasks.
         *
         * @return The maximum number of forked test processes.
         */
        get() = if (getDebug()) 1 else field
        /**
         * Sets the maximum number of test processes to start in parallel.
         *
         *
         * By default, Gradle executes a single test class at a time but allows multiple [Test] tasks to run in parallel.
         *
         *
         * @param maxParallelForks The maximum number of forked test processes. Use 1 to disable parallel test execution for this task.
         */
        set(maxParallelForks) {
            require(maxParallelForks >= 1) { "Cannot set maxParallelForks to a value less than 1." }
            field = maxParallelForks
        }

    private var testExecuter: TestExecuter<JvmTestExecutionSpec>? = null

    init {
        val objectFactory = getObjectFactory()
        patternSet = this.patternSetFactory.createPatternSet()
        testClassesDirs = objectFactory.fileCollection()
        classpath = objectFactory.fileCollection()
        // Create a stable instance to represent the classpath, that takes care of conventions and mutations applied to the property
        stableClasspath = objectFactory.fileCollection()
        stableClasspath.from(Callable { this.classpath } as Callable<Any?>)
        forkOptions = this.forkOptionsFactory.newDecoratedJavaForkOptions()
        forkOptions.setEnableAssertions(true)
        forkOptions.setExecutable(null)
        modularity = objectFactory.newInstance<DefaultModularitySpec>(DefaultModularitySpec::class.java)
        this.javaLauncher.convention(createJavaLauncherConvention())
        this.javaLauncher.finalizeValueOnRead()
        this.dryRun.convention(false)
        this.testFrameworkProperty = objectFactory.property<TestFramework>(TestFramework::class.java).convention(
            objectFactory.newInstance<JUnitTestFramework>(
                JUnitTestFramework::class.java, this.getFilter(), this.getTemporaryDirFactory(),
                this.dryRun
            )
        )
    }

    private fun createJavaLauncherConvention(): Provider<JavaLauncher> {
        val javaToolchainService = this.javaToolchainService
        val propertyFactory = this.propertyFactory
        val executableOverrideToolchainSpec = this.providerFactory.provider<JavaToolchainSpec>(Callable { TestExecutableUtils.getExecutableToolchainSpec(this@Test, propertyFactory) })

        return executableOverrideToolchainSpec
            .flatMap<JavaLauncher>(Transformer { spec: JavaToolchainSpec? -> javaToolchainService.launcherFor(spec!!) } as Transformer<Provider<JavaLauncher?>?, JavaToolchainSpec?>)
            .orElse(javaToolchainService.launcherFor(Action { javaToolchainSpec: JavaToolchainSpec? -> }))
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @ToBeReplacedByLazyProperty
    override fun getWorkingDir(): File {
        return forkOptions.getWorkingDir()
    }

    /**
     * {@inheritDoc}
     */
    override fun setWorkingDir(dir: File) {
        forkOptions.setWorkingDir(dir)
    }

    /**
     * {@inheritDoc}
     */
    override fun setWorkingDir(dir: Any) {
        forkOptions.setWorkingDir(dir)
    }

    /**
     * {@inheritDoc}
     */
    override fun workingDir(dir: Any): Test {
        forkOptions.workingDir(dir)
        return this
    }

    @get:ToBeReplacedByLazyProperty
    @get:Input
    val javaVersion: JavaVersion
        /**
         * Returns the version of Java used to run the tests based on the [JavaLauncher] specified by [.getJavaLauncher],
         * or the executable specified by [.getExecutable] if the `JavaLauncher` is not present.
         *
         * @since 3.3
         */
        get() = JavaVersion.toVersion(this.javaLauncher.get().metadata.languageVersion.asInt())

    /**
     * {@inheritDoc}
     */
    @Internal
    @ToBeReplacedByLazyProperty
    override fun getExecutable(): String {
        return forkOptions.getExecutable()
    }

    /**
     * {@inheritDoc}
     */
    override fun executable(executable: Any): Test {
        forkOptions.executable(executable)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun setExecutable(executable: String) {
        forkOptions.setExecutable(executable)
    }

    /**
     * {@inheritDoc}
     */
    override fun setExecutable(executable: Any) {
        forkOptions.setExecutable(executable)
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getSystemProperties(): MutableMap<String, Any?> {
        return forkOptions.getSystemProperties()
    }

    /**
     * {@inheritDoc}
     */
    override fun setSystemProperties(properties: MutableMap<String, out Any?>) {
        forkOptions.setSystemProperties(properties)
    }

    /**
     * {@inheritDoc}
     */
    override fun systemProperties(properties: MutableMap<String, out Any?>): Test {
        forkOptions.systemProperties(properties)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun systemProperty(name: String, value: Any?): Test {
        forkOptions.systemProperty(name, value)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getBootstrapClasspath(): FileCollection {
        return forkOptions.getBootstrapClasspath()
    }

    /**
     * {@inheritDoc}
     */
    override fun setBootstrapClasspath(classpath: FileCollection) {
        forkOptions.setBootstrapClasspath(classpath)
    }

    /**
     * {@inheritDoc}
     */
    override fun bootstrapClasspath(vararg classpath: Any?): Test {
        forkOptions.bootstrapClasspath(*classpath)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getMinHeapSize(): String? {
        return forkOptions.getMinHeapSize()
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getDefaultCharacterEncoding(): String? {
        return forkOptions.getDefaultCharacterEncoding()
    }

    /**
     * {@inheritDoc}
     */
    override fun setDefaultCharacterEncoding(defaultCharacterEncoding: String?) {
        forkOptions.setDefaultCharacterEncoding(defaultCharacterEncoding)
    }

    /**
     * {@inheritDoc}
     */
    override fun setMinHeapSize(heapSize: String?) {
        forkOptions.setMinHeapSize(heapSize)
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getMaxHeapSize(): String? {
        return forkOptions.getMaxHeapSize()
    }

    /**
     * {@inheritDoc}
     */
    override fun setMaxHeapSize(heapSize: String?) {
        forkOptions.setMaxHeapSize(heapSize)
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getJvmArgs(): MutableList<String> {
        return forkOptions.getJvmArgs()
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getJvmArgumentProviders(): MutableList<CommandLineArgumentProvider> {
        return forkOptions.getJvmArgumentProviders()
    }

    /**
     * {@inheritDoc}
     */
    override fun setJvmArgs(arguments: MutableList<String>) {
        forkOptions.setJvmArgs(arguments)
    }

    /**
     * {@inheritDoc}
     */
    override fun setJvmArgs(arguments: Iterable<*>) {
        forkOptions.setJvmArgs(arguments)
    }

    /**
     * {@inheritDoc}
     */
    override fun jvmArgs(arguments: Iterable<*>): Test {
        forkOptions.jvmArgs(arguments)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun jvmArgs(vararg arguments: Any): Test {
        forkOptions.jvmArgs(*arguments)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getEnableAssertions(): Boolean {
        return forkOptions.getEnableAssertions()
    }

    /**
     * {@inheritDoc}
     */
    override fun setEnableAssertions(enabled: Boolean) {
        forkOptions.setEnableAssertions(enabled)
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getDebug(): Boolean {
        return forkOptions.getDebug()
    }

    /**
     * {@inheritDoc}
     */
    @Option(option = "debug-jvm", description = "Enable debugging for the test process. The process is started suspended and listening on port 5005.")
    override fun setDebug(enabled: Boolean) {
        forkOptions.setDebug(enabled)
    }


    /**
     * {@inheritDoc}
     */
    override fun getDebugOptions(): JavaDebugOptions {
        return forkOptions.getDebugOptions()
    }

    /**
     * {@inheritDoc}
     */
    override fun debugOptions(action: Action<JavaDebugOptions>) {
        forkOptions.debugOptions(action)
    }

    /**
     * Enables fail fast behavior causing the task to fail on the first failed test.
     */
    @Option(option = "fail-fast", description = "Stops test execution after the first failed test.")
    public override fun setFailFast(failFast: Boolean) {
        super.setFailFast(failFast)
    }

    /**
     * Indicates if this task will fail on the first failed test
     *
     * @return whether this task will fail on the first failed test
     */
    @ToBeReplacedByLazyProperty
    public override fun getFailFast(): Boolean {
        return super.getFailFast()
    }

    @get:Option(option = "test-dry-run", description = "Simulate test execution.")
    @get:Input
    @get:Incubating
    abstract val dryRun: Property<Boolean>?

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getAllJvmArgs(): MutableList<String> {
        return forkOptions.getAllJvmArgs()
    }

    /**
     * {@inheritDoc}
     */
    override fun setAllJvmArgs(arguments: MutableList<String>) {
        forkOptions.setAllJvmArgs(arguments)
    }

    /**
     * {@inheritDoc}
     */
    override fun setAllJvmArgs(arguments: Iterable<*>) {
        forkOptions.setAllJvmArgs(arguments)
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @ToBeReplacedByLazyProperty
    override fun getEnvironment(): MutableMap<String, Any> {
        return forkOptions.getEnvironment()
    }

    /**
     * {@inheritDoc}
     */
    override fun environment(environmentVariables: MutableMap<String, *>): Test {
        forkOptions.environment(environmentVariables)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun environment(name: String, value: Any): Test {
        forkOptions.environment(name, value)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun setEnvironment(environmentVariables: MutableMap<String, *>) {
        forkOptions.setEnvironment(environmentVariables)
    }

    /**
     * {@inheritDoc}
     */
    override fun copyTo(target: ProcessForkOptions): Test {
        forkOptions.copyTo(target)
        copyToolchainAsExecutable(target)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun copyTo(target: JavaForkOptions): Test {
        forkOptions.copyTo(target)
        copyToolchainAsExecutable(target)
        return this
    }

    private fun copyToolchainAsExecutable(target: ProcessForkOptions) {
        val executable: String? = this.javaLauncher.get().executablePath.toString()
        target.setExecutable(executable)
    }

    /**
     * {@inheritDoc}
     *
     * @since 4.4
     */
    override fun createTestExecutionSpec(): JvmTestExecutionSpec {
        if (!getTestFramework().supportsNonClassBasedTesting() && !this.testDefinitionDirs.isEmpty()) {
            throw GradleException("The " + getTestFramework().getDisplayName() + " test framework does not support resource-based testing.")
        }

        validateExecutableMatchesToolchain()
        val javaForkOptions: JavaForkOptions? = this.forkOptionsFactory.newJavaForkOptions()
        copyTo(javaForkOptions!!)
        javaForkOptions.systemProperty(TestWorker.WORKER_TMPDIR_SYS_PROPERTY, File(getTemporaryDir(), "work"))
        val javaModuleDetector: JavaModuleDetector = this.javaModuleDetector
        val testIsModule: Boolean = javaModuleDetector.isModule(modularity.getInferModulePath().get(), this.testClassesDirs)
        val classpath: FileCollection? = javaModuleDetector.inferClasspath(testIsModule, stableClasspath)
        val modulePath: FileCollection? = javaModuleDetector.inferModulePath(testIsModule, stableClasspath)
        val candidateTestDefinitionDirs = determineCandidateTestDefinitionDirs()
        return JvmTestExecutionSpec(
            getTestFramework(), classpath, modulePath,
            this.candidateClassFiles, this.isScanForTestClasses, candidateTestDefinitionDirs,
            this.testClassesDirs, getPath(), getIdentityPath(), this.forkEvery, javaForkOptions, this.maxParallelForks, this.previousFailedTestClasses, testIsModule
        )
    }

    private fun determineCandidateTestDefinitionDirs(): MutableSet<File> {
        return this.testDefinitionDirs.getFiles().stream()
            .filter { dir: File? -> this.isValidDefinitionDir(dir!!) }
            .filter { dir: File? -> this.matchesPatternSet(dir!!) }
            .collect(Collectors.toSet())
    }

    private fun isValidDefinitionDir(dir: File): Boolean {
        if (!dir.exists()) {
            LOGGER.warn("Test definitions directory does not exist: " + dir.getAbsolutePath())
            return false
        } else if (!dir.isDirectory()) {
            LOGGER.warn("Test definitions directory is not a directory: " + dir.getAbsolutePath())
            return false
        } else {
            return true
        }
    }

    private fun matchesPatternSet(dir: File): Boolean {
        val fileTree = getObjectFactory().fileTree()
        fileTree.from(dir)
        fileTree.include(patternSet.getIncludes())
        fileTree.exclude(patternSet.getExcludes())
        return !fileTree.isEmpty()
    }

    private fun validateExecutableMatchesToolchain() {
        val toolchainExecutable = this.javaLauncher.get().executablePath.getAsFile()
        val customExecutable = getExecutable()
        JavaExecutableUtils.validateExecutable(
            customExecutable, "Toolchain from `executable` property",
            toolchainExecutable, "toolchain from `javaLauncher` property"
        )
    }

    private val previousFailedTestClasses: MutableSet<String>
        get() {
            val store =
                SerializableTestResultStore(getBinaryResultsDirectory().getAsFile().get().toPath())
            // We ignore if we can't read the old results file, as this is just an optimization.
            if (store.hasResultsSafe()) {
                val previousFailedTestClasses: MutableSet<String> = HashSet<String>()
                try {
                    store.forEachResult(SerializableTestResultStore.ResultProcessor { id: Long, parentId: Long?, result: SerializableTestResult?, ranges: OutputRanges? ->
                        // Test class descriptors set both name and class name to the test class name
                        if (result!!.getName() == result.getClassName()) {
                            if (result.getResultType() == TestResult.ResultType.FAILURE) {
                                previousFailedTestClasses.add(result.getClassName()!!)
                            }
                        }
                    })
                } catch (e: Exception) {
                    throw UncheckedException.throwAsUncheckedException(e)
                }
                return previousFailedTestClasses
            } else {
                return mutableSetOf<String>()
            }
        }

    @TaskAction
    override fun executeTests() {
        val javaVersion = this.javaVersion
        if (!javaVersion.isCompatibleWith(JavaVersion.toVersion(SupportedJavaVersions.MINIMUM_WORKER_JAVA_VERSION)!!)) {
            throw UnsupportedJavaRuntimeException(
                String.format(
                    "Gradle does not support executing tests using JVM %s or earlier.",
                    SupportedJavaVersions.MINIMUM_WORKER_JAVA_VERSION - 1
                )
            )
        }

        // TODO: JUnit6 requires Java 17+
        // Gradle should produce a better error message when using JUnit 6 with incompatible JVMs.
        if (getDebug()) {
            getLogger().info("Running tests for remote debugging.")
        }

        try {
            super.executeTests()
        } finally {
            CompositeStoppable.stoppable(getTestFramework()).stop()
        }
    }

    override fun createTestExecuter(): TestExecuter<JvmTestExecutionSpec> {
        if (testExecuter == null) {
            return DefaultTestExecuter(
                this.processBuilderFactory, this.actorFactory, this.moduleRegistry,
                getServices().get<WorkerLeaseService?>(WorkerLeaseService::class.java)!!,
                getServices().get<StartParameter?>(StartParameter::class.java)!!.maxWorkerCount,
                getServices().get<Clock?>(Clock::class.java)!!,
                (getFilter() as org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter?)!!
            )
        } else {
            return testExecuter!!
        }
    }

    override fun getNoMatchingTestErrorReasons(): MutableList<String> {
        val reasons: MutableList<String> = ArrayList<String>()
        if (!getIncludes().isEmpty()) {
            reasons.add(getIncludes().toString() + "(include rules)")
        }
        if (!getExcludes().isEmpty()) {
            reasons.add(getExcludes().toString() + "(exclude rules)")
        }
        reasons.addAll(super.getNoMatchingTestErrorReasons())
        return reasons
    }

    override fun getReportEntrySkipLevels(): Int {
        // Add 1 for the workers, plus any additional levels required by the test framework
        return super.getReportEntrySkipLevels() + 1 + getTestFramework().getAdditionalReportEntrySkipLevels()
    }

    /**
     * Adds include patterns for the files in the test classes directory (e.g. '**&#47;*Test.class')).
     *
     * @see .setIncludes
     */
    override fun include(vararg includes: String): Test {
        patternSet.include(*includes)
        return this
    }

    /**
     * Adds include patterns for the files in the test classes directory (e.g. '**&#47;*Test.class')).
     *
     * @see .setIncludes
     */
    override fun include(includes: Iterable<String>): Test {
        patternSet.include(includes)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun include(includeSpec: Spec<FileTreeElement?>): Test {
        patternSet.include(includeSpec)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun include(includeSpec: Closure<*>): Test {
        patternSet.include(includeSpec)
        return this
    }

    /**
     * Adds exclude patterns for the files in the test classes directory (e.g. '**&#47;*Test.class')).
     *
     * @see .setExcludes
     */
    override fun exclude(vararg excludes: String): Test {
        patternSet.exclude(*excludes)
        return this
    }

    /**
     * Adds exclude patterns for the files in the test classes directory (e.g. '**&#47;*Test.class')).
     *
     * @see .setExcludes
     */
    override fun exclude(excludes: Iterable<String>): Test {
        patternSet.exclude(excludes)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun exclude(excludeSpec: Spec<FileTreeElement?>): Test {
        patternSet.exclude(excludeSpec)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun exclude(excludeSpec: Closure<*>): Test {
        patternSet.exclude(excludeSpec)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun setTestNameIncludePatterns(testNamePattern: MutableList<String>): Test {
        super.setTestNameIncludePatterns(testNamePattern)
        return this
    }

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    @get:InputFiles
    @get:Incubating
    abstract val testDefinitionDirs: ConfigurableFileCollection?

    /**
     * Returns the include patterns for test execution.
     *
     * @see .include
     */
    @Internal
    @ToBeReplacedByLazyProperty
    override fun getIncludes(): MutableSet<String> {
        return patternSet.getIncludes()
    }

    /**
     * Sets the include patterns for test execution.
     *
     * @param includes The patterns list
     * @see .include
     */
    override fun setIncludes(includes: Iterable<String>): Test {
        patternSet.setIncludes(includes)
        return this
    }

    /**
     * Returns the exclude patterns for test execution.
     *
     * @see .exclude
     */
    @Internal
    @ToBeReplacedByLazyProperty
    override fun getExcludes(): MutableSet<String> {
        return patternSet.getExcludes()
    }

    /**
     * Sets the exclude patterns for test execution.
     *
     * @param excludes The patterns list
     * @see .exclude
     */
    override fun setExcludes(excludes: Iterable<String>): Test {
        patternSet.setExcludes(excludes)
        return this
    }

    @Internal
    @ToBeReplacedByLazyProperty(comment = "This will be removed")
    fun getTestFramework(): TestFramework {
        // TODO: Deprecate and remove this method
        return testFrameworkProperty.get()
    }

    /**
     * Do not call this method.
     */
    @Deprecated("This will be removed in Gradle 10")
    fun testFramework(testFrameworkConfigure: Closure<*>?): TestFramework {
        DeprecationLogger.deprecateMethod(Test::class.java, "testFramework(Closure)")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "deprecated_test_methods")!!
            .nagUser()
        options(testFrameworkConfigure!!)
        return getTestFramework()
    }

    @get:Nested
    val options: TestFrameworkOptions
        /**
         * Returns test framework specific options. Make sure to call [.useJUnit], [.useJUnitPlatform] or [.useTestNG] before using this method.
         *
         * @return The test framework options.
         */
        get() = getTestFramework().getOptions()

    /**
     * Configures test framework specific options.
     *
     *
     * When a `Test` task is created outside of Test Suites, you should call [.useJUnit], [.useJUnitPlatform] or [.useTestNG] before using this method.
     * If no test framework has been set, the task will assume JUnit4.
     *
     * @return The test framework options.
     */
    fun options(@DelegatesTo(TestFrameworkOptions::class) testFrameworkConfigure: Closure<*>): TestFrameworkOptions {
        return ConfigureUtil.configure<TestFrameworkOptions>(testFrameworkConfigure, this.options)
    }

    /**
     * Configures test framework specific options.
     *
     *
     * When a `Test` task is created outside of Test Suites, you should call [.useJUnit], [.useJUnitPlatform] or [.useTestNG] before using this method.
     * If no test framework has been set, the task will assume JUnit4.
     *
     * @return The test framework options.
     * @since 3.5
     */
    fun options(testFrameworkConfigure: Action<in TestFrameworkOptions>): TestFrameworkOptions {
        return Actions.with<TestFrameworkOptions>(this.options, testFrameworkConfigure)
    }

    /**
     * Specifies that JUnit4 should be used to discover and execute the tests.
     *
     * @see .useJUnit
     */
    fun useJUnit() {
        useTestFramework(
            getObjectFactory().newInstance<JUnitTestFramework>(
                JUnitTestFramework::class.java, this.getFilter(), this.getTemporaryDirFactory(),
                this.dryRun
            )
        )
    }

    /**
     * Specifies that JUnit4 should be used to discover and execute the tests with additional configuration.
     *
     *
     * The supplied action configures an instance of [JUnit4 specific options][JUnitOptions].
     *
     * @param testFrameworkConfigure A closure used to configure JUnit4 options.
     */
    fun useJUnit(@DelegatesTo(JUnitOptions::class) testFrameworkConfigure: Closure<*>?) {
        useJUnit(ConfigureUtil.configureUsing<JUnitOptions>(testFrameworkConfigure))
    }

    /**
     * Specifies that JUnit4 should be used to discover and execute the tests with additional configuration.
     *
     *
     * The supplied action configures an instance of [JUnit4 specific options][JUnitOptions].
     *
     * @param testFrameworkConfigure An action used to configure JUnit4 options.
     * @since 3.5
     */
    fun useJUnit(testFrameworkConfigure: Action<in JUnitOptions>) {
        useJUnit()
        applyOptions<JUnitOptions>(JUnitOptions::class.java, testFrameworkConfigure)
    }

    /**
     * Specifies that JUnit Platform should be used to discover and execute the tests.
     *
     *
     * Use this option if your tests use JUnit Jupiter/JUnit5.
     *
     *
     * JUnit Platform supports multiple test engines, which allows other testing frameworks to be built on top of it.
     * You may need to use this option even if you are not using JUnit directly.
     *
     * @see .useJUnitPlatform
     * @since 4.6
     */
    fun useJUnitPlatform() {
        useTestFramework(
            getObjectFactory().newInstance<JUnitPlatformTestFramework>(
                JUnitPlatformTestFramework::class.java, getFilter(),
                this.dryRun
            )
        )
    }

    /**
     * Specifies that JUnit Platform should be used to discover and execute the tests with additional configuration.
     *
     *
     * Use this option if your tests use JUnit Jupiter/JUnit5.
     *
     *
     * JUnit Platform supports multiple test engines, which allows other testing frameworks to be built on top of it.
     * You may need to use this option even if you are not using JUnit directly.
     *
     *
     * The supplied action configures an instance of [JUnit Platform specific options][JUnitPlatformOptions].
     *
     * @param testFrameworkConfigure A closure used to configure JUnit platform options.
     * @since 4.6
     */
    fun useJUnitPlatform(testFrameworkConfigure: Action<in JUnitPlatformOptions>) {
        useJUnitPlatform()
        applyOptions<JUnitPlatformOptions>(JUnitPlatformOptions::class.java, testFrameworkConfigure)
    }

    /**
     * Specifies that TestNG should be used to discover and execute the tests.
     *
     * @see .useTestNG
     */
    fun useTestNG() {
        useTestFramework(
            getObjectFactory().newInstance<TestNGTestFramework>(
                TestNGTestFramework::class.java, this.getFilter(), this.getTemporaryDirFactory(),
                this.dryRun, this.getReports().getHtml()
            )
        )
    }

    /**
     * Specifies that TestNG should be used to discover and execute the tests with additional configuration.
     *
     *
     * The supplied action configures an instance of [TestNG specific options][TestNGOptions].
     *
     * @param testFrameworkConfigure A closure used to configure TestNG options.
     */
    fun useTestNG(@DelegatesTo(TestNGOptions::class) testFrameworkConfigure: Closure<*>) {
        useTestNG(ConfigureUtil.configureUsing<TestNGOptions>(testFrameworkConfigure))
    }

    /**
     * Specifies that TestNG should be used to discover and execute the tests with additional configuration.
     *
     *
     * The supplied action configures an instance of [TestNG specific options][TestNGOptions].
     *
     * @param testFrameworkConfigure An action used to configure TestNG options.
     * @since 3.5
     */
    fun useTestNG(testFrameworkConfigure: Action<in TestNGOptions>) {
        useTestNG()
        applyOptions<TestNGOptions>(TestNGOptions::class.java, testFrameworkConfigure)
    }

    /**
     * Set the framework, only if it is being changed to a new value.
     *
     * If we are setting a framework to its existing value, no-op so as not to overwrite existing options here.
     * We need to allow this especially for the default test task, so that existing builds that configure options and
     * then call useJunit() don't clear out their options.
     */
    fun useTestFramework(testFramework: TestFramework) {
        val currentFramework: Class<*> = this.testFrameworkProperty.get().javaClass
        val newFramework: Class<*> = testFramework.javaClass
        if (currentFramework == newFramework) {
            return
        }

        this.testFrameworkProperty.set(testFramework)
    }

    private fun <T : TestFrameworkOptions?> applyOptions(optionsClass: Class<T?>, configuration: Action<in T>) {
        Actions.with<T?>(Cast.cast<T?, TestFrameworkOptions?>(optionsClass, this.options), configuration)
    }

    /**
     * Returns the classpath to use to execute the tests.
     *
     * @since 6.6
     */
    @Classpath
    protected fun getStableClasspath(): FileCollection {
        return stableClasspath
    }

    @get:ToBeReplacedByLazyProperty(comment = "Should this be kept as it is?")
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    @get:InputFiles
    val candidateClassFiles: FileTree
        /**
         * Returns the classes files to scan for test classes.
         *
         * @return The candidate class files.
         */
        get() = this.testClassesDirs.getAsFileTree().matching(patternSet)

    /**
     * Executes the action against the [.getFilter].
     *
     * @param action configuration of the test filter
     * @since 1.10
     */
    fun filter(action: Action<TestFilter>) {
        action.execute(getFilter())
    }

    /**
     * Sets the testExecuter property.
     *
     * @since 4.2
     */
    @UsedByScanPlugin("test-distribution, test-retry")
    fun setTestExecuter(testExecuter: TestExecuter<JvmTestExecutionSpec>) {
        this.testExecuter = testExecuter
    }

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>?

    override fun testsAreNotFiltered(): Boolean {
        return super.testsAreNotFiltered()
                && noCategoryOrTagOrGroupSpecified()
    }

    private fun noCategoryOrTagOrGroupSpecified(): Boolean {
        val frameworkOptions = getTestFramework().getOptions()
        if (frameworkOptions == null) {
            return true
        }

        if (JUnitOptions::class.java.isAssignableFrom(frameworkOptions.javaClass)) {
            val junitOptions = frameworkOptions as JUnitOptions
            return junitOptions.getIncludeCategories().isEmpty()
                    && junitOptions.getExcludeCategories().isEmpty()
        } else if (JUnitPlatformOptions::class.java.isAssignableFrom(frameworkOptions.javaClass)) {
            val junitPlatformOptions = frameworkOptions as JUnitPlatformOptions
            return junitPlatformOptions.getIncludeTags().isEmpty()
                    && junitPlatformOptions.getExcludeTags().isEmpty()
        } else if (TestNGOptions::class.java.isAssignableFrom(frameworkOptions.javaClass)) {
            val testNGOptions = frameworkOptions as TestNGOptions
            return testNGOptions.getIncludeGroups().isEmpty()
                    && testNGOptions.getExcludeGroups().isEmpty()
        } else {
            throw IllegalArgumentException("Unknown test framework: " + frameworkOptions.javaClass.getName())
        }
    }

    @get:Inject
    protected abstract val propertyFactory: PropertyFactory

    @get:Inject
    protected abstract val javaToolchainService: JavaToolchainService

    @get:Inject
    protected abstract val providerFactory: ProviderFactory?

    @get:Inject
    protected abstract val actorFactory: ActorFactory?

    @get:Inject
    protected abstract val processBuilderFactory: WorkerProcessFactory?

    @get:Inject
    protected abstract val patternSetFactory: PatternSetFactory?

    @get:Inject
    protected abstract val forkOptionsFactory: JavaForkOptionsFactory?

    @get:Inject
    protected abstract val moduleRegistry: ModuleRegistry?

    @get:Inject
    protected abstract val javaModuleDetector: JavaModuleDetector

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(Test::class.java)
    }
}
