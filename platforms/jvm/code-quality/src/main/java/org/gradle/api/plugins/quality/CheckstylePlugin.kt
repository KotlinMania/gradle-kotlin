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

import com.google.common.util.concurrent.Callables
import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.file.Directory
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin
import org.gradle.api.reporting.SingleFileReport
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.toolchain.internal.CurrentJvmToolchainSpec
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * Checkstyle Plugin.
 *
 * @see [Checkstyle plugin reference](https://docs.gradle.org/current/userguide/checkstyle_plugin.html)
 */
abstract class CheckstylePlugin : AbstractCodeQualityPlugin<Checkstyle>() {
    private var extension: CheckstyleExtension? = null

    override fun getToolName(): String {
        return "Checkstyle"
    }

    override fun getTaskType(): Class<Checkstyle> {
        return Checkstyle::class.java
    }

    @get:Inject
    protected abstract val toolchainService: JavaToolchainService?

    override fun createExtension(): CodeQualityExtension {
        extension = project.getExtensions().create<CheckstyleExtension>("checkstyle", CheckstyleExtension::class.java, project)
        extension!!.setToolVersion(DEFAULT_CHECKSTYLE_VERSION)
        val directory: Directory = getRootProjectDirectory().dir(CONFIG_DIR_NAME)
        extension!!.getConfigDirectory().convention(directory)
        extension!!.setConfig(
            project.getResources().getText().fromFile(
                extension!!.getConfigDirectory()
                    .file("checkstyle.xml") // If for whatever reason the provider above cannot be resolved, go back to default location, which we know how to ignore if missing
                    .orElse(directory.file("checkstyle.xml"))
            )
        )
        return extension!!
    }

    override fun configureConfiguration(configuration: Configuration) {
        configureDefaultDependencies(configuration)
    }

    override fun configureTaskDefaults(task: Checkstyle, baseName: String) {
        val configuration = project.getConfigurations().getAt(getConfigurationName())
        configureTaskConventionMapping(configuration, task)
        configureReportsConventionMapping(task, baseName)
        configureToolchains(task)
    }

    private fun configureDefaultDependencies(configuration: Configuration) {
        configuration.defaultDependencies(Action { dependencies: DependencySet? ->
            dependencies!!.add(
                project.getDependencies().create("com.puppycrawl.tools:checkstyle:" + extension!!.getToolVersion())
            )
        }
        )
    }

    private fun configureTaskConventionMapping(configuration: Configuration, task: Checkstyle) {
        val taskMapping = task.getConventionMapping()
        taskMapping.map("checkstyleClasspath", Callables.returning<Configuration>(configuration))
        taskMapping.map("config", Callable { extension!!.getConfig() })
        taskMapping.map("configProperties", Callable { extension!!.getConfigProperties() } as Callable<MutableMap<String?, Any?>?>)
        taskMapping.map("showViolations", Callable { extension!!.isShowViolations() })
        taskMapping.map("maxErrors", Callable { extension!!.getMaxErrors() })
        taskMapping.map("maxWarnings", Callable { extension!!.getMaxWarnings() })
        task.getConfigDirectory().convention(extension!!.getConfigDirectory())
        task.getEnableExternalDtdLoad().convention(extension!!.getEnableExternalDtdLoad())
        task.getIgnoreFailuresProperty().convention(project.provider<Boolean>(Callable { extension!!.isIgnoreFailures() }))
    }

    private fun configureReportsConventionMapping(task: Checkstyle, baseName: String) {
        val layout = project.getLayout()
        val providers = project.getProviders()
        val reportsDir = layout.file(providers.provider<File>(Callable { extension!!.getReportsDir() }))
        task.getReports().all(SerializableLambdas.action<SingleFileReport>(SerializableLambdas.SerializableAction { report: SingleFileReport ->
            report.getRequired().convention(report.getName() != "sarif")
            report.getOutputLocation().convention(
                layout.getProjectDirectory().file(providers.provider<String>(Callable {
                    val reportFileName = baseName + "." + report.getName()
                    File(reportsDir.get().getAsFile(), reportFileName).getAbsolutePath()
                }))
            )
        }))
    }

    private fun configureToolchains(task: Checkstyle) {
        val javaLauncherProvider = this.toolchainService.launcherFor(project.getObjects().newInstance<CurrentJvmToolchainSpec>(CurrentJvmToolchainSpec::class.java))
        task.getJavaLauncher().convention(javaLauncherProvider)
        project.getPluginManager().withPlugin("java-base", Action { p: AppliedPlugin? ->
            val toolchain = getJavaPluginExtension().getToolchain()
            task.getJavaLauncher().convention(this.toolchainService.launcherFor(toolchain).orElse(javaLauncherProvider))
        })
    }

    override fun configureForSourceSet(sourceSet: SourceSet, task: Checkstyle) {
        task.setDescription("Run Checkstyle analysis for " + sourceSet.name + " classes")
        task.setClasspath(sourceSet.output.plus(sourceSet.compileClasspath))
        task.setSource(sourceSet.allJava)
    }

    companion object {
        const val DEFAULT_CHECKSTYLE_VERSION: String = "10.24.0"
        private const val CONFIG_DIR_NAME = "config/checkstyle"
    }
}
