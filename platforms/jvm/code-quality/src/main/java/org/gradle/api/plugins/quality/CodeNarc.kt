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
import org.gradle.api.Action
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.quality.internal.CodeNarcActionParameters
import org.gradle.api.plugins.quality.internal.CodeNarcInvoker
import org.gradle.api.plugins.quality.internal.CodeNarcReportsImpl
import org.gradle.api.reporting.Reporting
import org.gradle.api.reporting.SingleFileReport
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.Describables
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.util.internal.ClosureBackedAction
import org.gradle.workers.ProcessWorkerSpec
import java.util.stream.Collectors

/**
 * Runs CodeNarc against some source files.
 */
@CacheableTask
abstract class CodeNarc : AbstractCodeQualityTask(), Reporting<CodeNarcReports> {
    /**
     * The class path containing the CodeNarc library to be used.
     */
    /**
     * The class path containing the CodeNarc library to be used.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Classpath
    var codenarcClasspath: FileCollection? = null

    /**
     * The class path to be used by CodeNarc when compiling classes during analysis.
     *
     * @since 4.2
     */
    /**
     * The class path to be used by CodeNarc when compiling classes during analysis.
     *
     * @since 4.2
     */
    @get:ToBeReplacedByLazyProperty
    @get:Classpath
    var compilationClasspath: FileCollection

    /**
     * The CodeNarc configuration to use. Replaces the `configFile` property.
     *
     * @since 2.2
     */
    /**
     * The CodeNarc configuration to use. Replaces the `configFile` property.
     *
     * @since 2.2
     */
    @get:Nested
    var config: TextResource? = null

    /**
     * The maximum number of priority 1 violations allowed before failing the build.
     */
    /**
     * The maximum number of priority 1 violations allowed before failing the build.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var maxPriority1Violations: Int = 0

    /**
     * The maximum number of priority 2 violations allowed before failing the build.
     */
    /**
     * The maximum number of priority 2 violations allowed before failing the build.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var maxPriority2Violations: Int = 0

    /**
     * The maximum number of priority 3 violations allowed before failing the build.
     */
    /**
     * The maximum number of priority 3 violations allowed before failing the build.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var maxPriority3Violations: Int = 0

    private val reports: CodeNarcReports

    init {
        reports = getObjectFactory().newInstance<CodeNarcReportsImpl>(CodeNarcReportsImpl::class.java, Describables.quoted("Task", getIdentityPath()))
        compilationClasspath = getProject().files()
        // Set default JavaLauncher to current JVM in case
        // CodeNarcPlugin that sets Java launcher convention is not applied
    }

    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var configFile: File
        /**
         * The CodeNarc configuration file to use.
         */
        get() = if (this.config == null) null else this.config!!.asFile()
        /**
         * The CodeNarc configuration file to use.
         */
        set(configFile) {
            this.config = getProject().getResources().getText().fromFile(configFile)
        }

    /**
     * {@inheritDoc}
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @ToBeReplacedByLazyProperty
    override fun getSource(): FileTree {
        return super.getSource()
    }

    @TaskAction
    fun run() {
        val workQueue = getWorkerExecutor().processIsolation(Action { spec: ProcessWorkerSpec? -> configureForkOptions(spec!!.getForkOptions()) })
        workQueue.submit<CodeNarcActionParameters>(CodeNarcInvoker::class.java, Action { parameters: CodeNarcActionParameters -> this.setupParameters(parameters) })
    }

    private fun setupParameters(parameters: CodeNarcActionParameters) {
        parameters.antLibraryClasspath.setFrom(this.codenarcClasspath)
        parameters.compilationClasspath.setFrom(this.compilationClasspath)
        parameters.config.set(this.configFile)
        parameters.maxPriority1Violations.set(this.maxPriority1Violations)
        parameters.maxPriority2Violations.set(this.maxPriority2Violations)
        parameters.maxPriority3Violations.set(this.maxPriority3Violations)
        parameters.enabledReports.set(getReports().getEnabled().stream().map<CodeNarcActionParameters.EnabledReport> { report: SingleFileReport? ->
            val newReport = getObjectFactory().newInstance<CodeNarcActionParameters.EnabledReport>(CodeNarcActionParameters.EnabledReport::class.java)
            newReport.getName().set(report!!.getName())
            newReport.getOutputLocation().set(report.getOutputLocation())
            newReport
        }.collect(Collectors.toList()))
        parameters.ignoreFailures.set(getIgnoreFailures())
        parameters.source.setFrom(getSource())
    }

    /**
     * Configures the reports to be generated by this task.
     */
    override fun reports(closure: Closure<*>): CodeNarcReports {
        return reports(ClosureBackedAction<CodeNarcReports>(closure))
    }

    /**
     * Configures the reports to be generated by this task.
     */
    override fun reports(configureAction: Action<in CodeNarcReports>): CodeNarcReports {
        configureAction.execute(reports)
        return reports
    }

    /**
     * The reports to be generated by this task.
     */
    @Nested
    override fun getReports(): CodeNarcReports {
        return reports
    }
}
