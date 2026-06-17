/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal

import org.gradle.StartParameter
import org.gradle.initialization.BuildLayoutParameters
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.initialization.layout.BuildLayoutConfiguration
import org.gradle.internal.buildoption.Option
import org.gradle.internal.configuration.inputs.InstrumentedInputs
import org.gradle.internal.deprecation.StartParameterDeprecations
import org.gradle.internal.watch.registry.WatchMode
import java.time.Duration

open class StartParameterInternal : StartParameter {
    private var watchFileSystemMode: WatchMode? = WatchMode.DEFAULT
    private var vfsVerboseLogging = false

    private var configurationCache = Option.Value.defaultValue<Boolean?>(false)
    private var isolatedProjects: Option.Value<Boolean?>? = Option.Value.defaultValue<Boolean?>(false)
    private var configurationCacheProblems: StartParameterBuildOptions.ConfigurationCacheProblemsOption.Value? = StartParameterBuildOptions.ConfigurationCacheProblemsOption.Value.FAIL
    private var configurationCacheDebug = false
    var isConfigurationCacheIgnoreInputsDuringStore: Boolean = false
    private var configurationCacheIgnoreUnsupportedBuildEventsListeners = false
    private var configurationCacheMaxProblems = 512
    private var configurationCacheIgnoredFileSystemCheckInputs: String? = null
    private var configurationCacheParallel = false
    private var configurationCacheReadOnly = false
    private var configurationCacheRecreateCache = false
    private var configurationCacheQuiet = false
    private var configurationCacheEntriesPerKey = 1
    private var configurationCacheIntegrityCheckEnabled = false
    private var configurationCacheHeapDumpDir: String? = null
    private var configurationCacheFineGrainedPropertyTracking = true
    var isIsolatedProjectsDiagnostics: Boolean = false
        private set
    var isIsolatedProjectsDangerouslyIgnoreProblems: Boolean = false
        private set
    var isSearchUpwards: Boolean = true
        private set
    var isUseEmptySettings: Boolean = false
        private set
    @JvmField
    var continuousBuildQuietPeriod: Duration? = Duration.ofMillis(250)
    var isPropertyUpgradeReportEnabled: Boolean = false
    var isProblemReportGenerationEnabled: Boolean = true
        private set
    private var daemonJvmCriteriaConfigured = false
    private var parallelToolingModelBuilding: Option.Value<Boolean?>? = Option.Value.defaultValue<Boolean?>(false)
    @JvmField
    var develocityUrl: String? = null
    @JvmField
    var develocityPluginVersion: String? = null

    constructor()

    protected constructor(layoutParameters: BuildLayoutParameters) : super(layoutParameters)

    override fun newInstance(): StartParameterInternal? {
        return prepareNewInstance(StartParameterInternal()) as StartParameterInternal?
    }

    override fun newBuild(): StartParameterInternal {
        return prepareNewBuild(StartParameterInternal())
    }

    override fun prepareNewBuild(startParameter: StartParameter): StartParameterInternal {
        val p = super.prepareNewBuild(startParameter) as StartParameterInternal
        p.watchFileSystemMode = watchFileSystemMode
        p.vfsVerboseLogging = vfsVerboseLogging
        p.configurationCache = configurationCache
        p.isolatedProjects = isolatedProjects
        p.configurationCacheProblems = configurationCacheProblems
        p.configurationCacheMaxProblems = configurationCacheMaxProblems
        p.configurationCacheIgnoredFileSystemCheckInputs = configurationCacheIgnoredFileSystemCheckInputs
        p.configurationCacheIgnoreUnsupportedBuildEventsListeners = configurationCacheIgnoreUnsupportedBuildEventsListeners
        p.configurationCacheDebug = configurationCacheDebug
        p.configurationCacheParallel = configurationCacheParallel
        p.configurationCacheReadOnly = configurationCacheReadOnly
        p.configurationCacheRecreateCache = configurationCacheRecreateCache
        p.configurationCacheQuiet = configurationCacheQuiet
        p.configurationCacheEntriesPerKey = configurationCacheEntriesPerKey
        p.configurationCacheIntegrityCheckEnabled = configurationCacheIntegrityCheckEnabled
        p.configurationCacheHeapDumpDir = configurationCacheHeapDumpDir
        p.configurationCacheFineGrainedPropertyTracking = configurationCacheFineGrainedPropertyTracking
        p.isIsolatedProjectsDiagnostics = this.isIsolatedProjectsDiagnostics
        p.isIsolatedProjectsDangerouslyIgnoreProblems = this.isIsolatedProjectsDangerouslyIgnoreProblems
        p.isSearchUpwards = this.isSearchUpwards
        p.isUseEmptySettings = this.isUseEmptySettings
        p.isProblemReportGenerationEnabled = this.isProblemReportGenerationEnabled
        p.daemonJvmCriteriaConfigured = daemonJvmCriteriaConfigured
        p.parallelToolingModelBuilding = parallelToolingModelBuilding
        return p
    }

