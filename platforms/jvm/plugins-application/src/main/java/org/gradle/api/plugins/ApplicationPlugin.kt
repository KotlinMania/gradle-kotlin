/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.plugins

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.distribution.Distribution
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.distribution.plugins.DistributionPlugin
import org.gradle.api.file.ConfigurableFilePermissions
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.api.plugins.internal.DefaultJavaApplication
import org.gradle.api.plugins.internal.JavaPluginHelper
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.internal.JavaExecExecutableUtils.getExecutableOverrideToolchainSpec
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.util.internal.DefaultGradleVersion
import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import java.util.function.BiFunction
import javax.inject.Inject

/**
 *
 * A [Plugin] which packages and runs a project as a Java Application.
 *
 *
 * The plugin can be configured via the [JavaApplication] extension.
 *
 * @see [Application plugin reference](https://docs.gradle.org/current/userguide/application_plugin.html)
 */
abstract class ApplicationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val tasks = project.getTasks()

        project.getPluginManager().apply(JavaPlugin::class.java)
        project.getPluginManager().apply(DistributionPlugin::class.java)

        val mainFeature: JvmFeatureInternal = JavaPluginHelper.getJavaComponent(project).mainFeature

        val extension = addExtension(project)
        addRunTask(project, mainFeature, extension)
        addCreateScriptsTask(project, mainFeature, extension)
        configureJavaCompileTask(mainFeature.compileJavaTask, extension)
        configureInstallTask(project.getProviders(), tasks.named<Sync>(DistributionPlugin.TASK_INSTALL_NAME, Sync::class.java), extension)

        val distributions = project.getExtensions().getByType<DistributionContainer>(DistributionContainer::class.java)
        val mainDistribution = distributions.getByName(DistributionPlugin.MAIN_DISTRIBUTION_NAME)
        configureDistribution(project, mainFeature, mainDistribution, extension)
    }

    private fun configureJavaCompileTask(javaCompile: TaskProvider<JavaCompile>, pluginExtension: JavaApplication) {
        javaCompile.configure(Action { j: JavaCompile? -> j!!.getOptions()!!.javaModuleMainClass.convention(pluginExtension.getMainClass()) })
    }

    private fun configureInstallTask(providers: ProviderFactory, installTask: TaskProvider<Sync>, pluginExtension: JavaApplication) {
        installTask.configure(Action { task: Sync? ->
            task!!.doFirst(
                "don't overwrite existing directories",
                PreventDestinationOverwrite(
                    providers.provider<String>(Callable { pluginExtension.getApplicationName() }),
                    providers.provider<String>(Callable { pluginExtension.getExecutableDir() })
                )
            )
        })
    }

    private class PreventDestinationOverwrite(private val applicationName: Provider<String>, private val executableDir: Provider<String>) : Action<Task> {
        override fun execute(task: Task) {
            val sync = task as Sync
            val destinationDir = sync.getDestinationDir()
            if (destinationDir.isDirectory()) {
                val children = destinationDir.list()
                if (children == null) {
                    throw throwAsUncheckedException(IOException("Could not list directory " + destinationDir), true)
                }
                if (children.size > 0) {
                    if (!File(destinationDir, "lib").isDirectory() || !File(destinationDir, executableDir.get()).isDirectory()) {
                        throw GradleException(
                            ("The specified installation directory \'"
                                    + destinationDir
                                    + "\' is neither empty nor does it contain an installation for \'"
                                    + applicationName.get()
                                    + "\'.\n"
                                    + "If you really want to install to this directory, delete it and run the install task again.\n"
                                    + "Alternatively, choose a different installation directory.")
                        )
                    }
                }
            }
        }
    }

    private fun addExtension(project: Project): JavaApplication {
        val javaApplication = project.getExtensions().create<JavaApplication>(JavaApplication::class.java, "application", DefaultJavaApplication::class.java)
        javaApplication.setApplicationName(project.getName())
        return javaApplication
    }

    private fun addRunTask(project: Project, mainFeature: JvmFeatureInternal, pluginExtension: JavaApplication) {
        project.getTasks().register<JavaExec>(TASK_RUN_NAME, JavaExec::class.java, Action { run: JavaExec ->
            run.setDescription("Runs this project as a JVM application")
            run.setGroup(APPLICATION_GROUP)

            val runtimeClasspath: FileCollection = project.files().from(Callable {
                if (run.getMainModule()!!.isPresent()) {
                    return@Callable jarsOnlyRuntimeClasspath(mainFeature)
                } else {
                    return@Callable runtimeClasspath(mainFeature)
                }
            })
            run.setClasspath(runtimeClasspath)
            run.getMainModule()!!.set(pluginExtension.getMainModule())
            run.getMainClass()!!.set(pluginExtension.getMainClass())
            run.getJvmArguments().convention(project.provider<Iterable<String>>(Callable { pluginExtension.getApplicationDefaultJvmArgs() }))

            val javaPluginExtension = project.getExtensions().getByType<JavaPluginExtension>(JavaPluginExtension::class.java)
            run.getModularity().getInferModulePath().convention(javaPluginExtension.modularity.getInferModulePath())
            val propertyFactory = this.propertyFactory

            val toolchainOverrideSpec = project.provider<JavaToolchainSpec>(Callable { getExecutableOverrideToolchainSpec(run, propertyFactory) })
            run.javaLauncher.convention(getToolchainTool<T>(project, BiFunction { obj: JavaToolchainService?, spec: JavaToolchainSpec? -> obj!!.launcherFor(spec!!) }, toolchainOverrideSpec))
        })
    }

    private fun <T> getToolchainTool(
        project: Project,
        toolMapper: BiFunction<JavaToolchainService, JavaToolchainSpec, Provider<T?>>,
        toolchainOverride: Provider<JavaToolchainSpec>
    ): Provider<T?> {
        val service = project.getExtensions().getByType<JavaToolchainService>(JavaToolchainService::class.java)
        val extension = project.getExtensions().getByType<JavaPluginExtension>(JavaPluginExtension::class.java)
        return toolchainOverride.orElse(extension.toolchain)
            .flatMap<T?>(Transformer { spec: JavaToolchainSpec? -> toolMapper.apply(service, spec!!) })
    }

    // @Todo: refactor this task configuration to extend a copy task and use replace tokens
    @Suppress("deprecation")
    private fun addCreateScriptsTask(project: Project, mainFeature: JvmFeatureInternal, pluginExtension: JavaApplication) {
        project.getTasks().register<CreateStartScripts>(TASK_START_SCRIPTS_NAME, CreateStartScripts::class.java, Action { startScripts: CreateStartScripts ->
            startScripts.setDescription("Creates OS specific scripts to run the project as a JVM application.")
            startScripts.setClasspath(jarsOnlyRuntimeClasspath(mainFeature))

            startScripts.getMainModule().set(pluginExtension.getMainModule())
            startScripts.getMainClass().set(pluginExtension.getMainClass())

            startScripts.getConventionMapping().map("applicationName", Callable { pluginExtension.getApplicationName() })

            startScripts.getGitRef().set(DefaultGradleVersion.current().getScriptTemplateGitRevision())

            startScripts.getConventionMapping().map("outputDir", Callable { File(project.getBuildDir(), "scripts") })

            startScripts.getConventionMapping().map("executableDir", Callable { pluginExtension.getExecutableDir() })

            startScripts.getConventionMapping().map("defaultJvmOpts", Callable { pluginExtension.getApplicationDefaultJvmArgs() })

            val javaPluginExtension = project.getExtensions().getByType<JavaPluginExtension>(JavaPluginExtension::class.java)
            startScripts.getModularity().getInferModulePath().convention(javaPluginExtension.modularity.getInferModulePath())
        })
    }

    private fun runtimeClasspath(mainFeature: JvmFeatureInternal): FileCollection {
        return mainFeature.sourceSet.getRuntimeClasspath()
    }

    private fun jarsOnlyRuntimeClasspath(mainFeature: JvmFeatureInternal): FileCollection {
        return mainFeature.jarTask.get().getOutputs().getFiles().plus(mainFeature.runtimeClasspathConfiguration)
    }

    private fun configureDistribution(project: Project, mainFeature: JvmFeatureInternal, mainDistribution: Distribution, pluginExtension: JavaApplication): CopySpec {
        mainDistribution.getDistributionBaseName().convention(project.provider<String>(Callable { pluginExtension.getApplicationName() }))
        val distSpec = mainDistribution.getContents()

        val jar: TaskProvider<Jar> = mainFeature.jarTask
        val startScripts: TaskProvider<Task> = project.getTasks().named(TASK_START_SCRIPTS_NAME)

        val libChildSpec = project.copySpec()
        libChildSpec.into("lib")
        libChildSpec.from(jar)
        libChildSpec.from(mainFeature.runtimeClasspathConfiguration)

        val binChildSpec = project.copySpec()

        binChildSpec.into(Callable { pluginExtension.getExecutableDir() } as Callable<Any?>)
        binChildSpec.from(startScripts)
        binChildSpec.filePermissions(Action { permissions: ConfigurableFilePermissions? -> permissions!!.unix("rwxr-xr-x") })

        val childSpec = project.copySpec()
        childSpec.from(project.file("src/dist"))
        childSpec.with(libChildSpec)
        childSpec.with(binChildSpec)

        distSpec.with(childSpec)

        distSpec.with(pluginExtension.getApplicationDistribution())
        return distSpec
    }

    @get:Inject
    protected abstract val propertyFactory: PropertyFactory

    companion object {
        const val APPLICATION_PLUGIN_NAME: String = "application"
        val APPLICATION_GROUP: String = APPLICATION_PLUGIN_NAME
        const val TASK_RUN_NAME: String = "run"
        const val TASK_START_SCRIPTS_NAME: String = "startScripts"
        const val TASK_DIST_ZIP_NAME: String = "distZip"
        const val TASK_DIST_TAR_NAME: String = "distTar"
    }
}
