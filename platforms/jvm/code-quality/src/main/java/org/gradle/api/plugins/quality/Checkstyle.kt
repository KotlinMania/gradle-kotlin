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
package org.gradle.api.plugins.quality

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.quality.internal.CheckstyleActionParameters
import org.gradle.api.plugins.quality.internal.CheckstyleInvoker
import org.gradle.api.plugins.quality.internal.CheckstyleReportsImpl
import org.gradle.api.reporting.Reporting
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.Describables
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.util.internal.ClosureBackedAction
import org.gradle.workers.ProcessWorkerSpec

/**
 * Runs Checkstyle against some source files.
 *
 * @see CheckstylePlugin
 *
 * @see CheckstyleExtension
 */
@CacheableTask
abstract class Checkstyle : AbstractCodeQualityTask(), Reporting<CheckstyleReports> {
    /**
     * The class path containing the Checkstyle library to be used.
     */
    /**
     * The class path containing the Checkstyle library to be used.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Classpath
    var checkstyleClasspath: FileCollection? = null
    /**
     * The class path containing the compiled classes for the source files to be analyzed.
     */
    /**
     * The class path containing the compiled classes for the source files to be analyzed.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Classpath
    var classpath: FileCollection? = null
    /**
     * The Checkstyle configuration to use. Replaces the `configFile` property.
     *
     * @since 2.2
     */
    /**
     * The Checkstyle configuration to use. Replaces the `configFile` property.
     *
     * @since 2.2
     */
    @get:Nested
    var config: TextResource? = null
    /**
     * The properties available for use in the configuration file. These are substituted into the configuration file.
     */
    /**
     * The properties available for use in the configuration file. These are substituted into the configuration file.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var configProperties: MutableMap<String, Any>? = LinkedHashMap<String, Any>()
    private val reports: CheckstyleReports
    /**
     * The maximum number of errors that are tolerated before breaking the build
     * or setting the failure property.
     *
     * @return the maximum number of errors allowed
     * @since 3.4
     */
    /**
     * Set the maximum number of errors that are tolerated before breaking the build.
     *
     * @param maxErrors number of errors allowed
     * @since 3.4
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var maxErrors: Int = 0
    /**
     * The maximum number of warnings that are tolerated before breaking the build
     * or setting the failure property.
     *
     * @return the maximum number of warnings allowed
     * @since 3.4
     */
    /**
     * Set the maximum number of warnings that are tolerated before breaking the build.
     *
     * @param maxWarnings number of warnings allowed
     * @since 3.4
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var maxWarnings: Int = Int.MAX_VALUE
    /**
     * Whether rule violations are to be displayed on the console.
     *
     * @return true if violations should be displayed on console
     */
    /**
     * Whether rule violations are to be displayed on the console.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Console
    var isShowViolations: Boolean = true

    init {
        this.reports = getObjectFactory().newInstance<CheckstyleReportsImpl>(CheckstyleReportsImpl::class.java, Describables.quoted("Task", getIdentityPath()))
        this.enableExternalDtdLoad.convention(false)
    }

    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var configFile: File
        /**
         * The Checkstyle configuration file to use.
         */
        get() = if (this.config == null) null else this.config!!.asFile()
        /**
         * The Checkstyle configuration file to use.
         */
        set(configFile) {
            this.config = getProject().getResources().getText().fromFile(configFile)
        }

    /**
     * Configures the reports to be generated by this task.
     *
     * The contained reports can be configured by name and closures. Example:
     *
     * <pre>
     * checkstyleTask {
     * reports {
     * html {
     * destination "build/checkstyle.html"
     * }
     * }
     * }
    </pre> *
     *
     * @param closure The configuration
     * @return The reports container
     */
    override fun reports(@DelegatesTo(value = CheckstyleReports::class, strategy = Closure.DELEGATE_FIRST) closure: Closure<*>): CheckstyleReports {
        return reports(ClosureBackedAction<CheckstyleReports>(closure))
    }

    /**
     * Configures the reports to be generated by this task.
     *
     * The contained reports can be configured by name and closures. Example:
     *
     * <pre>
     * checkstyleTask {
     * reports {
     * html {
     * destination "build/checkstyle.html"
     * }
     * }
     * }
    </pre> *
     *
     * @param configureAction The configuration
     * @return The reports container
     * @since 3.0
     */
    override fun reports(configureAction: Action<in CheckstyleReports>): CheckstyleReports {
        configureAction.execute(reports)
        return reports
    }

    @TaskAction
    fun run() {
        runWithProcessIsolation()
    }

    private fun runWithProcessIsolation() {
        val workQueue = getWorkerExecutor().processIsolation(Action { spec: ProcessWorkerSpec? ->
            configureForkOptions(spec!!.getForkOptions())
            spec.getForkOptions().getSystemProperties().put("checkstyle.enableExternalDtdLoad", this.enableExternalDtdLoad.get().toString())
        })
        workQueue.submit<CheckstyleActionParameters>(CheckstyleInvoker::class.java, Action { parameters: CheckstyleActionParameters -> this.setupParameters(parameters) })
    }

    private fun setupParameters(parameters: CheckstyleActionParameters) {
        parameters.getAntLibraryClasspath().setFrom(this.checkstyleClasspath)
        parameters.config.set(this.configFile)
        parameters.maxErrors.set(this.maxErrors)
        parameters.maxWarnings.set(this.maxWarnings)
        parameters.ignoreFailures.set(getIgnoreFailures())
        parameters.configDirectory.set(this.configDirectory)
        parameters.showViolations.set(this.isShowViolations)
        parameters.source.setFrom(getSource())
        parameters.isHtmlRequired.set(getReports().getHtml().getRequired())
        parameters.isXmlRequired.set(getReports().getXml().getRequired())
        parameters.isSarifRequired.set(getReports().getSarif().getRequired())
        parameters.xmlOutputLocation.set(getReports().getXml().getOutputLocation())
        parameters.htmlOutputLocation.set(getReports().getHtml().getOutputLocation())
        parameters.sarifOutputLocation.set(getReports().getSarif().getOutputLocation())
        parameters.temporaryDir.set(getTemporaryDir())
        parameters.configProperties.set(this.configProperties)
        val stylesheetString = getReports().getHtml().getStylesheet()
        if (stylesheetString != null) {
            parameters.stylesheetString.set(stylesheetString.asString())
        }
    }

    /**
     * {@inheritDoc}
     *
     *
     * The sources for this task are relatively relocatable even though it produces output that
     * includes absolute paths. This is a compromise made to ensure that results can be reused
     * between different builds. The downside is that up-to-date results, or results loaded
     * from cache can show different absolute paths than would be produced if the task was
     * executed.
     */
    @ToBeReplacedByLazyProperty
    @PathSensitive(PathSensitivity.RELATIVE)
    override fun getSource(): FileTree {
        return super.getSource()
    }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configDirectory: DirectoryProperty?

    /**
     * The reports to be generated by this task.
     */
    @Nested
    override fun getReports(): CheckstyleReports {
        return reports
    }

    @get:Input
    @get:Incubating
    abstract val enableExternalDtdLoad: Property<Boolean>?

    @get:ToBeReplacedByLazyProperty
    @get:Internal
    val isIgnoreFailures: Boolean
        /**
         * Whether the build should break when the verifications performed by this task fail.
         *
         * @return true if failures should be ignored
         */
        get() = getIgnoreFailures()
}