    @Suppress("deprecation")
    override fun getProjectProperties(): MutableMap<String?, String?>? {
        // We avoid using the more usual `Instrumented` directly because a class dependency on it bloats up the Shaded TAPI Jar
        InstrumentedInputs.listener().startParameterProjectPropertiesObserved()
        return super.getProjectProperties()
    }

    val projectPropertiesUntracked: MutableMap<String?, String?>?
        /**
         * Returns the properties without making their snapshot a build input for Configuration Caching purposes.
         *
         *
         * This should be used with care because failing to track properties can lead to false-positive cache hits.
         */
        get() = super.getProjectProperties()

    @JvmField
    var gradleHomeDir: File?

    fun doNotSearchUpwards() {
        this.isSearchUpwards = false
    }

    fun useEmptySettings() {
        this.isUseEmptySettings = true
    }

    fun getWatchFileSystemMode(): WatchMode? {
        return watchFileSystemMode
    }

    fun setWatchFileSystemMode(watchFileSystemMode: WatchMode?) {
        this.watchFileSystemMode = watchFileSystemMode
    }

    fun isVfsVerboseLogging(): Boolean {
        return vfsVerboseLogging
    }

    fun setVfsVerboseLogging(vfsVerboseLogging: Boolean) {
        this.vfsVerboseLogging = vfsVerboseLogging
    }

    /**
     * Is the configuration cache requested? Note: depending on the build action, this may not be the final value for this option.
     *
     * Consider querying [BuildModelParameters] instead.
     */
    fun getConfigurationCache(): Option.Value<Boolean?> {
        return configurationCache
    }

    fun setConfigurationCache(configurationCache: Option.Value<Boolean?>) {
        this.configurationCache = configurationCache
    }

    fun getIsolatedProjects(): Option.Value<Boolean?>? {
        return isolatedProjects
    }

    @Suppress("deprecation")
    override fun isConfigurationCacheRequested(): Boolean {
        StartParameterDeprecations.nagOnIsConfigurationCacheRequested()
        return configurationCache.get()!!
    }

    fun setIsolatedProjects(isolatedProjects: Option.Value<Boolean?>?) {
        this.isolatedProjects = isolatedProjects
    }

    fun getConfigurationCacheProblems(): StartParameterBuildOptions.ConfigurationCacheProblemsOption.Value? {
        return configurationCacheProblems
    }

    fun setConfigurationCacheProblems(configurationCacheProblems: StartParameterBuildOptions.ConfigurationCacheProblemsOption.Value?) {
        this.configurationCacheProblems = configurationCacheProblems
    }

    fun isConfigurationCacheDebug(): Boolean {
        return configurationCacheDebug
    }

    fun setConfigurationCacheDebug(configurationCacheDebug: Boolean) {
        this.configurationCacheDebug = configurationCacheDebug
    }

    fun setConfigurationCacheIgnoreUnsupportedBuildEventsListeners(configurationCacheIgnoreUnsupportedBuildEventsListeners: Boolean) {
        this.configurationCacheIgnoreUnsupportedBuildEventsListeners = configurationCacheIgnoreUnsupportedBuildEventsListeners
    }

    fun isConfigurationCacheIgnoreUnsupportedBuildEventsListeners(): Boolean {
        return configurationCacheIgnoreUnsupportedBuildEventsListeners
    }

    fun isConfigurationCacheParallel(): Boolean {
        return configurationCacheParallel
    }

    fun setConfigurationCacheParallel(parallel: Boolean) {
        this.configurationCacheParallel = parallel
    }

    fun isConfigurationCacheReadOnly(): Boolean {
        return configurationCacheReadOnly
    }

