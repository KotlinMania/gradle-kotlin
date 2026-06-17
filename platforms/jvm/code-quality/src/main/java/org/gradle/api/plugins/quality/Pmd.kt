/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.quality.internal.PmdActionParameters
import org.gradle.api.plugins.quality.internal.PmdInvoker
import org.gradle.api.plugins.quality.internal.PmdReportsImpl
import org.gradle.api.reporting.Reporting
import org.gradle.api.reporting.SingleFileReport
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.Describables
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.internal.nativeintegration.console.ConsoleDetector
import org.gradle.internal.nativeintegration.services.NativeServices.Companion.getInstance
import org.gradle.util.internal.ClosureBackedAction
import org.gradle.workers.ProcessWorkerSpec
import java.io.File
import java.util.stream.Collectors

/**
 * Runs a set of static code analysis rules on Java source code files and generates a report of problems found.
 *
 * @see PmdPlugin
 *
 * @see PmdExtension
 */
@CacheableTask
@Suppress("deprecation") // The targetJdk property and TargetJdk type are themselves deprecated.
abstract class Pmd : AbstractCodeQualityTask(), Reporting<PmdReports> {
    /**
     * The class path containing the PMD library to be used.
     */
    /**
     * The class path containing the PMD library to be used.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Classpath
    var pmdClasspath: FileCollection? = null
    /**
     * The built-in rule sets to be used. See the [official list](https://docs.pmd-code.org/pmd-doc-7.24.0/pmd_rules_java.html) of built-in rule sets.
     *
     * <pre>
     * ruleSets = ["basic", "braces"]
    </pre> *
     */
    /**
     * The built-in rule sets to be used. See the [official list](https://docs.pmd-code.org/pmd-doc-7.24.0/pmd_rules_java.html) of built-in rule sets.
     *
     * <pre>
     * ruleSets = ["basic", "braces"]
    </pre> *
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var ruleSets: MutableList<String>? = null

    @get:Deprecated(
        """This property has no effect for PMD 5.0 and later, which infer the language version from the rule sets.
          Scheduled to be removed in Gradle 10."""
    )
    @get:Input
    @set:Deprecated(
        """This property has no effect for PMD 5.0 and later, which infer the language version from the rule sets.
          Scheduled to be removed in Gradle 10."""
    )
    var targetJdk: TargetJdk? = null
        /**
         * The target JDK to use with PMD.
         *
         */
        get() {
            nagAboutTargetJdkDeprecation("getTargetJdk()")
            return field
        }
        /**
         * The target JDK to use with PMD.
         *
         */
        set(targetJdk) {
            nagAboutTargetJdkDeprecation("setTargetJdk(TargetJdk)")
            field = targetJdk
        }
    /**
     * The custom rule set to be used (if any). Replaces `ruleSetFiles`, except that it does not currently support multiple rule sets.
     *
     * See the [official documentation](https://docs.pmd-code.org/pmd-doc-7.24.0/pmd_userdocs_making_rulesets.html) for how to author a rule set.
     *
     * <pre>
     * ruleSetConfig = resources.text.fromFile(resources.file("config/pmd/myRuleSets.xml"))
    </pre> *
     *
     * @since 2.2
     */
    /**
     * The custom rule set to be used (if any). Replaces `ruleSetFiles`, except that it does not currently support multiple rule sets.
     *
     * See the [official documentation](https://docs.pmd-code.org/pmd-doc-7.24.0/pmd_userdocs_making_rulesets.html) for how to author a rule set.
     *
     * <pre>
     * ruleSetConfig = resources.text.fromFile(resources.file("config/pmd/myRuleSets.xml"))
    </pre> *
     *
     * @since 2.2
     */
    @get:Nested
    @get:Optional
    var ruleSetConfig: TextResource? = null
    /**
     * The custom rule set files to be used. See the [official documentation](https://docs.pmd-code.org/pmd-doc-7.24.0/pmd_userdocs_making_rulesets.html) for how to author a rule set file.
     * If you want to only use custom rule sets, you must clear `ruleSets`.
     *
     * <pre>
     * ruleSetFiles = files("config/pmd/myRuleSet.xml")
    </pre> *
     */
    /**
     * The custom rule set files to be used. See the [official documentation](https://docs.pmd-code.org/pmd-doc-7.24.0/pmd_userdocs_making_rulesets.html) for how to author a rule set file.
     * This adds to the default rule sets defined by [.getRuleSets].
     *
     * <pre>
     * ruleSetFiles = files("config/pmd/myRuleSets.xml")
    </pre> *
     */
    @get:ToBeReplacedByLazyProperty
    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFiles
    var ruleSetFiles: FileCollection? = null
    private val reports: PmdReports
    /**
     * Whether or not to write PMD results to `System.out`.
     *
     * @since 2.1
     */
    /**
     * Whether or not to write PMD results to `System.out`.
     *
     * @since 2.1
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isConsoleOutput: Boolean = false
    /**
     * Compile class path for the classes to be analyzed.
     *
     * The classes on this class path are used during analysis but aren't analyzed themselves.
     *
     * This is only well supported for PMD 5.2.1 or better.
     *
     * @since 2.8
     */
    /**
     * Compile class path for the classes to be analyzed.
     *
     * The classes on this class path are used during analysis but aren't analyzed themselves.
     *
     * This is only well supported for PMD 5.2.1 or better.
     *
     * @since 2.8
     */
    @get:ToBeReplacedByLazyProperty
    @get:Classpath
    @get:Optional
    var classpath: FileCollection? = null

