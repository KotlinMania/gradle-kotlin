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
package org.gradle

import com.google.common.collect.Lists
import com.google.common.collect.Sets
import org.apache.commons.lang3.builder.EqualsBuilder
import org.apache.commons.lang3.builder.HashCodeBuilder
import org.gradle.api.Incubating
import org.gradle.api.artifacts.verification.DependencyVerificationMode
import org.gradle.api.launcher.cli.WelcomeMessageConfiguration
import org.gradle.api.launcher.cli.WelcomeMessageDisplayMode
import org.gradle.api.logging.configuration.LoggingConfiguration
import org.gradle.concurrent.ParallelismConfiguration
import org.gradle.initialization.BuildLayoutParameters
import org.gradle.initialization.CompositeInitScriptFinder
import org.gradle.initialization.DistributionInitScriptFinder
import org.gradle.initialization.UserHomeInitScriptFinder
import org.gradle.internal.DefaultTaskExecutionRequest
import org.gradle.internal.FileUtils
import org.gradle.internal.RunDefaultTasksExecutionRequest
import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.deprecation.StartParameterDeprecations.nagOnIsConfigurationCacheRequested
import org.gradle.internal.logging.DefaultLoggingConfiguration
import java.io.File
import java.io.Serializable
import java.util.Collections
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.Iterable
import kotlin.collections.LinkedHashSet
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.mutableListOf

/**
 *
 * `StartParameter` defines the configuration used by a Gradle instance to execute a build. The properties of `StartParameter` generally correspond to the command-line options of
 * Gradle.
 *
 *
 * You can obtain an instance of a `StartParameter` by either creating a new one, or duplicating an existing one using [.newInstance] or [.newBuild].
 */
open class StartParameter protected constructor(layoutParameters: BuildLayoutParameters) : LoggingConfiguration, ParallelismConfiguration, Serializable {
    private val loggingConfiguration = DefaultLoggingConfiguration()
    private val parallelismConfiguration = DefaultParallelismConfiguration()
    private var taskRequests: MutableList<TaskExecutionRequest> = ArrayList<TaskExecutionRequest>()
    private var excludedTaskNames: MutableSet<String?> = LinkedHashSet<String?>()

    /**
     * Returns true if project dependencies are to be built, false if they should not be. The default is true.
     */
    var isBuildProjectDependencies: Boolean = true
        private set
    private var currentDir: File?
    private var projectDir: File?
    private var projectProperties: MutableMap<String?, String?> = HashMap<String?, String?>()
    private var systemPropertiesArgs: MutableMap<String?, String?> = HashMap<String?, String?>()
    private var gradleUserHomeDir: File?
    protected var gradleHomeDir: File?
    private var settingsFile: File? = null
    private var buildFile: File? = null
    private var initScripts: MutableList<File> = ArrayList<File>()
    private var dryRun = false
    private var rerunTasks = false
    private var taskGraph = false
    private var profile = false
    private var continueOnFailure = false
    private var offline = false
    private var projectCacheDir: File? = null
    private var refreshDependencies = false
    private var buildCacheEnabled = false
    /**
     * Whether build cache debug logging is enabled.
     *
     * @since 4.6
     */
    /**
     * Whether build cache debug logging is enabled.
     *
     * @since 4.6
     */
    var isBuildCacheDebugLogging: Boolean = false
    private var configureOnDemand = false
    var isContinuous: Boolean = false
    private var includedBuilds: MutableList<File> = ArrayList<File>()
    /**
     * Returns true if a Build Scan should be created.
     *
     * @since 3.4
     */
    /**
     * Specifies whether a Build Scan should be created.
     *
     * @since 3.4
     */
    var isBuildScan: Boolean = false
    /**
     * Returns true when Build Scan creation is explicitly disabled.
     *
     * @since 3.4
     */
    /**
     * Specifies whether Build Scan creation is explicitly disabled.
     *
     * @since 3.4
     */
    var isNoBuildScan: Boolean = false
    private var writeDependencyLocks = false
    private var writeDependencyVerifications: MutableList<String?>? = mutableListOf<String?>()

