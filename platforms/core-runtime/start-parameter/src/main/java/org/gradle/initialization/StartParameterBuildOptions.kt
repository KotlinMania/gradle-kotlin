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
package org.gradle.initialization

import com.google.common.base.Splitter
import org.gradle.api.Transformer
import org.gradle.api.artifacts.verification.DependencyVerificationMode
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.file.BasicFileResolver
import org.gradle.cli.OptionCategory
import org.gradle.internal.buildoption.BooleanBuildOption
import org.gradle.internal.buildoption.BooleanCommandLineOptionConfiguration
import org.gradle.internal.buildoption.BuildOption
import org.gradle.internal.buildoption.BuildOptionSet
import org.gradle.internal.buildoption.CommandLineOptionConfiguration
import org.gradle.internal.buildoption.EnabledOnlyBooleanBuildOption
import org.gradle.internal.buildoption.EnumBuildOption
import org.gradle.internal.buildoption.IntegerBuildOption
import org.gradle.internal.buildoption.ListBuildOption
import org.gradle.internal.buildoption.Option
import org.gradle.internal.buildoption.Origin
import org.gradle.internal.buildoption.StringBuildOption
import org.gradle.internal.watch.registry.WatchMode
import java.io.File
import java.time.Duration
import java.util.Arrays
import java.util.Locale
import java.util.stream.Collectors

class StartParameterBuildOptions : BuildOptionSet<StartParameterInternal>() {
    override fun getAllOptions(): MutableList<out BuildOption<in StartParameterInternal>> {
        return OPTIONS
    }

    class ProjectCacheDirOption : StringBuildOption<StartParameterInternal>(
        PROPERTY_NAME,
        CommandLineOptionConfiguration.create("project-cache-dir", "Specifies the project-specific cache directory. Default is .gradle in the root project directory.")
    ) {
        override fun applyTo(value: String, settings: StartParameterInternal, origin: Origin) {
            val resolver: Transformer<File?, String?> = BasicFileResolver(settings.getCurrentDir())
            settings.setProjectCacheDir(resolver.transform(value))
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.CONFIGURATION
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.projectcachedir"
        }
    }

    class RerunTasksOption : EnabledOnlyBooleanBuildOption<StartParameterInternal>(null, CommandLineOptionConfiguration.create("rerun-tasks", "Ignores previously cached task results.")) {
        override fun applyTo(settings: StartParameterInternal, origin: Origin) {
            settings.setRerunTasks(true)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.EXECUTION
        }
    }

    class ProfileOption : EnabledOnlyBooleanBuildOption<StartParameterInternal>(
        null,
        CommandLineOptionConfiguration.create("profile", "Profiles build execution time. Generates a report in the <build_dir>/reports/profile directory.")
    ) {
        override fun applyTo(settings: StartParameterInternal, origin: Origin) {
            settings.setProfile(true)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DIAGNOSTICS
        }
    }

