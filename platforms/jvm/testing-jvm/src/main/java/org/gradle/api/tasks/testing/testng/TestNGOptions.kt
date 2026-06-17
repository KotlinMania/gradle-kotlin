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
package org.gradle.api.tasks.testing.testng

import groovy.lang.MissingMethodException
import groovy.lang.MissingPropertyException
import groovy.xml.MarkupBuilder
import org.gradle.api.Incubating
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.tasks.testing.testng.TestNGTestRunner
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.TestFrameworkOptions
import org.gradle.internal.ErroringAction
import org.gradle.internal.IoActions
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.internal.serialization.Cached
import java.io.BufferedWriter
import java.io.File
import java.io.StringWriter
import java.util.Arrays
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * The TestNG specific test options.
 */
abstract class TestNGOptions @Inject constructor(projectLayout: ProjectLayout) : TestFrameworkOptions() {
    /**
     * The location to write TestNG's output.
     *
     * Defaults to the owning test task's location for writing the HTML report.
     *
     * @since 1.11
     */
    @get:ToBeReplacedByLazyProperty
    @get:OutputDirectory
    var outputDirectory: File? = null

    /**
     * The set of groups to run.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var includeGroups: MutableSet<String?> = LinkedHashSet<String?>()

    /**
     * The set of groups to exclude.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var excludeGroups: MutableSet<String?> = LinkedHashSet<String?>()

    /**
     * Option for what to do for other tests that use a configuration step when that step fails. Can be "skip" or "continue", defaults to "skip".
     */
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var configFailurePolicy: String? = DEFAULT_CONFIG_FAILURE_POLICY

    /**
     * Fully qualified classes that are TestNG listeners (instances of org.testng.ITestListener or org.testng.IReporter). By default, the listeners set is empty.
     *
     * Configuring extra listener:
     * <pre class='autoTested'>
     * plugins {
     * id 'java'
     * }
     *
     * test {
     * useTestNG() {
     * // creates emailable HTML file
     * // this reporter typically ships with TestNG library
     * listeners &lt;&lt; 'org.testng.reporters.EmailableReporter'
     * }
     * }
    </pre> *
     */
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var listeners: MutableSet<String?> = LinkedHashSet<String?>()

    /**
     * The parallel mode to use for running the tests - one of the following modes: methods, tests, classes or instances.
     *
     * Not required.
     *
     * If not present, parallel mode will not be selected
     */
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var parallel: String? = DEFAULT_PARALLEL_MODE

    /**
     * The number of threads to use for this run. Ignored unless the parallel mode is also specified
     */
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var threadCount: Int = DEFAULT_THREAD_COUNT

    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var useDefaultListeners: Boolean = false

    /**
     * ThreadPoolExecutorFactory class used by TestNG
     * @since 8.7
     */
    /**
     * Sets a custom threadPoolExecutorFactory class.
     * This should be a fully qualified class name and the class should implement org.testng.IExecutorFactory
     * More details in https://github.com/testng-team/testng/pull/2042
     * Requires TestNG 7.0 or higher
     * @since 8.7
     */
    @get:ToBeReplacedByLazyProperty
    @get:Incubating
    @get:Internal
    @set:Incubating
    var threadPoolFactoryClass: String? = null

    /**
     * Sets the default name of the test suite, if one is not specified in a suite XML file or in the source code.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var suiteName: String? = "Gradle suite"

    /**
     * Sets the default name of the test, if one is not specified in a suite XML file or in the source code.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var testName: String? = "Gradle test"

    /**
     * The suiteXmlFiles to use for running TestNG.
     *
     * Note: The suiteXmlFiles can be used in conjunction with the suiteXmlBuilder.
     */
    @get:ToBeReplacedByLazyProperty
    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFiles
    var suiteXmlFiles: MutableList<File?> = ArrayList<File?>()

    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var preserveOrder: Boolean = false

    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var groupByInstances: Boolean = false

