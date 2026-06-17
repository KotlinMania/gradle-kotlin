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
package org.gradle.api.distribution.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.distribution.DistributionContainer

/**
 *
 * Applies the [DistributionBasePlugin] and adds a conventional [main][.MAIN_DISTRIBUTION_NAME] distribution.
 *
 * @see [Distribution plugin reference](https://docs.gradle.org/current/userguide/distribution_plugin.html)
 */
abstract class DistributionPlugin : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(DistributionBasePlugin::class.java)

        val distributions = project.getExtensions().getByType<DistributionContainer>(DistributionContainer::class.java)
        distributions.create(MAIN_DISTRIBUTION_NAME)
    }

    companion object {
        /**
         * Name of the main distribution
         */
        const val MAIN_DISTRIBUTION_NAME: String = "main"

        /**
         * The name of the install task for the main distribution.
         */
        const val TASK_INSTALL_NAME: String = "installDist"
    }
}
