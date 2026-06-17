/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.internal.JavaPluginHelper.getDefaultTestSuite
import org.gradle.api.plugins.internal.JavaPluginHelper.getJavaComponent
import javax.inject.Inject

/**
 *
 * A [Plugin] which extends the capabilities of the [Java plugin][JavaPlugin] by cleanly separating
 * the API and implementation dependencies of a library.
 *
 * @since 3.4
 * @see [Java Library plugin reference](https://docs.gradle.org/current/userguide/java_library_plugin.html)
 */
abstract class JavaLibraryPlugin @Inject constructor() : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(JavaPlugin::class.java)

        val component = getJavaComponent(project)
        component.mainFeature.withApi()

        // Make compileOnlyApi visible to tests.
        val defaultTestSuite = getDefaultTestSuite(project)
        project.getConfigurations()
            .getByName(defaultTestSuite.sources.compileOnlyConfigurationName!!)
            .extendsFrom(component.mainFeature.compileOnlyApiConfiguration)
    }
}
