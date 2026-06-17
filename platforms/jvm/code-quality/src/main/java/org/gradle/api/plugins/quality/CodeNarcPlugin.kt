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

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin
import org.gradle.api.reporting.SingleFileReport
import org.gradle.api.tasks.GroovySourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.jvm.toolchain.internal.CurrentJvmToolchainSpec
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * CodeNarc Plugin.
 *
 * @see [CodeNarc plugin reference](https://docs.gradle.org/current/userguide/codenarc_plugin.html)
 */
abstract class CodeNarcPlugin : AbstractCodeQualityPlugin<CodeNarc>() {
    private var extension: CodeNarcExtension? = null

    override fun getToolName(): String {
        return "CodeNarc"
    }

    override fun getTaskType(): Class<CodeNarc> {
        return CodeNarc::class.java
    }

    @get:Inject
    protected abstract val toolchainService: JavaToolchainService?

    override fun getBasePlugin(): Class<out Plugin<*>> {
        return GroovyBasePlugin::class.java
    }

    override fun createExtension(): CodeQualityExtension {
        extension = project.getExtensions().create<CodeNarcExtension>("codenarc", CodeNarcExtension::class.java, project)
        extension!!.setToolVersion(DEFAULT_CODENARC_VERSION)
        extension!!.setConfig(project.getResources().getText().fromFile(getRootProjectDirectory().file(DEFAULT_CONFIG_FILE_PATH)))
        extension!!.setMaxPriority1Violations(0)
        extension!!.setMaxPriority2Violations(0)
        extension!!.setMaxPriority3Violations(0)
        extension!!.setReportFormat("html")
        return extension!!
    }

    override fun configureConfiguration(configuration: Configuration) {
        configureDefaultDependencies(configuration)
    }

    override fun configureTaskDefaults(task: CodeNarc, baseName: String) {
        val configuration = project.getConfigurations().getAt(getConfigurationName())
        configureTaskConventionMapping(configuration, task)
        configureReportsConventionMapping(task, baseName)
        configureToolchains(task)
    }

    override fun beforeApply() {
        // Necessary to disambiguate the published variants of newer codenarc versions (including the default version)
        project.getPluginManager().apply(JvmEcosystemPlugin::class.java)
    }

    private fun configureDefaultDependencies(configuration: Configuration) {
        configuration.defaultDependencies(Action { dependencies: DependencySet? -> dependencies!!.add(project.getDependencies().create("org.codenarc:CodeNarc:" + extension!!.getToolVersion())) }
        )
    }

    private fun configureTaskConventionMapping(configuration: Configuration, task: CodeNarc) {
        val taskMapping = task.getConventionMapping()
        taskMapping.map("codenarcClasspath", Callable { configuration })
        taskMapping.map("config", Callable { extension!!.getConfig() })
        taskMapping.map("maxPriority1Violations", Callable { extension!!.getMaxPriority1Violations() })
        taskMapping.map("maxPriority2Violations", Callable { extension!!.getMaxPriority2Violations() })
        taskMapping.map("maxPriority3Violations", Callable { extension!!.getMaxPriority3Violations() })
        task.getIgnoreFailuresProperty().convention(project.provider<Boolean>(Callable { extension!!.isIgnoreFailures() }))
    }

    private fun configureReportsConventionMapping(task: CodeNarc, baseName: String) {
        val layout = project.getLayout()
        val providers = project.getProviders()
        val reportFormat = providers.provider<String>(Callable { extension!!.getReportFormat() })
        val reportsDir = layout.file(providers.provider<File>(Callable { extension!!.getReportsDir() }))
        task.getReports().all(SerializableLambdas.action<SingleFileReport>(SerializableLambdas.SerializableAction { report: SingleFileReport ->
            report.getRequired().convention(providers.provider<Boolean>(Callable { report.getName() == reportFormat.get() }))
            report.getOutputLocation().convention(layout.getProjectDirectory().file(providers.provider<String>(Callable {
                val fileSuffix = if (report.getName() == "text") "txt" else report.getName()
                File(reportsDir.get().getAsFile(), baseName + "." + fileSuffix).getAbsolutePath()
            })))
        }))
    }

    private fun configureToolchains(task: CodeNarc) {
        val javaLauncherProvider = this.toolchainService.launcherFor(project.getObjects().newInstance<CurrentJvmToolchainSpec>(CurrentJvmToolchainSpec::class.java))
        task.getJavaLauncher().convention(javaLauncherProvider)
        project.getPluginManager().withPlugin("java-base", Action { p: AppliedPlugin? ->
            val toolchain = getJavaPluginExtension().getToolchain()
            task.getJavaLauncher().convention(this.toolchainService.launcherFor(toolchain).orElse(javaLauncherProvider))
        })
    }

    override fun configureForSourceSet(sourceSet: SourceSet, task: CodeNarc) {
        task.setDescription("Run CodeNarc analysis for " + sourceSet.name + " classes")
        val groovySourceSet: SourceDirectorySet = sourceSet.getExtensions().getByType<GroovySourceDirectorySet>(GroovySourceDirectorySet::class.java)
        task.setSource(groovySourceSet.matching(Action { filter: PatternFilterable? -> filter!!.include("**/*.groovy") }))
        task.getConventionMapping().map("compilationClasspath", Callable { sourceSet.compileClasspath })
    }

    companion object {
        const val DEFAULT_CODENARC_VERSION: String = "3.7.0-groovy-4.0"
        private const val DEFAULT_CONFIG_FILE_PATH = "config/codenarc/codenarc.xml"
    }
}