    /**
     * Returns the list of modules that are to be allowed to update their version compared to the lockfile.
     *
     * @return a list of modules allowed to have a version update
     * @since 4.8
     */
    var lockedDependenciesToUpdate: MutableList<String> = mutableListOf<String?>()
        private set
    /**
     * Returns the dependency verification mode.
     *
     * @since 6.2
     */
    /**
     * Sets the dependency verification mode. There are three different modes:
     *
     *  * *strict*, the default, verification is enabled as soon as a dependency verification file is present.
     *  * *lenient*, in this mode, failure to verify a checksum, missing checksums or signatures will be logged
     * but will not fail the build. This mode should only be used when updating dependencies as it is inherently unsafe.
     *  * *off*, this mode disables all verifications
     *
     *
     * @param verificationMode the verification mode to use
     * @since 6.2
     */
    var dependencyVerificationMode: DependencyVerificationMode? = DependencyVerificationMode.STRICT
    private var refreshKeys = false
    private var exportKeys = false
    private var welcomeMessageConfiguration: WelcomeMessageConfiguration? = WelcomeMessageConfiguration(WelcomeMessageDisplayMode.ONCE)

    var logLevel: LogLevel?
        /**
         * {@inheritDoc}
         */
        get() = loggingConfiguration.getLogLevel()
        /**
         * {@inheritDoc}
         */
        set(logLevel) {
            loggingConfiguration.setLogLevel(logLevel)
        }

    var showStacktrace: ShowStacktrace?
        /**
         * {@inheritDoc}
         */
        get() = loggingConfiguration.getShowStacktrace()
        /**
         * {@inheritDoc}
         */
        set(showStacktrace) {
            loggingConfiguration.setShowStacktrace(showStacktrace)
        }

    var consoleOutput: ConsoleOutput?
        /**
         * {@inheritDoc}
         */
        get() = loggingConfiguration.getConsoleOutput()
        /**
         * {@inheritDoc}
         */
        set(consoleOutput) {
            loggingConfiguration.setConsoleOutput(consoleOutput)
        }

    var consoleUnicodeSupport: ConsoleUnicodeSupport?
        /**
         * {@inheritDoc}
         */
        get() = loggingConfiguration.getConsoleUnicodeSupport()
        /**
         * {@inheritDoc}
         */
        set(unicodeSupport) {
            loggingConfiguration.setConsoleUnicodeSupport(unicodeSupport)
        }

    var warningMode: WarningMode?
        /**
         * {@inheritDoc}
         */
        get() = loggingConfiguration.getWarningMode()
        /**
         * {@inheritDoc}
         */
        set(warningMode) {
            loggingConfiguration.setWarningMode(warningMode)
        }

    var isNonInteractive: Boolean
        /**
         * {@inheritDoc}
         */
        get() = loggingConfiguration.isNonInteractive()
        /**
         * {@inheritDoc}
         */
        set(nonInteractive) {
            this.loggingConfiguration.setNonInteractive(nonInteractive)
        }

    /**
     * Sets the project's cache location. Set to null to use the default location.
     */
    fun setProjectCacheDir(projectCacheDir: File?) {
        this.projectCacheDir = projectCacheDir
    }

    /**
     * Returns the project's cache dir.
     *
     *
     * Note that this directory is managed by Gradle, and it assumes full ownership of its contents.
     * Plugins and build logic should not store or modify any files or directories within this cache directory.
     *
     * @return project's cache dir, or null if the default location is to be used.
     */
    fun getProjectCacheDir(): File? {
        return projectCacheDir
    }

    /**
     * Creates a `StartParameter` with default values. This is roughly equivalent to running Gradle on the command-line with no arguments.
     */
    constructor() : this(BuildLayoutParameters())

    /**
     * Creates a `StartParameter` initialized from the given [BuildLayoutParameters].
     *
     * @since 7.0
     */
    init {
        gradleHomeDir = layoutParameters.getGradleInstallationHomeDir()
        currentDir = layoutParameters.getCurrentDir()
        projectDir = layoutParameters.getProjectDir()
        gradleUserHomeDir = layoutParameters.getGradleUserHomeDir()
    }

    /**
     * Duplicates this `StartParameter` instance.
     *
     * @return the new parameters.
     */
    open fun newInstance(): StartParameter? {
        return prepareNewInstance(StartParameter())
    }

