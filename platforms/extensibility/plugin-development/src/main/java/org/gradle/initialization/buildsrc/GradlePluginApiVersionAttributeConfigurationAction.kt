/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.initialization.buildsrc

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.attributes.plugin.GradlePluginApiVersion
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.util.GradleVersion

/**
 * Sets the attribute [GradlePluginApiVersion] to the current Gradle version on the project's
 * compile classpath and runtime classpath configurations as soon as the java-base plugin is applied,
 * so that those configurations resolve Gradle multi-variant plugin dependencies for the current API version.
 */
class GradlePluginApiVersionAttributeConfigurationAction : BuildSrcProjectConfigurationAction {
    override fun execute(project: ProjectInternal) {
        project.getPlugins().withType<JavaBasePlugin?>(JavaBasePlugin::class.java, Action { javaBasePlugin: JavaBasePlugin? -> addGradlePluginApiVersionAttributeToClasspath(project) })
    }

    private fun addGradlePluginApiVersionAttributeToClasspath(project: ProjectInternal) {
        val configurations: ConfigurationContainer = project.getConfigurations()

        project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getSourceSets()
            .all(Action { sourceSet: SourceSet? -> setAttributeForSourceSet(sourceSet!!, configurations) }
            )
    }

    private fun setAttributeForSourceSet(sourceSet: SourceSet, configurations: ConfigurationContainer) {
        Companion.setAttributeForConfiguration(configurations.named(sourceSet.compileClasspathConfigurationName))
        Companion.setAttributeForConfiguration(configurations.named(sourceSet.runtimeClasspathConfigurationName))
    }

    companion object {
        private fun setAttributeForConfiguration(configurationProvider: NamedDomainObjectProvider<Configuration?>) {
            configurationProvider.configure(Action { configuration: Configuration? ->
                val attrs = configuration!!.getAttributes()
                attrs.attribute<GradlePluginApiVersion?>(
                    GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE,
                    attrs.named<GradlePluginApiVersion?>(GradlePluginApiVersion::class.java, GradleVersion.current().getVersion())
                )
            }
            )
        }
    }
}
