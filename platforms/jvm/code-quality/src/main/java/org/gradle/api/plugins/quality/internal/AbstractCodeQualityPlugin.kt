/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.plugins.quality.internal

import com.google.common.base.Function
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Iterables
import com.google.common.util.concurrent.Callables
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.ConventionMapping
import org.gradle.api.internal.IConventionAware
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.plugins.quality.CodeQualityExtension
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.Cast.uncheckedCast
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

abstract class AbstractCodeQualityPlugin<T> : Plugin<ProjectInternal> {
    protected var project: ProjectInternal? = null
    private var extension: CodeQualityExtension? = null

    override fun apply(project: ProjectInternal) {
        this.project = project

        beforeApply()
        project.getPluginManager().apply(ReportingBasePlugin::class.java)
        createConfigurations()
        extension = createExtension()
        configureExtensionRule()
        configureTaskRule()
        configureSourceSetRule()
        configureCheckTask()
    }

    protected abstract val toolName: String?

    protected abstract val taskType: Class<T?>?

    private val castedTaskType: Class<out Task>
        get() = uncheckedCast<Class<out Task>?>(this.taskType)

    protected val taskBaseName: String
        get() = this.toolName!!.lowercase()

    protected val configurationName: String
        get() = this.toolName!!.lowercase()

    protected val reportName: String
        get() = this.toolName!!.lowercase()

    protected val rootProjectDirectory: Directory
        get() = project!!.getIsolated().getRootProject().getProjectDirectory()

    protected open val basePlugin: Class<out Plugin<*>>
        get() = JavaBasePlugin::class.java

    protected open fun beforeApply() {
    }

    @Suppress("deprecation")
    protected open fun createConfigurations() {
        project!!.getConfigurations().resolvableDependencyScopeLocked(this.configurationName, Action { configuration: Configuration? ->
            configuration!!.setTransitive(true)
            configuration.setDescription("The " + this.toolName + " libraries to be used for this project.")
            this.jvmPluginServices.configureAsRuntimeClasspath(configuration)

            // Don't need these things, they're provided by the runtime
            configuration.exclude(excludeProperties("ant", "ant"))
            configuration.exclude(excludeProperties("org.apache.ant", "ant"))
            configuration.exclude(excludeProperties("org.apache.ant", "ant-launcher"))
            configuration.exclude(excludeProperties("org.slf4j", "slf4j-api"))
            configuration.exclude(excludeProperties("org.slf4j", "jcl-over-slf4j"))
            configuration.exclude(excludeProperties("org.slf4j", "log4j-over-slf4j"))
            configuration.exclude(excludeProperties("commons-logging", "commons-logging"))
            configuration.exclude(excludeProperties("log4j", "log4j"))
            configureConfiguration(configuration)
        })
    }

    protected abstract fun configureConfiguration(configuration: Configuration)

    private fun excludeProperties(group: String, module: String): MutableMap<String, String> {
        return ImmutableMap.builder<String, String>()
            .put("group", group)
            .put("module", module)
            .build()
    }

    protected abstract fun createExtension(): CodeQualityExtension

    private fun configureExtensionRule() {
        val extensionMapping: ConventionMapping = Companion.conventionMappingOf(extension!!)
        extensionMapping.map("sourceSets", Callables.returning<ArrayList<Any>>(ArrayList<Any?>()))
        extensionMapping.map("reportsDir", object : Callable<File> {
            override fun call(): File {
                return project!!.getExtensions().getByType<ReportingExtension>(ReportingExtension::class.java).getBaseDirectory().dir(this.reportName).get().getAsFile()
            }
        })
        withBasePlugin(object : Action<Plugin<*>> {
            override fun execute(plugin: Plugin<*>) {
                extensionMapping.map("sourceSets", object : Callable<SourceSetContainer> {
                    override fun call(): SourceSetContainer {
                        return this.javaPluginExtension.getSourceSets()
                    }
                })
            }
        })
    }

    private fun configureTaskRule() {
        project!!.getTasks().withType(this.castedTaskType).configureEach(object : Action<Task> {
            override fun execute(task: Task) {
                var prunedName: String = task.getName().replaceFirst(this.taskBaseName.toRegex(), "")
                if (prunedName.isEmpty()) {
                    prunedName = task.getName()
                }
                prunedName = ("" + prunedName.get(0)).lowercase() + prunedName.substring(1)
                configureTaskDefaults(task as T, prunedName)
            }
        })
    }

    protected open fun configureTaskDefaults(task: T?, baseName: String) {
    }

    private fun configureSourceSetRule() {
        withBasePlugin(object : Action<Plugin<*>> {
            override fun execute(plugin: Plugin<*>) {
                configureForSourceSets(this.javaPluginExtension.getSourceSets())
            }
        })
    }

    private fun configureForSourceSets(sourceSets: SourceSetContainer) {
        sourceSets.all(object : Action<SourceSet> {
            override fun execute(sourceSet: SourceSet) {
                project!!.getTasks().register(sourceSet.getTaskName(this.taskBaseName, null), this.castedTaskType, object : Action<Task> {
                    override fun execute(task: Task) {
                        configureForSourceSet(sourceSet, task as T)
                    }
                })
            }
        })
    }

    protected open fun configureForSourceSet(sourceSet: SourceSet, task: T?) {
    }

    private fun configureCheckTask() {
        withBasePlugin(object : Action<Plugin<*>> {
            override fun execute(plugin: Plugin<*>) {
                configureCheckTaskDependents()
            }
        })
    }

    private fun configureCheckTaskDependents() {
        val taskBaseName = this.taskBaseName
        project!!.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME, object : Action<Task> {
            override fun execute(task: Task) {
                task.dependsOn(object : Callable<Any> {
                    override fun call(): Any {
                        return Iterables.transform<SourceSet, String>(extension!!.getSourceSets(), object : Function<SourceSet, String> {
                            override fun apply(sourceSet: SourceSet): String {
                                return sourceSet.getTaskName(taskBaseName, null)
                            }
                        })
                    }
                })
            }
        })
    }

    protected fun withBasePlugin(action: Action<Plugin<*>>) {
        project!!.getPlugins().withType(this.basePlugin, action)
    }

    protected val javaPluginExtension: JavaPluginExtension
        get() = project!!.getExtensions().getByType<JavaPluginExtension>(JavaPluginExtension::class.java)

    @get:Inject
    protected abstract val jvmPluginServices: JvmPluginServices?

    companion object {
        protected fun conventionMappingOf(`object`: Any): ConventionMapping {
            return (`object` as IConventionAware).getConventionMapping()
        }
    }
}