    protected fun prepareNewInstance(p: StartParameter): StartParameter {
        prepareNewBuild(p)
        p.warningMode = warningMode
        p.buildFile = buildFile
        p.projectDir = projectDir
        p.settingsFile = settingsFile
        p.taskRequests = ArrayList<TaskExecutionRequest>(taskRequests)
        p.excludedTaskNames = LinkedHashSet<String?>(excludedTaskNames)
        p.isBuildProjectDependencies = this.isBuildProjectDependencies
        p.currentDir = currentDir
        p.projectProperties = HashMap<String?, String?>(projectProperties)
        p.systemPropertiesArgs = HashMap<String?, String?>(systemPropertiesArgs)
        p.initScripts = ArrayList<File>(initScripts)
        p.includedBuilds = ArrayList<File>(includedBuilds)
        p.projectCacheDir = projectCacheDir
        return p
    }

    /**
     *
     * Creates the parameters for a new build, using these parameters as a template. Copies the environmental properties from this parameter (eg Gradle user home dir, etc), but does not copy the
     * build specific properties (eg task names).
     *
     * @return The new parameters.
     */
    open fun newBuild(): StartParameter {
        return prepareNewBuild(StartParameter())
    }

    protected open fun prepareNewBuild(p: StartParameter): StartParameter {
        p.gradleUserHomeDir = gradleUserHomeDir
        p.gradleHomeDir = gradleHomeDir
        p.logLevel = this.logLevel
        p.consoleOutput = consoleOutput
        p.consoleUnicodeSupport = consoleUnicodeSupport
        p.showStacktrace = this.showStacktrace
        p.warningMode = warningMode
        p.profile = profile
        p.continueOnFailure = continueOnFailure
        p.offline = offline
        p.rerunTasks = rerunTasks
        p.refreshDependencies = refreshDependencies
        p.isParallelProjectExecutionEnabled = this.isParallelProjectExecutionEnabled
        p.buildCacheEnabled = buildCacheEnabled
        p.configureOnDemand = configureOnDemand
        p.maxWorkerCount = this.maxWorkerCount
        p.systemPropertiesArgs = HashMap<String?, String?>(systemPropertiesArgs)
        p.writeDependencyLocks = writeDependencyLocks
        p.writeDependencyVerifications = writeDependencyVerifications
        p.lockedDependenciesToUpdate = ArrayList<String>(lockedDependenciesToUpdate)
        p.dependencyVerificationMode = this.dependencyVerificationMode
        p.refreshKeys = refreshKeys
        p.exportKeys = exportKeys
        p.welcomeMessageConfiguration = welcomeMessageConfiguration
        p.dryRun = dryRun
        p.taskGraph = taskGraph
        return p
    }

    override fun equals(obj: Any?): Boolean {
        return EqualsBuilder.reflectionEquals(this, obj)
    }

    override fun hashCode(): Int {
        return HashCodeBuilder.reflectionHashCode(this)
    }

    var taskNames: Iterable<String?>? = null
        /**
         * Returns the names of the tasks to execute in this build. When empty, the default tasks for the project will be executed. If [TaskExecutionRequest]s are set for this build then names from these task parameters are returned.
         *
         *
         * **Note that this will also return entries for each task ARGUMENT as well.**>
         *
         * @return the names of the tasks to execute in this build. Never returns null.
         */
        get() {
            val taskNames: MutableList<String?> = ArrayList<String?>()
            for (taskRequest in taskRequests) {
                taskNames.addAll(taskRequest.getArgs())
            }
            return taskNames
        }
        /**
         *
         * Sets the tasks to execute in this build. Set to an empty list, or null, to execute the default tasks for the project. The tasks are executed in the order provided, subject to dependency
         * between the tasks.
         *
         * @param taskNames the names of the tasks to execute in this build.
         */
        set(taskNames) {
            if (taskNames == null) {
                this.taskRequests = mutableListOf<TaskExecutionRequest?>(RunDefaultTasksExecutionRequest())
            } else {
                this.taskRequests = mutableListOf<T>(DefaultTaskExecutionRequest.of(taskNames))
            }
        }

    /**
     * Returns the tasks to execute in this build. When empty, the default tasks for the project will be executed.
     *
     * @return the tasks to execute in this build. Never returns null.
     */
    fun getTaskRequests(): MutableList<TaskExecutionRequest> {
        return taskRequests
    }

