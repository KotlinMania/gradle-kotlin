/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.buildinit.plugins

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.jvm.internal.JvmPluginServices
import org.gradle.api.provider.Provider
import org.gradle.buildinit.InsecureProtocolOption
import org.gradle.buildinit.tasks.InitBuild

/**
 * The build init plugin.
 *
 * @see [Build Init plugin reference](https://docs.gradle.org/current/userguide/build_init_plugin.html)
 */
abstract class BuildInitPlugin : Plugin<Project?> {
    override fun apply(project: Project) {
        if (project.getParent() == null) {
            project.getTasks().register<InitBuild?>("init", InitBuild::class.java, Action { initBuild: InitBuild? ->
                initBuild!!.setGroup("Build Setup")
                initBuild.setDescription("Initializes a new Gradle build.")

                val projectInternal = project as ProjectInternal

                val detachedResolver = projectInternal.newDetachedResolver()
                initBuild.getProjectLayoutRegistry().getBuildConverter().configureClasspath(
                    detachedResolver, project.getObjects(), projectInternal.getServices().get<JvmPluginServices?>(JvmPluginServices::class.java)!!
                )

                initBuild.getUseDefaults().convention(false)
                initBuild.getInsecureProtocol().convention(InsecureProtocolOption.WARN)
                initBuild.getAllowFileOverwrite().convention(false)
                initBuild.getComments().convention(getCommentsProperty(project).orElse(true))
                initBuild.getProjectDirectory().convention(project.getLayout().getProjectDirectory())
            })
        }
    }

    companion object {
        private const val COMMENTS_PROPERTY = "org.gradle.buildinit.comments"

        private fun getCommentsProperty(project: Project): Provider<Boolean?> {
            return project.getProviders().gradleProperty(COMMENTS_PROPERTY)
                .map<Boolean?>(SerializableLambdas.transformer<Boolean?, String?>(SerializableLambdas.SerializableTransformer { s: String? -> s.toBoolean() }))
        }
    }
}
