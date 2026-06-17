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

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableSet
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin
import org.gradle.api.reporting.SingleFileReport
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.jvm.toolchain.internal.CurrentJvmToolchainSpec
import org.gradle.util.internal.VersionNumber
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * A plugin for the [PMD](https://pmd.github.io/) source code analyzer.
 *
 *
 * Declares a `pmd` configuration which needs to be configured with the PMD library to be used.
 *
 *
 * Declares a `pmdAux` configuration to add transitive compileOnly dependencies to the PMD's auxclasspath. This is only needed if PMD complains about NoClassDefFoundError during type
 * resolution.
 *
 *
 * For each source set that is to be analyzed, a [Pmd] task is created and configured to analyze all Java code.
 *
 *
 * All PMD tasks (including user-defined ones) are added to the `check` lifecycle task.
 *
 * @see PmdExtension
 *
 * @see Pmd
 *
 * @see [PMD plugin reference](https://docs.gradle.org/current/userguide/pmd_plugin.html)
 */
@Suppress("deprecation") // The targetJdk property and TargetJdk type are themselves deprecated.
abstract class PmdPlugin : AbstractCodeQualityPlugin<Pmd>() {
    private var extension: PmdExtension? = null

    override fun getToolName(): String {
        return "PMD"
    }

    override fun getTaskType(): Class<Pmd> {
        return Pmd::class.java
    }

    @get:Inject
    protected abstract val toolchainService: JavaToolchainService?

    override fun createExtension(): CodeQualityExtension {
        extension = project.getExtensions().create<PmdExtension>("pmd", PmdExtension::class.java, project)
        extension!!.setToolVersion(DEFAULT_PMD_VERSION)
        extension!!.getRulesMinimumPriority().convention(5)
        extension!!.getIncrementalAnalysis().convention(true)
        extension!!.getMaxFailures().convention(0)
        extension!!.getThreads().convention(1)
        extension!!.setRuleSetFiles(project.getLayout().files())
        extension!!.getRuleSetsProperty().convention(project.getProviders().provider<MutableList<String>>(Callable { Companion.ruleSetsConvention(extension!!) }))
        AbstractCodeQualityPlugin.Companion.conventionMappingOf(extension)
            .map("targetJdk", Callable { whileDisabled<TargetJdk?>(org.gradle.internal.Factory { getDefaultTargetJdk(getJavaPluginExtension().getSourceCompatibility()) }) })
        return extension!!
    }

    /**
     * Returns the default [TargetJdk] for the given Java source compatibility version.
     *
     */
    @Deprecated("The {@code targetJdk} property has no effect for PMD 5.0 and later. Scheduled to be removed in Gradle 10.")
    fun getDefaultTargetJdk(javaVersion: JavaVersion): TargetJdk {
        deprecateMethod(PmdPlugin::class.java, "getDefaultTargetJdk(JavaVersion)")
            .withAdvice("This method is only used to compute the deprecated PMD targetJdk property, which has no effect for PMD 5.0 and later.")!!
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "deprecated_pmd_target_jdk")!!
            .nagUser()
        return org.gradle.internal.deprecation.DeprecationLogger.whileDisabled<TargetJdk?>(org.gradle.internal.Factory {
            try {
                return@whileDisabled org.gradle.api.plugins.quality.TargetJdk.toVersion(javaVersion.toString())
            } catch (ignored: java.lang.IllegalArgumentException) {
                // TargetJDK does not include 1.1, 1.2 and 1.8;
                // Use same fallback as PMD
                return@whileDisabled org.gradle.api.plugins.quality.TargetJdk.VERSION_1_4
            }
        })!!
    }

    override fun createConfigurations() {
        super.createConfigurations()

        project.getConfigurations().dependencyScopeLocked(PMD_ADDITIONAL_AUX_DEPS_CONFIGURATION, Action { additionalAuxDepsConfiguration: Configuration? ->
            additionalAuxDepsConfiguration!!.setDescription("The additional libraries that are available for type resolution during analysis")
            getJvmPluginServices().configureAsRuntimeClasspath(additionalAuxDepsConfiguration)
        })
    }

    override fun configureConfiguration(configuration: Configuration) {
        configureDefaultDependencies(configuration)
    }

    override fun configureTaskDefaults(task: Pmd, baseName: String) {
        val configuration = project.getConfigurations().getAt(getConfigurationName())
        configureTaskConventionMapping(configuration, task)
        configureReportsConventionMapping(task, baseName)
        configureToolchains(task)
    }

    private fun configureDefaultDependencies(configuration: Configuration) {
        configuration.defaultDependencies(Action { dependencies: DependencySet? ->
            calculateDefaultDependencyNotation(extension!!.getToolVersion())
                .stream()
                .map<Dependency> { dependencyNotation: String? -> project.getDependencies().create(dependencyNotation!!) }
                .forEach { e: Dependency? -> dependencies!!.add(e!!) }
        }
        )
    }

    private fun configureTaskConventionMapping(configuration: Configuration, task: Pmd) {
        val taskMapping = task.getConventionMapping()
        taskMapping.map("pmdClasspath", Callable { configuration })
        taskMapping.map("ruleSets", Callable { extension!!.getRuleSets() })
        taskMapping.map("ruleSetConfig", Callable { extension!!.getRuleSetConfig() })
        taskMapping.map("ruleSetFiles", Callable { extension!!.getRuleSetFiles() })
        taskMapping.map("consoleOutput", Callable { extension!!.isConsoleOutput() })
        taskMapping.map("targetJdk", Callable { whileDisabled<TargetJdk?>(org.gradle.internal.Factory { extension!!.getTargetJdk() }) })

        task.getRulesMinimumPriority().convention(extension!!.getRulesMinimumPriority())
        task.getMaxFailures().convention(extension!!.getMaxFailures())
        task.getIncrementalAnalysis().convention(extension!!.getIncrementalAnalysis())
        task.getThreads().convention(extension!!.getThreads())
        task.getIgnoreFailuresProperty().convention(project.provider<Boolean>(Callable { extension!!.isIgnoreFailures() }))
    }

    private fun configureReportsConventionMapping(task: Pmd, baseName: String) {
        val layout = project.getLayout()
        val providers = project.getProviders()
        val reportsDir = layout.file(providers.provider<File>(Callable { extension!!.getReportsDir() }))
        task.getReports().all(SerializableLambdas.action<SingleFileReport>(SerializableLambdas.SerializableAction { report: SingleFileReport ->
            val name = report.getName()
            val shouldRequireByDefault = name == "html" || name == "xml"
            report.getRequired().convention(shouldRequireByDefault)
            report.getOutputLocation().convention(
                layout.getProjectDirectory().file(providers.provider<String>(Callable {
                    val ext: String?
                    when (name) {
                        "codeClimate" -> ext = "codeclimate.json"
                        "sarif" -> ext = "sarif.json"
                        else -> ext = name
                    }
                    val reportFileName = baseName + "." + ext
                    File(reportsDir.get().getAsFile(), reportFileName).getAbsolutePath()
                }))
            )
        }))
    }

    private fun configureToolchains(task: Pmd) {
        val javaLauncherProvider = this.toolchainService.launcherFor(project.getObjects().newInstance<CurrentJvmToolchainSpec>(CurrentJvmToolchainSpec::class.java))
        task.getJavaLauncher().convention(javaLauncherProvider)
        project.getPluginManager().withPlugin("java-base", Action { p: AppliedPlugin? ->
            val toolchain = getJavaPluginExtension().getToolchain()
            task.getJavaLauncher().convention(this.toolchainService.launcherFor(toolchain).orElse(javaLauncherProvider))
        })
    }

    override fun configureForSourceSet(sourceSet: SourceSet, task: Pmd) {
        task.setDescription("Run PMD analysis for " + sourceSet.name + " classes")
        task.setSource(sourceSet.allJava)
        val taskMapping = task.getConventionMapping()
        val configurations = project.getConfigurations()

        val compileClasspath = configurations.getByName(sourceSet.compileClasspathConfigurationName)
        val pmdAdditionalAuxDepsConfiguration = configurations.getByName(PMD_ADDITIONAL_AUX_DEPS_CONFIGURATION)

        // TODO: Consider checking if the resolution consistency is enabled for compile/runtime.
        val pmdAuxClasspath: Configuration = configurations.resolvableLocked(sourceSet.name + "PmdAuxClasspath", Action { conf: Configuration? ->
            conf!!.extendsFrom(compileClasspath, pmdAdditionalAuxDepsConfiguration)
            // This is important to get transitive implementation dependencies. PMD may load referenced classes for analysis so it expects the classpath to be "closed" world.
            getJvmPluginServices().configureAsRuntimeClasspath(conf)
        })

        // We have to explicitly add compileClasspath here because it may contain classes that aren't part of the compileClasspathConfiguration. In particular, compile
        // classpath of the test sourceSet contains output of the main sourceSet.
        taskMapping.map("classpath", Callable {
            // It is important to subtract compileClasspath and not pmdAuxClasspath here because these configurations are resolved differently (as a compile and as a
            // runtime classpath). Compile and runtime entries for the same dependency may resolve to different files (e.g. compiled classes directory vs. jar).
            val nonConfigurationClasspathEntries = sourceSet.compileClasspath.minus(compileClasspath)
            sourceSet.output.plus(nonConfigurationClasspathEntries).plus(pmdAuxClasspath)
        })
    }

    companion object {
        // When updating DEFAULT_PMD_VERSION, also update links in Pmd and PmdExtension!
        const val DEFAULT_PMD_VERSION: String = "7.24.0"
        private const val PMD_ADDITIONAL_AUX_DEPS_CONFIGURATION = "pmdAux"

        private fun ruleSetsConvention(extension: PmdExtension): MutableList<String> {
            if (extension.getRuleSetConfig() == null && extension.getRuleSetFiles().isEmpty()) {
                return mutableListOf<String>("category/java/errorprone.xml")
            } else {
                return mutableListOf<String>()
            }
        }

        @VisibleForTesting
        fun calculateDefaultDependencyNotation(versionString: String): MutableSet<String> {
            val toolVersion = VersionNumber.parse(versionString)
            if (toolVersion.compareTo(VersionNumber.version(5)) < 0) {
                return mutableSetOf<String>("pmd:pmd:" + versionString)
            } else if (toolVersion.compareTo(VersionNumber.parse("5.2.0")) < 0) {
                return mutableSetOf<String>("net.sourceforge.pmd:pmd:" + versionString)
            } else if (toolVersion.getMajor() < 7) {
                return mutableSetOf<String>("net.sourceforge.pmd:pmd-java:" + versionString)
            }

            // starting from version 7, PMD is split into multiple modules
            return ImmutableSet.of<String>(
                "net.sourceforge.pmd:pmd-java:" + versionString,
                "net.sourceforge.pmd:pmd-ant:" + versionString
            )
        }
    }
}
