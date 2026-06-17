/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.plugin.devel.internal.precompiled

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.file.Directory
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.plugins.DefaultPluginManager
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.tasks.GroovySourceDirectorySet
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.internal.deprecation.Documentation.Companion.userManual
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.PluginDeclaration
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
import java.io.File
import java.util.function.Consumer
import java.util.stream.Collectors
import javax.inject.Inject

abstract class PrecompiledGroovyPluginsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(GroovyBasePlugin::class.java)
        project.getPluginManager().apply(JavaGradlePluginPlugin::class.java)

        project.afterEvaluate(Action { project: Project? -> this.exposeScriptsAsPlugins(project!!) })
    }

    @get:Inject
    protected abstract val textFileResourceLoader: TextFileResourceLoader?

    private fun exposeScriptsAsPlugins(project: Project) {
        val pluginExtension = project.getExtensions().getByType<GradlePluginDevelopmentExtension>(GradlePluginDevelopmentExtension::class.java)

        val pluginSourceSet = pluginExtension.getPluginSourceSet()
        val scriptPluginFiles =
            pluginSourceSet.getAllSource().matching(Action { patternFilterable: PatternFilterable? -> PrecompiledGroovyScript.Companion.filterPluginFiles(patternFilterable) }).getFiles()

        val scriptPlugins = scriptPluginFiles.stream()
            .map<PrecompiledGroovyScript> { file: File? ->
                PrecompiledGroovyScript(
                    file!!,
                    this.textFileResourceLoader
                )
            }
            .peek { scriptPlugin: PrecompiledGroovyScript? -> validateScriptPlugin(project, scriptPlugin!!) }
            .collect(Collectors.toList())

        declarePluginMetadata(pluginExtension, scriptPlugins)

        val buildDir = project.getLayout().getBuildDirectory()
        val tasks = project.getTasks()

        val extractPluginRequests = tasks.register<ExtractPluginRequestsTask>("extractPluginRequests", ExtractPluginRequestsTask::class.java, Action { task: ExtractPluginRequestsTask ->
            task.getScriptPlugins().convention(scriptPlugins)
            task.getScriptFiles().from(scriptPluginFiles)
            task.getExtractedPluginRequestsClassesDirectory().convention(buildDir.dir("groovy-dsl-plugins/output/plugin-requests"))
            task.getExtractedPluginRequestsClassesStagingDirectory().convention(buildDir.dir("groovy-dsl-plugins/output/plugin-requests-staging"))
        })

        val generatePluginAdapters = tasks.register<GeneratePluginAdaptersTask>("generatePluginAdapters", GeneratePluginAdaptersTask::class.java, Action { task: GeneratePluginAdaptersTask ->
            task.getScriptPlugins().convention(scriptPlugins)
            task.getExtractedPluginRequestsClassesDirectory()
                .convention(extractPluginRequests.flatMap<Directory>(Transformer { obj: ExtractPluginRequestsTask? -> obj!!.getExtractedPluginRequestsClassesDirectory() }))
            task.getPluginAdapterSourcesOutputDirectory().convention(buildDir.dir("groovy-dsl-plugins/output/adapter-src"))
        })

        val precompilePlugins = tasks.register<CompileGroovyScriptPluginsTask>("compileGroovyPlugins", CompileGroovyScriptPluginsTask::class.java, Action { task: CompileGroovyScriptPluginsTask ->
            task.getScriptPlugins().convention(scriptPlugins)
            task.getScriptFiles().from(scriptPluginFiles)
            task.getPrecompiledGroovyScriptsOutputDirectory().convention(buildDir.dir("groovy-dsl-plugins/output/plugin-classes"))

            val javaSource = pluginSourceSet.getJava()
            val groovySource: SourceDirectorySet = pluginSourceSet.getExtensions().getByType<GroovySourceDirectorySet>(GroovySourceDirectorySet::class.java)
            task.getClasspath().from(pluginSourceSet.getCompileClasspath(), javaSource.getClassesDirectory(), groovySource.getClassesDirectory())
        })

        pluginSourceSet.getJava().srcDir(generatePluginAdapters.flatMap<Directory>(Transformer { obj: GeneratePluginAdaptersTask? -> obj!!.getPluginAdapterSourcesOutputDirectory() }))
        pluginSourceSet.getOutput().dir(precompilePlugins.flatMap<Directory>(Transformer { obj: CompileGroovyScriptPluginsTask? -> obj!!.getPrecompiledGroovyScriptsOutputDirectory() }))
        pluginSourceSet.getOutput().dir(extractPluginRequests.flatMap<Directory>(Transformer { obj: ExtractPluginRequestsTask? -> obj!!.getExtractedPluginRequestsClassesStagingDirectory() }))
    }

    private fun validateScriptPlugin(project: Project, scriptPlugin: PrecompiledGroovyScript) {
        if (scriptPlugin.getId() == DefaultPluginManager.CORE_PLUGIN_NAMESPACE || scriptPlugin.getId().startsWith(DefaultPluginManager.CORE_PLUGIN_PREFIX)) {
            throw PrecompiledScriptException(
                String.format("The precompiled plugin (%s) cannot start with '%s'.", project.relativePath(scriptPlugin.getFileName()), DefaultPluginManager.CORE_PLUGIN_NAMESPACE),
                PRECOMPILED_SCRIPT_MANUAL.getConsultDocumentationMessage()
            )
        }
        val existingPlugin = project.getPlugins().findPlugin(scriptPlugin.getId())
        if (existingPlugin != null && existingPlugin.javaClass.getPackage().getName().startsWith(DefaultPluginManager.CORE_PLUGIN_PREFIX)) {
            throw PrecompiledScriptException(
                String.format("The precompiled plugin (%s) conflicts with the core plugin '%s'. Rename your plugin.", project.relativePath(scriptPlugin.getFileName()), scriptPlugin.getId()),
                PRECOMPILED_SCRIPT_MANUAL.getConsultDocumentationMessage()
            )
        }
    }

    private fun declarePluginMetadata(pluginExtension: GradlePluginDevelopmentExtension, scriptPlugins: MutableList<PrecompiledGroovyScript>) {
        pluginExtension.plugins(Action { pluginDeclarations: NamedDomainObjectContainer<PluginDeclaration?>? ->
            scriptPlugins.forEach(Consumer { scriptPlugin: PrecompiledGroovyScript? ->
                pluginDeclarations!!.create(
                    scriptPlugin!!.getId(),
                    Action { pluginDeclaration: PluginDeclaration? -> scriptPlugin.declarePlugin(pluginDeclaration!!) })
            })
        })
    }

    companion object {
        private val PRECOMPILED_SCRIPT_MANUAL = userManual("custom_plugins", "sec:precompiled_plugins")
    }
}
