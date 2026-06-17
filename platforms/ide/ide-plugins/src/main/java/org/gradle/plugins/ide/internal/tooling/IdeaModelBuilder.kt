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
package org.gradle.plugins.ide.internal.tooling

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.internal.build.AllProjectsAccess
import org.gradle.internal.build.IncludedBuildState
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaContentRoot
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaDependency
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaJavaLanguageSettings
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaLanguageLevel
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaModule
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaProject
import org.gradle.plugins.ide.internal.tooling.java.DefaultInstalledJdk
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject
import org.gradle.util.Path
import java.util.LinkedList
import java.util.function.Consumer

/**
 * Builds the [org.gradle.tooling.model.idea.IdeaProject] model
 * that contains project Java language settings and a flat list of Idea modules.
 */
class IdeaModelBuilder(private val gradleProjectBuilder: GradleProjectBuilderInternal) : IdeaModelBuilderInternal {
    override fun canBuild(modelName: String): Boolean {
        return modelName == MODEL_NAME
    }

    override fun buildAll(modelName: String, project: Project): DefaultIdeaProject {
        return buildForRoot(project, false)
    }

    override fun buildForRoot(project: Project, offlineDependencyResolution: Boolean): DefaultIdeaProject {
        val root = project.getRootProject()
        applyIdeaPluginToBuildTree((root as ProjectInternal).getOwner(), HashSet<Path?>())
        val rootGradleProject = gradleProjectBuilder.buildForRoot(project)
        return build(root, rootGradleProject!!, offlineDependencyResolution)
    }

    private fun build(project: Project, rootGradleProject: DefaultGradleProject, offlineDependencyResolution: Boolean): DefaultIdeaProject {
        val ideaModel = ideaPluginFor(project).getModel()
        val projectModel = ideaModel.getProject()
        val projectSourceLanguageLevel = IdeaModuleBuilderSupport.convertToJavaVersion(projectModel.getLanguageLevel())
        val projectTargetBytecodeLevel = projectModel.getTargetBytecodeVersion()

        val out = DefaultIdeaProject()
            .setName(projectModel.getName())
            .setJdkName(projectModel.getJdkName())
            .setLanguageLevel(DefaultIdeaLanguageLevel(projectModel.getLanguageLevel().level))
            .setJavaLanguageSettings(
                DefaultIdeaJavaLanguageSettings()
                    .setSourceLanguageLevel(projectSourceLanguageLevel)
                    .setTargetBytecodeVersion(projectTargetBytecodeLevel)
                    .setJdk(DefaultInstalledJdk.current())
            )

        val ideaModules: MutableList<DefaultIdeaModule?> = ArrayList<DefaultIdeaModule?>()
        for (module in projectModel.getModules()) {
            ideaModules.add(createModule(module, out, rootGradleProject, offlineDependencyResolution))
        }
        out.setChildren(LinkedList<DefaultIdeaModule?>(ideaModules))
        return out
    }

    private fun ideaPluginFor(project: Project): IdeaPlugin {
        return project.getPlugins().getPlugin<IdeaPlugin>(IdeaPlugin::class.java)
    }

    companion object {
        private val MODEL_NAME: String = org.gradle.tooling.model.idea.IdeaProject::class.java.getName()

        private fun applyIdeaPluginToBuildTree(rootState: ProjectState, alreadyProcessed: MutableSet<Path?>) {
            val build = rootState.getOwner()
            build.getProjects().applyToMutableStateOfAllProjects(Consumer { access: AllProjectsAccess? ->
                for (p in access!!.getMutableModel(rootState).getAllprojects()) {
                    p.getPluginManager().apply(IdeaPlugin::class.java)
                }
            })
            for (reference in build.getMutableModel().includedBuilds()) {
                val target = reference.getTarget()
                if (target is IncludedBuildState) {
                    target.ensureProjectsConfigured()
                    if (alreadyProcessed.add(target.getIdentityPath())) {
                        applyIdeaPluginToBuildTree(target.getProjects().getRootProject(), alreadyProcessed)
                    }
                }
            }
        }

        private fun buildDependencies(tapiModule: DefaultIdeaModule, ideaModule: IdeaModule, offlineDependencyResolution: Boolean) {
            ideaModule.setOffline(offlineDependencyResolution)
            val resolved = ideaModule.resolveDependencies()
            val dependencies: MutableList<DefaultIdeaDependency?> = IdeaModuleBuilderSupport.buildDependencies(resolved)
            tapiModule.setDependencies(dependencies)
        }

        private fun createModule(
            ideaModule: IdeaModule,
            ideaProject: DefaultIdeaProject?,
            rootGradleProject: DefaultGradleProject,
            offlineDependencyResolution: Boolean
        ): DefaultIdeaModule {
            val contentRoot = IdeaModuleBuilderSupport.buildContentRoot(ideaModule)
            val project = ideaModule.getProject()

            val defaultIdeaModule = DefaultIdeaModule()
                .setName(ideaModule.getName())
                .setParent(ideaProject)
                .setGradleProject(rootGradleProject.findByPath(ideaModule.getProject().getPath())!!)
                .setContentRoots(mutableListOf<DefaultIdeaContentRoot?>(contentRoot))
                .setJdkName(ideaModule.getJdkName())
                .setCompilerOutput(IdeaModuleBuilderSupport.buildCompilerOutput(ideaModule))

            val javaPluginExtension: JavaPluginExtension? = project.getExtensions().findByType<JavaPluginExtension?>(JavaPluginExtension::class.java)
            if (javaPluginExtension != null) {
                val ideaModuleLanguageLevel = ideaModule.getLanguageLevel()
                val moduleSourceLanguageLevel = IdeaModuleBuilderSupport.convertToJavaVersion(ideaModuleLanguageLevel)
                val moduleTargetBytecodeVersion = ideaModule.getTargetBytecodeVersion()
                defaultIdeaModule.setJavaLanguageSettings(
                    DefaultIdeaJavaLanguageSettings()
                        .setSourceLanguageLevel(moduleSourceLanguageLevel)
                        .setTargetBytecodeVersion(moduleTargetBytecodeVersion)
                )
            }

            buildDependencies(defaultIdeaModule, ideaModule, offlineDependencyResolution)

            return defaultIdeaModule
        }
    }
}