    /**
     *
     * Sets the task parameters to execute in this build. Set to an empty list, to execute the default tasks for the project. The tasks are executed in the order provided, subject to dependency
     * between the tasks.
     *
     * @param taskParameters the tasks to execute in this build.
     */
    fun setTaskRequests(taskParameters: Iterable<out TaskExecutionRequest?>) {
        this.taskRequests = Lists.newArrayList<TaskExecutionRequest?>(taskParameters)
    }

    /**
     * Returns the names of the tasks to be excluded from this build. When empty, no tasks are excluded from the build.
     *
     * @return The names of the excluded tasks. Returns an empty set if there are no such tasks.
     */
    fun getExcludedTaskNames(): MutableSet<String?> {
        return excludedTaskNames
    }

    /**
     * Sets the tasks to exclude from this build.
     *
     * @param excludedTaskNames The task names.
     */
    fun setExcludedTaskNames(excludedTaskNames: Iterable<String?>) {
        this.excludedTaskNames = Sets.newLinkedHashSet<String?>(excludedTaskNames)
    }

    /**
     * Returns the directory to use to select the default project, and to search for the settings file.
     *
     * @return The current directory. Never returns null.
     */
    fun getCurrentDir(): File? {
        return currentDir
    }

    /**
     * Sets the directory to use to select the default project, and to search for the settings file. Set to null to use the default current directory.
     *
     * @param currentDir The directory. Set to null to use the default.
     */
    fun setCurrentDir(currentDir: File?) {
        if (currentDir != null) {
            this.currentDir = FileUtils.canonicalize(currentDir)
        } else {
            this.currentDir = BuildLayoutParameters().getCurrentDir()
        }
    }

    /**
     * Key-value map of project properties. These are derived from the command-line arguments (-P) and do not reflect the final project properties available.
     *
     * Changing these properties may be too late to impact the build configuration.
     *
     * @return map of properties
     */
    open fun getProjectProperties(): MutableMap<String?, String?>? {
        return projectProperties
    }

    /**
     * Sets the project properties. This completely replaces the map of project properties.
     *
     * Changing these properties may be too late to impact the build configuration.
     *
     * @param projectProperties new map of properties
     */
    fun setProjectProperties(projectProperties: MutableMap<String?, String?>) {
        this.projectProperties = projectProperties
    }


    /**
     * Key-value map of system properties. These are derived from the command-line arguments (-D) and do not reflect the final system properties available.
     *
     * Changing these properties may be too late to impact the build configuration.
     *
     * @return map of properties
     */
    fun getSystemPropertiesArgs(): MutableMap<String?, String?> {
        return systemPropertiesArgs
    }

    /**
     * Sets the system properties. This completely replaces the map of system properties.
     *
     * Changing these properties may be too late to impact the build configuration.
     *
     * @param systemPropertiesArgs new map of properties
     */
    fun setSystemPropertiesArgs(systemPropertiesArgs: MutableMap<String?, String?>) {
        this.systemPropertiesArgs = systemPropertiesArgs
    }

    /**
     * Returns the directory to use as the user home directory.
     *
     * @return The home directory.
     */
    fun getGradleUserHomeDir(): File? {
        return gradleUserHomeDir
    }

    /**
     * Sets the directory to use as the user home directory. Set to null to use the default directory.
     *
     * @param gradleUserHomeDir The home directory. May be null.
     */
    fun setGradleUserHomeDir(gradleUserHomeDir: File?) {
        this.gradleUserHomeDir = if (gradleUserHomeDir == null) BuildLayoutParameters().getGradleUserHomeDir() else FileUtils.canonicalize(gradleUserHomeDir)
    }

    /**
     * Specifies whether project dependencies should be built. Defaults to true.
     *
     * @return this
     */
    fun setBuildProjectDependencies(build: Boolean): StartParameter {
        this.isBuildProjectDependencies = build
        return this
    }

    /**
     * Is the build running as a dry-run? Dry-run means task actions do not execute for the root build.
     *
     * @return true if the build is running as a dry-run
     */
    fun isDryRun(): Boolean {
        return dryRun
    }

    /**
     * Enables or disables dry-run.
     *
     * @param dryRun true if the build should run as a dry-run
     */
    fun setDryRun(dryRun: Boolean) {
        this.dryRun = dryRun
    }