    init {
        val objects = getObjectFactory()
        reports = objects.newInstance<PmdReportsImpl>(PmdReportsImpl::class.java, Describables.quoted("Task", getIdentityPath()))
    }

    @TaskAction
    fun run() {
        Companion.validate(this.rulesMinimumPriority.get())
        Companion.validateThreads(this.threads.get())

        val workQueue = getWorkerExecutor().processIsolation(Action { spec: ProcessWorkerSpec? -> configureForkOptions(spec!!.getForkOptions()) })
        workQueue.submit<PmdActionParameters>(PmdInvoker::class.java, Action { parameters: PmdActionParameters -> this.setupParameters(parameters) })
    }

    private fun setupParameters(parameters: PmdActionParameters) {
        parameters.antLibraryClasspath.setFrom(this.pmdClasspath)
        parameters.pmdClasspath.setFrom(this.pmdClasspath)
        parameters.targetJdk.set(whileDisabled<TargetJdk?>(org.gradle.internal.Factory { this.targetJdk }))
        parameters.ruleSets.set(this.ruleSets)
        parameters.ruleSetConfigFiles.from(this.ruleSetFiles)
        if (this.ruleSetConfig != null) {
            parameters.ruleSetConfigFiles.from(this.ruleSetConfig!!.asFile())
        }
        parameters.ignoreFailures.set(getIgnoreFailures())
        parameters.consoleOutput.set(this.isConsoleOutput)
        parameters.stdOutIsAttachedToTerminal.set(stdOutIsAttachedToTerminal())
        if (this.classpath != null) {
            parameters.auxClasspath.setFrom(this.classpath)
        }
        parameters.rulesMinimumPriority.set(this.rulesMinimumPriority)
        parameters.maxFailures.set(this.maxFailures)
        parameters.incrementalAnalysis.set(this.incrementalAnalysis)
        parameters.incrementalCacheFile.set(this.incrementalCacheFile)
        parameters.threads.set(this.threads)
        parameters.source.setFrom(getSource())
        parameters.enabledReports.set(getReports().getEnabled().stream().map<PmdActionParameters.EnabledReport> { report: SingleFileReport? ->
            val newReport = getObjectFactory().newInstance<PmdActionParameters.EnabledReport>(PmdActionParameters.EnabledReport::class.java)
            newReport.getName().set(report!!.getName())
            newReport.getOutputLocation().set(report.getOutputLocation())
            newReport
        }.collect(Collectors.toList()))
    }

    fun stdOutIsAttachedToTerminal(): Boolean {
        try {
            val consoleDetector = getInstance().get<ConsoleDetector?>(ConsoleDetector::class.java)
            val consoleMetaData = consoleDetector!!.console
            return consoleMetaData != null && consoleMetaData.isStdOutATerminal
        } catch (e: RuntimeException) {
            return false
        }
    }

    /**
     * Configures the reports to be generated by this task.
     */
    override fun reports(@DelegatesTo(value = PmdReports::class, strategy = Closure.DELEGATE_FIRST) closure: Closure<*>): PmdReports {
        return reports(ClosureBackedAction<PmdReports>(closure))
    }

    /**
     * Configures the reports to be generated by this task.
     *
     * @since 3.0
     */
    override fun reports(configureAction: Action<in PmdReports>): PmdReports {
        configureAction.execute(reports)
        return reports
    }

    /**
     * {@inheritDoc}
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @ToBeReplacedByLazyProperty
    override fun getSource(): FileTree {
        return super.getSource()
    }

    /**
     * The reports to be generated by this task.
     */
    @Nested
    override fun getReports(): PmdReports {
        return reports
    }

    @get:Input
    abstract val maxFailures: Property<Int>?

    @get:Input
    abstract val rulesMinimumPriority: Property<Int>?

    @get:Internal
    abstract val incrementalAnalysis: Property<Boolean>?

    @get:ToBeReplacedByLazyProperty
    @get:LocalState
    val incrementalCacheFile: File
        /**
         * Path to the incremental cache file, if incremental analysis is used.
         *
         * @since 5.6
         */
        get() = File(getTemporaryDir(), "incremental.cache")

    @get:Input
    abstract val threads: Property<Int>?

    companion object {
        /**
         * Validates the value is a valid PMD rules minimum priority (1-5)
         *
         * @param value rules minimum priority threshold
         */
        fun validate(value: Int) {
            if (value > 5 || value < 1) {
                throw InvalidUserDataException(String.format("Invalid rulesMinimumPriority '%d'.  Valid range 1 (highest) to 5 (lowest).", value))
            }
        }

        /**
         * Validates the number of threads used by PMD.
         *
         * @param value the number of threads used by PMD
         */
        private fun validateThreads(value: Int) {
            if (value < 0) {
                throw InvalidUserDataException(String.format("Invalid number of threads '%d'.  Number should not be negative.", value))
            }
        }

        private fun nagAboutTargetJdkDeprecation(methodWithParams: String) {
            deprecateMethod(Pmd::class.java, methodWithParams)
                .withAdvice("This property has no effect for PMD 5.0 and later, which infer the language version from the rule sets. Remove the targetJdk configuration from your build.")!!
                .willBeRemovedInGradle10()
                .withUpgradeGuideSection(9, "deprecated_pmd_target_jdk")!!
                .nagUser()
        }
    }
}