    @get:ToBeReplacedByLazyProperty
    @get:Internal
    @Transient
    var suiteXmlWriter: StringWriter? = null

    @get:ToBeReplacedByLazyProperty
    @get:Internal
    @Transient
    var suiteXmlBuilder: MarkupBuilder? = null

    private val cachedSuiteXml = Cached.of(object : Callable<String?> {
        override fun call(): String? {
            return if (suiteXmlWriter != null) suiteXmlWriter.toString() else null
        }
    })

    @get:Internal
    protected val projectDir: File

    init {
        this.projectDir = projectLayout.getProjectDirectory().getAsFile()
        this.suiteThreadPoolSize.convention(DEFAULT_SUITE_THREAD_POOL_SIZE_DEFAULT)
    }

    /**
     * Copies the options from the source options into the current one.
     * @since 8.0
     */
    fun copyFrom(other: TestNGOptions) {
        this.outputDirectory = other.outputDirectory
        replace<String?>(this.includeGroups, other.includeGroups)
        replace<String?>(this.excludeGroups, other.excludeGroups)
        this.configFailurePolicy = other.configFailurePolicy
        replace<String?>(this.listeners, other.listeners)
        this.parallel = other.parallel
        this.threadCount = other.threadCount
        this.suiteThreadPoolSize.set(other.suiteThreadPoolSize)
        this.useDefaultListeners = other.useDefaultListeners
        this.threadPoolFactoryClass = other.threadPoolFactoryClass
        this.suiteName = other.suiteName
        this.testName = other.testName
        replace<File?>(this.suiteXmlFiles, other.suiteXmlFiles)
        this.preserveOrder = other.preserveOrder
        this.groupByInstances = other.groupByInstances
        // not copying suiteXmlWriter as it is transient
        // not copying suiteXmlBuilder as it is transient
    }

    fun suiteXmlBuilder(): MarkupBuilder {
        suiteXmlWriter = StringWriter()
        suiteXmlBuilder = MarkupBuilder(suiteXmlWriter)
        return suiteXmlBuilder!!
    }

    /**
     * Add suite files by Strings. Each suiteFile String should be a path relative to the project root.
     */
    fun suites(vararg suiteFiles: String) {
        for (suiteFile in suiteFiles) {
            suiteXmlFiles.add(File(this@TestNGOptions.projectDir, suiteFile))
        }
    }

    /**
     * Add suite files by File objects.
     */
    fun suites(vararg suiteFiles: File?) {
        suiteXmlFiles.addAll(Arrays.asList<File?>(*suiteFiles))
    }

    @ToBeReplacedByLazyProperty
    fun getSuites(testSuitesDir: File): MutableList<File?> {
        val suites: MutableList<File?> = ArrayList<File?>()

        suites.addAll(suiteXmlFiles)

        val suiteXmlMarkup = this.suiteXml
        if (suiteXmlMarkup != null) {
            val buildSuiteXml = File(testSuitesDir.getAbsolutePath(), "build-suite.xml")

            if (buildSuiteXml.exists()) {
                if (!buildSuiteXml.delete()) {
                    throw RuntimeException("failed to remove already existing build-suite.xml file")
                }
            }

            IoActions.writeTextFile(buildSuiteXml, object : ErroringAction<BufferedWriter?>() {
                @Throws(Exception::class)
                override fun doExecute(writer: BufferedWriter) {
                    writer.write("<!DOCTYPE suite SYSTEM \"http://testng.org/testng-1.0.dtd\">")
                    writer.newLine()
                    writer.write(suiteXmlMarkup)
                }
            })

            suites.add(buildSuiteXml)
        }

        return suites
    }

    fun includeGroups(vararg includeGroups: String?): TestNGOptions {
        this.includeGroups.addAll(Arrays.asList<String?>(*includeGroups))
        return this
    }