    /**
     * Adds the given file to the list of init scripts that are run before the build starts.  This list is in addition to the default init scripts.
     *
     * @param initScriptFile The init scripts.
     */
    fun addInitScript(initScriptFile: File?) {
        initScripts.add(initScriptFile!!)
    }

    /**
     * Sets the list of init scripts to be run before the build starts. This list is in addition to the default init scripts.
     *
     * @param initScripts The init scripts.
     */
    fun setInitScripts(initScripts: MutableList<File>) {
        this.initScripts = initScripts
    }

    /**
     * Returns all explicitly added init scripts that will be run before the build starts.  This list does not contain the user init script located in ${user.home}/.gradle/init.gradle, even though
     * that init script will also be run.
     *
     * @return list of all explicitly added init scripts.
     */
    fun getInitScripts(): MutableList<File?> {
        return Collections.unmodifiableList<File?>(initScripts)
    }

    open val allInitScripts: MutableList<File?>
        /**
         * Returns all init scripts, including explicit init scripts and implicit init scripts.
         *
         * @return All init scripts, including explicit init scripts and implicit init scripts.
         */
        get() {
            val initScriptFinder = CompositeInitScriptFinder(
                UserHomeInitScriptFinder(getGradleUserHomeDir()),
                DistributionInitScriptFinder(gradleHomeDir)
            )

            val scripts: MutableList<File> = ArrayList<File>(getInitScripts())
            initScriptFinder.findScripts(scripts)
            return Collections.unmodifiableList<File?>(scripts)
        }

    /**
     * Sets the project directory to use to select the default project. Use null to use the default criteria for selecting the default project.
     *
     * @param projectDir The project directory. May be null.
     */
    fun setProjectDir(projectDir: File?) {
        if (projectDir == null) {
            setCurrentDir(null)
            this.projectDir = null
        } else {
            val canonicalFile = FileUtils.canonicalize(projectDir)
            currentDir = canonicalFile
            this.projectDir = canonicalFile
        }
    }

    /**
     * Returns the project dir to use to select the default project.
     *
     * Returns null when the build file is not used to select the default project
     *
     * @return The project dir. May be null.
     */
    fun getProjectDir(): File? {
        return projectDir
    }

    /**
     * Specifies if a profile report should be generated.
     *
     * @param profile true if a profile report should be generated
     */
    fun setProfile(profile: Boolean) {
        this.profile = profile
    }

    /**
     * Returns true if a profile report will be generated.
     */
    fun isProfile(): Boolean {
        return profile
    }

    /**
     * Specifies whether the build should continue on task failure. The default is false.
     */
    fun isContinueOnFailure(): Boolean {
        return continueOnFailure
    }

    /**
     * Specifies whether the build should continue on task failure. The default is false.
     */
    fun setContinueOnFailure(continueOnFailure: Boolean) {
        this.continueOnFailure = continueOnFailure
    }

    /**
     * Specifies whether the build should be performed offline (ie without network access).
     */
    fun isOffline(): Boolean {
        return offline
    }

    /**
     * Specifies whether the build should be performed offline (ie without network access).
     */
    fun setOffline(offline: Boolean) {
        this.offline = offline
    }

    /**
     * Specifies whether the dependencies should be refreshed..
     */
    fun isRefreshDependencies(): Boolean {
        return refreshDependencies
    }

    /**
     * Specifies whether the dependencies should be refreshed..
     */
    fun setRefreshDependencies(refreshDependencies: Boolean) {
        this.refreshDependencies = refreshDependencies
    }

    /**
     * Specifies whether the cached task results should be ignored and each task should be forced to be executed.
     */
    fun isRerunTasks(): Boolean {
        return rerunTasks
    }

    /**
     * Specifies whether the cached task results should be ignored and each task should be forced to be executed.
     */
    fun setRerunTasks(rerunTasks: Boolean) {
        this.rerunTasks = rerunTasks
    }

    /**
     * Specifies whether the task graph should be printed.
     *
     * @since 9.1.0
     */
    fun isTaskGraph(): Boolean {
        return taskGraph
    }

    /**
     * Specifies whether the task graph should be printed.
     *
     * @since 9.1.0
     */
    fun setTaskGraph(taskGraph: Boolean) {
        this.taskGraph = taskGraph
    }

