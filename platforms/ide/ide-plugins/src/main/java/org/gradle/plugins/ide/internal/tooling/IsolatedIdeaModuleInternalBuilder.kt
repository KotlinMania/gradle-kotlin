/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.internal.IdeaModuleInternal
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.internal.tooling.idea.IsolatedIdeaModuleInternal
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import org.jspecify.annotations.NullMarked

/**
 * Builds the [IsolatedIdeaModuleInternal] model that contains information about a project and its tasks.
 */
@NullMarked
class IsolatedIdeaModuleInternalBuilder : ParameterizedToolingModelBuilder<IdeaModelParameter> {
    override fun getParameterType(): Class<IdeaModelParameter> {
        return IdeaModelParameter::class.java
    }

    override fun canBuild(modelName: String): Boolean {
        return modelName == IsolatedIdeaModuleInternal::class.java.getName()
    }

    override fun buildAll(modelName: String, parameter: IdeaModelParameter, project: Project): IsolatedIdeaModuleInternal {
        return build(project, parameter.getOfflineDependencyResolution())
    }

    override fun buildAll(modelName: String, project: Project): IsolatedIdeaModuleInternal {
        return build(project, false)
    }

    companion object {
        private fun build(project: Project, offlineDependencyResolution: Boolean): IsolatedIdeaModuleInternal {
            project.getPluginManager().apply(IdeaPlugin::class.java)

            val ideaModelExt = project.getExtensions().getByType<IdeaModel>(IdeaModel::class.java)
            val ideaModuleExt = ideaModelExt.getModule() as IdeaModuleInternal

            ideaModuleExt.setOffline(offlineDependencyResolution)
            val resolvedDependencies = ideaModuleExt.resolveDependencies()

            val model = IsolatedIdeaModuleInternal()

            model.name = ideaModuleExt.getName()
            model.jdkName = ideaModuleExt.getJdkName()
            model.setContentRoot(IdeaModuleBuilderSupport.buildContentRoot(ideaModuleExt))
            model.compilerOutput = IdeaModuleBuilderSupport.buildCompilerOutput(ideaModuleExt)

            // Simulating IdeaPlugin to only expose these values when 'java' plugin is applied
            if (project.getPlugins().hasPlugin(JavaPlugin::class.java)) {
                model.explicitSourceLanguageLevel = ideaModuleExt.getRawLanguageLevel()
                model.explicitTargetBytecodeVersion = ideaModuleExt.getRawTargetBytecodeVersion()
            }

            // Simulating IdeaPlugin to only expose these values when 'java-base' plugin is applied
            if (project.getPlugins().hasPlugin(JavaBasePlugin::class.java)) {
                val javaExt = project.getExtensions().getByType<JavaPluginExtension>(JavaPluginExtension::class.java)
                model.javaSourceCompatibility = javaExt.sourceCompatibility
                model.javaTargetCompatibility = javaExt.targetCompatibility
            }

            model.dependencies = IdeaModuleBuilderSupport.buildDependencies(resolvedDependencies)

            return model
        }
    }
}