    fun setConfigurationCacheReadOnly(readOnly: Boolean) {
        this.configurationCacheReadOnly = readOnly
    }

    fun getConfigurationCacheEntriesPerKey(): Int {
        return configurationCacheEntriesPerKey
    }

    fun setConfigurationCacheEntriesPerKey(configurationCacheEntriesPerKey: Int) {
        this.configurationCacheEntriesPerKey = configurationCacheEntriesPerKey
    }

    fun getConfigurationCacheMaxProblems(): Int {
        return configurationCacheMaxProblems
    }

    fun setConfigurationCacheMaxProblems(configurationCacheMaxProblems: Int) {
        this.configurationCacheMaxProblems = configurationCacheMaxProblems
    }

    fun getConfigurationCacheIgnoredFileSystemCheckInputs(): String? {
        return configurationCacheIgnoredFileSystemCheckInputs
    }

    fun setConfigurationCacheIgnoredFileSystemCheckInputs(configurationCacheIgnoredFileSystemCheckInputs: String?) {
        this.configurationCacheIgnoredFileSystemCheckInputs = configurationCacheIgnoredFileSystemCheckInputs
    }

    fun isConfigurationCacheRecreateCache(): Boolean {
        return configurationCacheRecreateCache
    }

    fun setConfigurationCacheRecreateCache(configurationCacheRecreateCache: Boolean) {
        this.configurationCacheRecreateCache = configurationCacheRecreateCache
    }

    fun isConfigurationCacheQuiet(): Boolean {
        return configurationCacheQuiet
    }

    fun setConfigurationCacheQuiet(configurationCacheQuiet: Boolean) {
        this.configurationCacheQuiet = configurationCacheQuiet
    }

    fun setConfigurationCacheIntegrityCheckEnabled(configurationCacheIntegrityCheck: Boolean) {
        this.configurationCacheIntegrityCheckEnabled = configurationCacheIntegrityCheck
    }

    fun isConfigurationCacheIntegrityCheckEnabled(): Boolean {
        return configurationCacheIntegrityCheckEnabled
    }

    fun setConfigurationCacheHeapDumpDir(configurationCacheHeapDumpDir: String?) {
        this.configurationCacheHeapDumpDir = configurationCacheHeapDumpDir
    }

    fun getConfigurationCacheHeapDumpDir(): String? {
        return configurationCacheHeapDumpDir
    }

    fun setConfigurationCacheFineGrainedPropertyTracking(configurationCacheFineGrainedPropertyTracking: Boolean) {
        this.configurationCacheFineGrainedPropertyTracking = configurationCacheFineGrainedPropertyTracking
    }

    fun isConfigurationCacheFineGrainedPropertyTracking(): Boolean {
        return configurationCacheFineGrainedPropertyTracking
    }

    fun setIsolatedProjectsDiagnostics(isolatedProjectsDiagnostics: Boolean) {
        this.isIsolatedProjectsDiagnostics = isolatedProjectsDiagnostics
    }

    fun setIsolatedProjectsDangerouslyIgnoreProblems(isolatedProjectsDangerouslyIgnoreProblems: Boolean) {
        this.isIsolatedProjectsDangerouslyIgnoreProblems = isolatedProjectsDangerouslyIgnoreProblems
    }

    fun enableProblemReportGeneration(enableProblemReportGeneration: Boolean) {
        this.isProblemReportGenerationEnabled = enableProblemReportGeneration
    }

    fun isDaemonJvmCriteriaConfigured(): Boolean {
        return daemonJvmCriteriaConfigured
    }

    fun setDaemonJvmCriteriaConfigured(daemonJvmCriteriaConfigured: Boolean) {
        this.daemonJvmCriteriaConfigured = daemonJvmCriteriaConfigured
    }

    fun getParallelToolingModelBuilding(): Option.Value<Boolean?>? {
        return parallelToolingModelBuilding
    }

    fun setParallelToolingModelBuilding(parallelToolingModelBuilding: Option.Value<Boolean?>?) {
        this.parallelToolingModelBuilding = parallelToolingModelBuilding
    }

    fun toBuildLayoutConfiguration(): BuildLayoutConfiguration {
        return BuildLayoutConfiguration(getCurrentDir(), this.isSearchUpwards, this.isUseEmptySettings)
    }
}