    var isParallelProjectExecutionEnabled: Boolean
        /**
         * {@inheritDoc}
         */
        get() = parallelismConfiguration.isParallelProjectExecutionEnabled()
        /**
         * {@inheritDoc}
         */
        set(parallelProjectExecution) {
            parallelismConfiguration.setParallelProjectExecutionEnabled(parallelProjectExecution)
        }

    /**
     * Returns true if the build cache is enabled.
     *
     * @since 3.5
     */
    fun isBuildCacheEnabled(): Boolean {
        return buildCacheEnabled
    }

    /**
     * Enables/disables the build cache.
     *
     * @since 3.5
     */
    fun setBuildCacheEnabled(buildCacheEnabled: Boolean) {
        this.buildCacheEnabled = buildCacheEnabled
    }

    var maxWorkerCount: Int
        /**
         * {@inheritDoc}
         */
        get() = parallelismConfiguration.getMaxWorkerCount()
        /**
         * {@inheritDoc}
         */
        set(maxWorkerCount) {
            parallelismConfiguration.setMaxWorkerCount(maxWorkerCount)
        }

    /**
     * If the configure-on-demand mode is active
     */
    @Incubating
    fun isConfigureOnDemand(): Boolean {
        return configureOnDemand
    }

    override fun toString(): String {
        return ("StartParameter{"
                + "taskRequests=" + taskRequests
                + ", excludedTaskNames=" + excludedTaskNames
                + ", buildProjectDependencies=" + this.isBuildProjectDependencies
                + ", currentDir=" + currentDir
                + ", projectDir=" + projectDir
                + ", projectProperties=" + projectProperties
                + ", systemPropertiesArgs=" + systemPropertiesArgs
                + ", gradleUserHomeDir=" + gradleUserHomeDir
                + ", gradleHome=" + gradleHomeDir
                + ", logLevel=" + this.logLevel
                + ", showStacktrace=" + this.showStacktrace
                + ", settingsFile=" + settingsFile
                + ", buildFile=" + buildFile
                + ", initScripts=" + initScripts
                + ", dryRun=" + dryRun
                + ", rerunTasks=" + rerunTasks
                + ", taskGraph=" + taskGraph
                + ", profile=" + profile
                + ", continueOnFailure=" + continueOnFailure
                + ", offline=" + offline
                + ", projectCacheDir=" + projectCacheDir
                + ", refreshDependencies=" + refreshDependencies
                + ", buildCacheEnabled=" + buildCacheEnabled
                + ", buildCacheDebugLogging=" + this.isBuildCacheDebugLogging
                + ", parallelProjectExecution=" + this.isParallelProjectExecutionEnabled
                + ", configureOnDemand=" + configureOnDemand
                + ", continuous=" + this.isContinuous
                + ", maxWorkerCount=" + this.maxWorkerCount
                + ", includedBuilds=" + includedBuilds
                + ", buildScan=" + this.isBuildScan
                + ", noBuildScan=" + this.isNoBuildScan
                + ", writeDependencyLocks=" + writeDependencyLocks
                + ", writeDependencyVerifications=" + writeDependencyVerifications
                + ", lockedDependenciesToUpdate=" + lockedDependenciesToUpdate
                + ", verificationMode=" + this.dependencyVerificationMode
                + ", refreshKeys=" + refreshKeys
                + ", exportKeys=" + exportKeys
                + ", welcomeMessageConfiguration=" + welcomeMessageConfiguration
                + '}')
    }

    /**
     * Package scope for testing purposes.
     */
    fun setGradleHomeDir(gradleHomeDir: File?) {
        this.gradleHomeDir = gradleHomeDir
    }

    @Incubating
    fun setConfigureOnDemand(configureOnDemand: Boolean) {
        this.configureOnDemand = configureOnDemand
    }

    fun includeBuild(includedBuild: File?) {
        includedBuilds.add(includedBuild!!)
    }

    fun setIncludedBuilds(includedBuilds: MutableList<File>) {
        this.includedBuilds = includedBuilds
    }

    fun getIncludedBuilds(): MutableList<File?> {
        return Collections.unmodifiableList<File?>(includedBuilds)
    }

    /**
     * Specifies whether dependency resolution needs to be persisted for locking
     *
     * @since 4.8
     */
    fun setWriteDependencyLocks(writeDependencyLocks: Boolean) {
        this.writeDependencyLocks = writeDependencyLocks
    }