    class ContinueOption : BooleanBuildOption<StartParameterInternal>(
        PROPERTY_NAME,
        BooleanCommandLineOptionConfiguration.create(
            LONG_OPTION,
            "Continues task execution after a task failure.",
            "Stops task execution after a task failure."
        )
    ) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.setContinueOnFailure(value)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.EXECUTION
        }

        companion object {
            const val LONG_OPTION: String = "continue"

            const val PROPERTY_NAME: String = "org.gradle.continue"
        }
    }

    class OfflineOption : EnabledOnlyBooleanBuildOption<StartParameterInternal>(null, CommandLineOptionConfiguration.create("offline", "Runs the build without accessing network resources.")) {
        override fun applyTo(settings: StartParameterInternal, origin: Origin) {
            settings.setOffline(true)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.CONFIGURATION
        }
    }

    class RefreshDependenciesOption :
        EnabledOnlyBooleanBuildOption<StartParameterInternal>(null, CommandLineOptionConfiguration.create("refresh-dependencies", "U", "Refreshes the state of dependencies.")) {
        override fun applyTo(settings: StartParameterInternal, origin: Origin) {
            settings.setRefreshDependencies(true)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.CONFIGURATION
        }
    }

    class DryRunOption : EnabledOnlyBooleanBuildOption<StartParameterInternal>(null, CommandLineOptionConfiguration.create("dry-run", "m", "Runs the build with all task actions disabled.")) {
        override fun applyTo(settings: StartParameterInternal, origin: Origin) {
            settings.setDryRun(true)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.EXECUTION
        }
    }

    class ContinuousOption : EnabledOnlyBooleanBuildOption<StartParameterInternal>(
        null,
        CommandLineOptionConfiguration.create("continuous", "t", "Enables continuous build. Gradle does not exit and will re-execute tasks when task file inputs change.")
    ) {
        override fun applyTo(settings: StartParameterInternal, origin: Origin) {
            settings.isContinuous = true
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.EXECUTION
        }
    }

    class ContinuousBuildQuietPeriodOption : IntegerBuildOption<StartParameterInternal>(PROPERTY_NAME) {
        override fun applyTo(quietPeriodMillis: Int, startParameter: StartParameterInternal, origin: Origin?) {
            startParameter.continuousBuildQuietPeriod = Duration.ofMillis(quietPeriodMillis.toLong())
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.PERFORMANCE
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.continuous.quietperiod"
        }
    }

    class NoProjectDependenciesRebuildOption :
        EnabledOnlyBooleanBuildOption<StartParameterInternal>(null, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, "Disables rebuilding of project dependencies.")) {
        override fun applyTo(settings: StartParameterInternal, origin: Origin) {
            settings.setBuildProjectDependencies(false)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.EXECUTION
        }

        companion object {
            private const val LONG_OPTION = "no-rebuild"
            private const val SHORT_OPTION = "a"
        }
    }

    class InitScriptOption : ListBuildOption<StartParameterInternal>("", CommandLineOptionConfiguration.create("init-script", "I", "Specifies an initialization script.")) {
        override fun applyTo(values: MutableList<String>, settings: StartParameterInternal, origin: Origin) {
            val resolver: Transformer<File?, String?> = BasicFileResolver(settings.getCurrentDir())

            for (script in values) {
                settings.addInitScript(resolver.transform(script)!!)
            }
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.CONFIGURATION
        }
    }

    class ExcludeTaskOption : ListBuildOption<StartParameterInternal>("", CommandLineOptionConfiguration.create("exclude-task", "x", "Specifies a task to exclude from execution.")) {
        override fun applyTo(values: MutableList<String>, settings: StartParameterInternal, origin: Origin) {
            settings.setExcludedTaskNames(values)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.EXECUTION
        }
    }

    class IncludeBuildOption : ListBuildOption<StartParameterInternal>("", CommandLineOptionConfiguration.create("include-build", "Includes the specified build in the composite.")) {
        override fun applyTo(values: MutableList<String>, settings: StartParameterInternal, origin: Origin) {
            val resolver: Transformer<File?, String?> = BasicFileResolver(settings.getCurrentDir())

            for (includedBuild in values) {
                settings.includeBuild(resolver.transform(includedBuild)!!)
            }
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.CONFIGURATION
        }
    }

    class ConfigureOnDemandOption : BooleanBuildOption<StartParameterInternal>(
        GRADLE_PROPERTY,
        BooleanCommandLineOptionConfiguration.create(
            "configure-on-demand",
            "Configures necessary projects only. Gradle will attempt to reduce configuration time for large multi-project builds.",
            "Disables the use of configuration on demand."
        ).incubating()
    ) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.setConfigureOnDemand(value)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.PERFORMANCE
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.configureondemand"
        }
    }

    class BuildCacheOption : BooleanBuildOption<StartParameterInternal>(
        GRADLE_PROPERTY,
        BooleanCommandLineOptionConfiguration.create("build-cache", "Enables the Gradle build cache. Gradle will try to reuse outputs from previous builds.", "Disables the Gradle build cache.")
    ) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.setBuildCacheEnabled(value)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.PERFORMANCE
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.caching"
        }
    }

    class BuildCacheDebugLoggingOption : BooleanBuildOption<StartParameterInternal>(GRADLE_PROPERTY) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.isBuildCacheDebugLogging = value
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.LOGGING
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.caching.debug"
        }
    }

    class WatchFileSystemOption : BooleanBuildOption<StartParameterInternal>(
        GRADLE_PROPERTY, BooleanCommandLineOptionConfiguration.create(
            LONG_OPTION,
            "Enables file system watching. Reuses file system data for subsequent builds.",
            "Disables file system watching."
        )
    ) {
        override fun applyTo(value: Boolean, startParameter: StartParameterInternal, origin: Origin?) {
            startParameter.setWatchFileSystemMode(
                if (value)
                    WatchMode.ENABLED
                else
                    WatchMode.DISABLED
            )
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.PERFORMANCE
        }

        companion object {
            const val LONG_OPTION: String = "watch-fs"
            const val GRADLE_PROPERTY: String = "org.gradle.vfs.watch"
        }
    }

    class VfsVerboseLoggingOption : BooleanBuildOption<StartParameterInternal>(GRADLE_PROPERTY) {
        override fun applyTo(value: Boolean, startParameter: StartParameterInternal, origin: Origin?) {
            startParameter.setVfsVerboseLogging(value)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.LOGGING
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.vfs.verbose"
        }
    }

    class BuildScanOption : BooleanBuildOption<StartParameterInternal>(
        null, null, BooleanCommandLineOptionConfiguration.create(
            LONG_OPTION,
            "Generates a Build Scan (powered by Develocity).",
            "Disables the creation of a Build Scan."
        )
    ) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            if (value) {
                settings.isBuildScan = true
            } else {
                settings.isNoBuildScan = true
            }
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DEVELOCITY
        }

        companion object {
            const val LONG_OPTION: String = "scan"
        }
    }

    class DevelocityUrlOption : StringBuildOption<StartParameterInternal>(
        GRADLE_PROPERTY, CommandLineOptionConfiguration.create(
            LONG_OPTION,
            "Default URL of the Develocity server to publish Build Scan to. Triggers auto-application of the Develocity plugin if not already applied.\n" +
                    "Has no effect if the Develocity plugin is already applied and a server URL is configured."
        )
    ) {
        override fun applyTo(value: String, settings: StartParameterInternal, origin: Origin) {
            settings.develocityUrl = value
        }

        override fun applyFromEnvVar(envVars: MutableMap<String, String>, settings: StartParameterInternal) {
            val develocityUrlEnvVar = envVars.get(ENVIRONMENT_VARIABLE)
            if (develocityUrlEnvVar != null) {
                settings.develocityUrl = develocityUrlEnvVar
            }
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DEVELOCITY
        }

        companion object {
            const val LONG_OPTION: String = "develocity-url"
            const val GRADLE_PROPERTY: String = "com.gradle.develocity.url"
            const val ENVIRONMENT_VARIABLE: String = "COM_GRADLE_DEVELOCITY_URL"
        }
    }

    class DevelocityPluginVersionOption : StringBuildOption<StartParameterInternal>(
        GRADLE_PROPERTY, CommandLineOptionConfiguration.create(
            LONG_OPTION,
            "Version of the Develocity plugin to auto-apply, must be 4.4.0 or higher if Develocity URL is specified as well.\n" +
                    "Used only if --develocity-url or --scan triggers auto-application of the Develocity plugin."
        )
    ) {
        override fun applyTo(value: String, settings: StartParameterInternal, origin: Origin) {
            settings.develocityPluginVersion = value
        }

        override fun applyFromEnvVar(envVars: MutableMap<String, String>, settings: StartParameterInternal) {
            val develocityPluginVersionEnvVar = envVars.get(ENVIRONMENT_VARIABLE)
            if (develocityPluginVersionEnvVar != null) {
                settings.develocityPluginVersion = develocityPluginVersionEnvVar
            }
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DEVELOCITY
        }

        companion object {
            const val LONG_OPTION: String = "develocity-plugin-version"
            const val GRADLE_PROPERTY: String = "com.gradle.develocity.plugin.version"
            const val ENVIRONMENT_VARIABLE: String = "COM_GRADLE_DEVELOCITY_PLUGIN_VERSION"
        }
    }

    class DependencyLockingWriteOption : EnabledOnlyBooleanBuildOption<StartParameterInternal>(
        null,
        CommandLineOptionConfiguration.create(LONG_OPTION, "Persists dependency resolution for locked configurations. Ignores existing locking information if it exists.")
    ) {
        override fun applyTo(settings: StartParameterInternal, origin: Origin) {
            settings.setWriteDependencyLocks(true)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.SECURITY
        }

        companion object {
            const val LONG_OPTION: String = "write-locks"
        }
    }

    class DependencyVerificationWriteOption internal constructor() : StringBuildOption<StartParameterInternal>(
        "", CommandLineOptionConfiguration.create(
            LONG_OPTION, SHORT_OPTION,
            "Generates checksums for dependencies used in the project. Accepts a comma-separated list."
        )
    ) {
        override fun applyTo(value: String, settings: StartParameterInternal, origin: Origin) {
            val checksums = Splitter.on(",")
                .omitEmptyStrings()
                .trimResults()
                .splitToList(value)
                .stream()
                .map<String?> { obj: String? -> obj!!.lowercase(Locale.getDefault()) }
                .collect(Collectors.toList())
            settings.setWriteDependencyVerifications(checksums)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.SECURITY
        }

        companion object {
            const val SHORT_OPTION: String = "M"
            const val LONG_OPTION: String = "write-verification-metadata"
        }
    }

    class DependencyVerificationModeOption : EnumBuildOption<DependencyVerificationMode, StartParameterInternal>(
        LONG_OPTION,
        DependencyVerificationMode::class.java,
        DependencyVerificationMode.entries.toTypedArray(),
        GRADLE_PROPERTY,
        CommandLineOptionConfiguration.create(
            LONG_OPTION, SHORT_OPTION, "Configures the dependency verification mode. Supported values are 'strict', 'lenient', or 'off'."
        )
    ) {
        override fun applyTo(value: DependencyVerificationMode, settings: StartParameterInternal, origin: Origin?) {
            settings.dependencyVerificationMode = value
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.SECURITY
        }

        companion object {
            private const val GRADLE_PROPERTY = "org.gradle.dependency.verification"
            private const val LONG_OPTION = "dependency-verification"
            private const val SHORT_OPTION = "F"
        }
    }

    class DependencyLockingUpdateOption : ListBuildOption<StartParameterInternal>("",
        CommandLineOptionConfiguration.create("update-locks", "Performs a partial update of the dependency lock. Allows passed-in module notations to change version.").incubating()
    ) {
        override fun applyTo(modulesToUpdate: MutableList<String>, settings: StartParameterInternal, origin: Origin) {
            settings.setLockedDependenciesToUpdate(modulesToUpdate)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.SECURITY
        }
    }

    class RefreshKeysOption : EnabledOnlyBooleanBuildOption<StartParameterInternal>(
        null,
        CommandLineOptionConfiguration.create(LONG_OPTION, "Refreshes the public keys used for dependency verification.")
    ) {
        override fun applyTo(settings: StartParameterInternal, origin: Origin) {
            settings.setRefreshKeys(true)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.SECURITY
        }

        companion object {
            private const val LONG_OPTION = "refresh-keys"
        }
    }

    class ExportKeysOption : EnabledOnlyBooleanBuildOption<StartParameterInternal>(
        null,
        CommandLineOptionConfiguration.create(LONG_OPTION, "Exports the public keys used for dependency verification.")
    ) {
        override fun applyTo(settings: StartParameterInternal, origin: Origin) {
            settings.setExportKeys(true)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.SECURITY
        }

        companion object {
            const val LONG_OPTION: String = "export-keys"
        }
    }

    class ConfigurationCacheOption : BooleanBuildOption<StartParameterInternal>(
        PROPERTY_NAME,
        DEPRECATED_PROPERTY_NAME,
        BooleanCommandLineOptionConfiguration.create(
            LONG_OPTION,
            "Enables the configuration cache. Gradle will try to reuse the build configuration from previous builds.",
            "Disables the configuration cache."
        )
    ) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.setConfigurationCache(Option.Value.value<Boolean?>(value))
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.PERFORMANCE
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.configuration-cache"
            const val DEPRECATED_PROPERTY_NAME: String = "org.gradle.unsafe.configuration-cache"
            const val LONG_OPTION: String = "configuration-cache"
        }
    }

    class IsolatedProjectsOption : BooleanBuildOption<StartParameterInternal>(
        PROPERTY_NAME,
        DEPRECATED_PROPERTY_NAME,
        BooleanCommandLineOptionConfiguration.create(
            LONG_OPTION,
            "Enables Isolated Projects. Projects are configured in parallel. Implies `--configuration-cache`.",
            "Disables Isolated Projects."
        ).incubating()
    ) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.setIsolatedProjects(Option.Value.value<Boolean?>(value))
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.PERFORMANCE
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.isolated-projects"
            const val DEPRECATED_PROPERTY_NAME: String = "org.gradle.unsafe.isolated-projects"
            const val LONG_OPTION: String = "isolated-projects"
        }
    }

    class IsolatedProjectsDiagnosticsOption : BooleanBuildOption<StartParameterInternal>(PROPERTY_NAME, DEPRECATED_PROPERTY_NAME) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.setIsolatedProjectsDiagnostics(value)
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.isolated-projects.diagnostics"
            const val DEPRECATED_PROPERTY_NAME: String = "org.gradle.unsafe.isolated-projects.diagnostics"
        }
    }

    class IsolatedProjectsDangerouslyIgnoreProblemsOption : BooleanBuildOption<StartParameterInternal>(PROPERTY_NAME, DEPRECATED_PROPERTY_NAME) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.setIsolatedProjectsDangerouslyIgnoreProblems(value)
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.isolated-projects.dangerously-ignore-problems"
            const val DEPRECATED_PROPERTY_NAME: String = "org.gradle.unsafe.isolated-projects.dangerously-ignore-problems"
        }
    }

    class ConfigurationCacheProblemsOption : EnumBuildOption<ConfigurationCacheProblemsOption.Value, StartParameterInternal>(
        LONG_OPTION,
        Value::class.java,
        Value.entries.toTypedArray(),
        PROPERTY_NAME,
        DEPRECATED_PROPERTY_NAME,
        CommandLineOptionConfiguration.create(
            LONG_OPTION,
            "Configures how the configuration cache handles problems (fail or warn). Supported values are 'warn', or 'fail' (default)."
        )
    ) {
        enum class Value {
            FAIL, WARN
        }

        override fun applyTo(value: Value, settings: StartParameterInternal, origin: Origin?) {
            settings.setConfigurationCacheProblems(value)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DIAGNOSTICS
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.configuration-cache.problems"
            const val DEPRECATED_PROPERTY_NAME: String = "org.gradle.unsafe.configuration-cache-problems"
            const val LONG_OPTION: String = "configuration-cache-problems"
        }
    }

    class ConfigurationCacheIgnoreInputsDuringStore : BooleanBuildOption<StartParameterInternal>(PROPERTY_NAME) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.isConfigurationCacheIgnoreInputsDuringStore = value
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.configuration-cache.inputs.unsafe.ignore.in-serialization"
        }
    }

    /**
     * Suppresses Configuration Cache problems for unsupported listeners registered in `BuildEventsListenersRegistry`.
     *
     * @since 9.0.0
     */
    class ConfigurationCacheIgnoreUnsupportedBuildEventsListeners : BooleanBuildOption<StartParameterInternal>(PROPERTY_NAME) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.setConfigurationCacheIgnoreUnsupportedBuildEventsListeners(value)
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.configuration-cache.unsafe.ignore.unsupported-build-events-listeners"
        }
    }

    class ConfigurationCacheMaxProblemsOption : IntegerBuildOption<StartParameterInternal>(PROPERTY_NAME, DEPRECATED_PROPERTY_NAME) {
        override fun applyTo(value: Int, settings: StartParameterInternal, origin: Origin?) {
            settings.setConfigurationCacheMaxProblems(value)
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.configuration-cache.max-problems"

            const val DEPRECATED_PROPERTY_NAME: String = "org.gradle.unsafe.configuration-cache.max-problems"
        }
    }

    class ConfigurationCacheIgnoredFileSystemCheckInputs : StringBuildOption<StartParameterInternal>(PROPERTY_NAME) {
        override fun applyTo(value: String, settings: StartParameterInternal, origin: Origin) {
            settings.setConfigurationCacheIgnoredFileSystemCheckInputs(value)
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.configuration-cache.inputs.unsafe.ignore.file-system-checks"
        }
    }

    class ConfigurationCacheDebugOption : BooleanBuildOption<StartParameterInternal>(PROPERTY_NAME, DEPRECATED_PROPERTY_NAME) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.setConfigurationCacheDebug(value)
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.internal.configuration-cache.debug"
            const val DEPRECATED_PROPERTY_NAME: String = "org.gradle.unsafe.configuration-cache.debug"
        }
    }

    class ConfigurationCacheParallelOption : BooleanBuildOption<StartParameterInternal>(PROPERTY_NAME) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.setConfigurationCacheParallel(value)
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.configuration-cache.parallel"
        }
    }

    class ConfigurationCacheReadOnlyOption : BooleanBuildOption<StartParameterInternal>(PROPERTY_NAME) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.setConfigurationCacheReadOnly(value)
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.configuration-cache.read-only"
        }
    }

    class ConfigurationCacheEntriesPerKeyOption : IntegerBuildOption<StartParameterInternal>(PROPERTY_NAME) {
        override fun applyTo(value: Int, settings: StartParameterInternal, origin: Origin?) {
            settings.setConfigurationCacheEntriesPerKey(value)
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.configuration-cache.entries-per-key"
        }
    }

    class ConfigurationCacheRecreateOption : BooleanBuildOption<StartParameterInternal>(PROPERTY_NAME, DEPRECATED_PROPERTY_NAME) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.setConfigurationCacheRecreateCache(value)
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.internal.configuration-cache.recreate-cache"
            const val DEPRECATED_PROPERTY_NAME: String = "org.gradle.unsafe.configuration-cache.recreate-cache"
        }
    }

    class ConfigurationCacheQuietOption : BooleanBuildOption<StartParameterInternal>(PROPERTY_NAME, DEPRECATED_PROPERTY_NAME) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.setConfigurationCacheQuiet(value)
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.internal.configuration-cache.quiet"
            const val DEPRECATED_PROPERTY_NAME: String = "org.gradle.unsafe.configuration-cache.quiet"
        }
    }

    /**
     * Enables stricter integrity checks of the stored configuration cache entries, at the cost of potential performance penalty and significantly inflated entry size.
     * Can be useful when debugging store failures.
     */
    class ConfigurationCacheIntegrityCheckOption : BooleanBuildOption<StartParameterInternal>(PROPERTY_NAME) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.setConfigurationCacheIntegrityCheckEnabled(value)
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.configuration-cache.integrity-check"
        }
    }

    /**
     * When set, tells Gradle to emit heap dumps in the given directory after loading the work graph on a Configuration Cache hit,
     * after storing and loading the work graph on a Configuration Cache miss.
     */
    class ConfigurationCacheHeapDumpDir : StringBuildOption<StartParameterInternal>(PROPERTY_NAME) {
        override fun applyTo(value: String, settings: StartParameterInternal, origin: Origin) {
            settings.setConfigurationCacheHeapDumpDir(value)
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.configuration-cache.heap-dump-dir"
        }
    }

    /**
     * Whether [project property accesses][org.gradle.api.internal.properties.GradleProperties] are tracked individually
     * to increase cache hit rates.
     *
     * Increases memory usage proportionally to the number of projects and property accesses.
     *
     * It can be disabled to save on memory.
     *
     * The default is `true`.
     */
    class ConfigurationCacheFineGrainedPropertyTracking : BooleanBuildOption<StartParameterInternal>(PROPERTY_NAME) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.setConfigurationCacheFineGrainedPropertyTracking(value)
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.configuration-cache.fine-grained-property-tracking"
        }
    }

    class PropertyUpgradeReportOption :
        EnabledOnlyBooleanBuildOption<StartParameterInternal>(null, CommandLineOptionConfiguration.create(LONG_OPTION, "Runs the build with the experimental property upgrade report.").incubating()) {
        override fun applyTo(settings: StartParameterInternal, origin: Origin) {
            settings.isPropertyUpgradeReportEnabled = true
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DIAGNOSTICS
        }

        companion object {
            const val LONG_OPTION: String = "property-upgrade-report"
        }
    }

    class ProblemReportGenerationOption : BooleanBuildOption<StartParameterInternal>(
        GRADLE_PROPERTY,
        BooleanCommandLineOptionConfiguration.create(LONG_OPTION, "Enables the HTML problems report.", "Disables the HTML problems report.").incubating()
    ) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.enableProblemReportGeneration(value)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DIAGNOSTICS
        }

        companion object {
            const val LONG_OPTION: String = "problems-report"
            const val GRADLE_PROPERTY: String = "org.gradle.problems.report"
        }
    }

    class TaskGraphOption : EnabledOnlyBooleanBuildOption<StartParameterInternal>(null, CommandLineOptionConfiguration.create(LONG_OPTION, "Prints the task graph instead of executing tasks.")) {
        override fun applyTo(settings: StartParameterInternal, origin: Origin) {
            settings.setTaskGraph(true)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DIAGNOSTICS
        }

        companion object {
            const val LONG_OPTION: String = "task-graph"
        }
    }

    class ParallelToolingModelBuildingOption : BooleanBuildOption<StartParameterInternal>(PROPERTY_NAME) {
        override fun applyTo(value: Boolean, settings: StartParameterInternal, origin: Origin?) {
            settings.setParallelToolingModelBuilding(Option.Value.value<Boolean?>(value))
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.tooling.parallel"
        }
    }

    companion object {
        private val OPTIONS: MutableList<BuildOption<StartParameterInternal>> = Arrays.asList<BuildOption<StartParameterInternal>>(
            ProjectCacheDirOption(),
            RerunTasksOption(),
            ProfileOption(),
            ContinueOption(),
            OfflineOption(),
            RefreshDependenciesOption(),
            DryRunOption(),
            ContinuousOption(),
            ContinuousBuildQuietPeriodOption(),
            NoProjectDependenciesRebuildOption(),
            InitScriptOption(),
            ExcludeTaskOption(),
            IncludeBuildOption(),
            ConfigureOnDemandOption(),
            BuildCacheOption(),
            BuildCacheDebugLoggingOption(),
            WatchFileSystemOption(),
            VfsVerboseLoggingOption(),
            BuildScanOption(),
            DevelocityUrlOption(),
            DevelocityPluginVersionOption(),
            DependencyLockingWriteOption(),
            DependencyVerificationWriteOption(),
            DependencyVerificationModeOption(),
            DependencyLockingUpdateOption(),
            RefreshKeysOption(),
            ExportKeysOption(),
            ConfigurationCacheProblemsOption(),
            ConfigurationCacheOption(),
            ConfigurationCacheIgnoreInputsDuringStore(),
            ConfigurationCacheIgnoreUnsupportedBuildEventsListeners(),
            ConfigurationCacheMaxProblemsOption(),
            ConfigurationCacheIgnoredFileSystemCheckInputs(),
            ConfigurationCacheDebugOption(),
            ConfigurationCacheParallelOption(),
            ConfigurationCacheReadOnlyOption(),
            ConfigurationCacheRecreateOption(),
            ConfigurationCacheQuietOption(),
            ConfigurationCacheIntegrityCheckOption(),
            ConfigurationCacheEntriesPerKeyOption(),
            ConfigurationCacheHeapDumpDir(),
            ConfigurationCacheFineGrainedPropertyTracking(),
            IsolatedProjectsOption(),
            IsolatedProjectsDiagnosticsOption(),
            IsolatedProjectsDangerouslyIgnoreProblemsOption(),
            ProblemReportGenerationOption(),
            PropertyUpgradeReportOption(),
            TaskGraphOption(),
            ParallelToolingModelBuildingOption()
        )
    }
}