    fun excludeGroups(vararg excludeGroups: String?): TestNGOptions {
        this.excludeGroups.addAll(Arrays.asList<String?>(*excludeGroups))
        return this
    }

    fun useDefaultListeners(): TestNGOptions {
        useDefaultListeners = true
        return this
    }

    fun useDefaultListeners(useDefaultListeners: Boolean): TestNGOptions {
        this.useDefaultListeners = useDefaultListeners
        return this
    }

    fun propertyMissing(name: String?): Any? {
        if (suiteXmlBuilder != null) {
            return suiteXmlBuilder!!.getMetaClass().getProperty(suiteXmlBuilder, name)
        }

        throw MissingPropertyException(name, javaClass)
    }

    fun methodMissing(name: String?, args: Any?): Any? {
        if (suiteXmlBuilder != null) {
            return suiteXmlBuilder!!.getMetaClass().invokeMethod(suiteXmlBuilder, name, args)
        }

        throw MissingMethodException(name, javaClass, args as Array<Any?>?)
    }

    @get:Incubating
    @get:Internal
    abstract val suiteThreadPoolSize: Property<Int?>?

    /**
     * Whether the default listeners and reporters should be used. Since Gradle 1.4 it defaults to 'false' so that Gradle can own the reports generation and provide various improvements. This option
     * might be useful for advanced TestNG users who prefer the reports generated by the TestNG library. If you cannot live without some specific TestNG reporter please use [.listeners]
     * property. If you really want to use all default TestNG reporters (e.g. generate the old reports):
     *
     * <pre class='autoTested'>
     * plugins {
     * id 'java'
     * }
     *
     * test {
     * useTestNG() {
     * // report generation delegated to TestNG library:
     * useDefaultListeners = true
     * }
     *
     * // turn off Gradle's HTML report to avoid replacing the
     * // reports generated by TestNG library:
     * reports.html.required = false
     * }
    </pre> *
     *
     * Please refer to the documentation of your version of TestNG what are the default listeners. At the moment of writing this documentation, the default listeners are a set of reporters that
     * generate: TestNG variant of HTML results, TestNG variant of XML results in JUnit format, emailable HTML test report, XML results in TestNG format.
     */
    @Internal
    @ToBeReplacedByLazyProperty
    fun isUseDefaultListeners(): Boolean {
        return useDefaultListeners
    }

    /**
     * Indicates whether the tests should be run in deterministic order. Preserving the order guarantees that the complete test
     * (including @BeforeXXX and @AfterXXX) is run in a test thread before the next test is run.
     *
     * Not required.
     *
     * If not present, the order will not be preserved.
     */
    @Internal
    @ToBeReplacedByLazyProperty
    fun isPreserveOrder(): Boolean {
        return preserveOrder
    }

    /**
     * Indicates whether the tests should be grouped by instances. Grouping by instances will result in resolving test method dependencies for each instance instead of running the dependees of all
     * instances before running the dependants.
     *
     * Not required.
     *
     * If not present, the tests will not be grouped by instances.
     */
    @Internal
    @ToBeReplacedByLazyProperty
    fun isGroupByInstances(): Boolean {
        return groupByInstances
    }

    @get:Optional
    @get:Input
    protected val suiteXml: String?
        /**
         * Returns the XML generated using [.suiteXmlBuilder], if any.
         *
         *
         * This property is read-only and exists merely for up-to-date checking.
         */
        get() = cachedSuiteXml.get()

    companion object {
        val DEFAULT_CONFIG_FAILURE_POLICY: String = TestNGTestRunner.DEFAULT_CONFIG_FAILURE_POLICY
        private val DEFAULT_PARALLEL_MODE: String? = null
        private val DEFAULT_THREAD_COUNT = -1
        private const val DEFAULT_SUITE_THREAD_POOL_SIZE_DEFAULT = 1

        private fun <T> replace(target: MutableCollection<T?>, source: MutableCollection<T?>) {
            target.clear()
            target.addAll(source)
        }
    }
}