    /**
     * Returns true when dependency resolution is to be persisted for locking
     *
     * @since 4.8
     */
    fun isWriteDependencyLocks(): Boolean {
        return writeDependencyLocks
    }

    /**
     * Indicates that specified dependencies are to be allowed to update their version.
     * Implicitly activates dependency locking persistence.
     *
     * @param lockedDependenciesToUpdate the modules to update
     * @see .isWriteDependencyLocks
     * @since 4.8
     */
    fun setLockedDependenciesToUpdate(lockedDependenciesToUpdate: MutableList<String?>) {
        this.lockedDependenciesToUpdate = Lists.newArrayList<String?>(lockedDependenciesToUpdate)
        this.writeDependencyLocks = true
    }

    /**
     * Indicates if a dependency verification metadata file should be written at the
     * end of this build. If the list is not empty, then it means we need to generate
     * or update the dependency verification file with the checksums specified in the
     * list.
     *
     * @since 6.1
     */
    fun getWriteDependencyVerifications(): MutableList<String?>? {
        return writeDependencyVerifications
    }

    /**
     * Tells if a dependency verification metadata file should be written at the end
     * of this build.
     *
     * @param checksums the list of checksums to generate
     * @since 6.1
     */
    fun setWriteDependencyVerifications(checksums: MutableList<String?>?) {
        this.writeDependencyVerifications = checksums
    }

    /**
     * Sets the key refresh flag.
     *
     * @param refresh If set to true, missing keys will be checked again. By default missing keys are cached for 24 hours.
     * @since 6.2
     */
    fun setRefreshKeys(refresh: Boolean) {
        refreshKeys = refresh
    }

    /**
     * If true, Gradle will try to download missing keys again.
     *
     * @since 6.2
     */
    fun isRefreshKeys(): Boolean {
        return refreshKeys
    }

    /**
     * If true, after writing the dependency verification file, a public keyring
     * file will be generated with all keys seen during generation of the file.
     *
     * This file can then be used as a source for public keys instead of reaching
     * out public key servers.
     *
     * @return true if keys should be exported
     * @since 6.2
     */
    fun isExportKeys(): Boolean {
        return exportKeys
    }

    /**
     * If true, after writing the dependency verification file, a public keyring
     * file will be generated with all keys seen during generation of the file.
     *
     * This file can then be used as a source for public keys instead of reaching
     * out public key servers.
     *
     * @param exportKeys set to true if keys should be exported
     * @since 6.2
     */
    fun setExportKeys(exportKeys: Boolean) {
        this.exportKeys = exportKeys
    }

    /**
     * Returns when to display a welcome message on the command line.
     *
     * @return The welcome message configuration.
     * @see WelcomeMessageDisplayMode
     *
     * @since 7.5
     */
    @Incubating
    fun getWelcomeMessageConfiguration(): WelcomeMessageConfiguration? {
        return welcomeMessageConfiguration
    }

    /**
     * Updates when to display a welcome message on the command line.
     *
     * @param welcomeMessageConfiguration The welcome message configuration.
     * @see WelcomeMessageDisplayMode
     *
     * @since 7.5
     */
    @Incubating
    fun setWelcomeMessageConfiguration(welcomeMessageConfiguration: WelcomeMessageConfiguration?) {
        this.welcomeMessageConfiguration = welcomeMessageConfiguration
    }

    @get:Deprecated("Use {@link org.gradle.api.configuration.BuildFeatures#getConfigurationCache() Configuration Cache build feature} instead.")
    @get:Incubating
    open val isConfigurationCacheRequested: Boolean
        /**
         * Returns true if configuration caching has been requested. Note that the configuration cache may not necessarily be used even when requested, for example
         * it may be disabled due to the presence of configuration cache problems. It is also currently not used during an IDE import/sync.
         *
         * @since 7.6
         */
        get() {
            nagOnIsConfigurationCacheRequested()
            return false
        }

    companion object {
        val GRADLE_USER_HOME_PROPERTY_KEY: String = BuildLayoutParameters.GRADLE_USER_HOME_PROPERTY_KEY

        /**
         * The default user home directory.
         */
        val DEFAULT_GRADLE_USER_HOME: File? = BuildLayoutParameters().getGradleUserHomeDir()
    }
}
