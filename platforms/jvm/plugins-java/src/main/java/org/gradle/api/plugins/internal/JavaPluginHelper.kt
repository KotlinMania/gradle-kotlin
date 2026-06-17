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
package org.gradle.api.plugins.internal

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.plugins.JvmTestSuitePlugin
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.jvm.component.internal.JvmSoftwareComponentInternal
import org.gradle.testing.base.TestingExtension

/**
 * Utility class intended for use only when the [JavaPlugin][org.gradle.api.plugins.JavaPlugin] is applied.
 *
 * This class exists to avoid adding these methods to the plugin itself,
 * and thus avoids adding these methods to the public API.
 */
object JavaPluginHelper {
    /**
     * Gets the main Java component. This method assumes the Java plugin is applied.
     *
     * @throws GradleException If the `java` component does not exist.
     */
    @JvmStatic
    fun getJavaComponent(project: Project): JvmSoftwareComponentInternal {
        val component = project.getComponents().findByName(JvmConstants.JAVA_MAIN_COMPONENT_NAME)

        if (component !is JvmSoftwareComponentInternal) {
            throw GradleException("The Java plugin must be applied to access the java component.")
        }

        return component
    }

    /**
     * Gets the default test suite. This method assumes the Java plugin is applied.
     *
     * @throws GradleException If the default test suite does not exist.
     */
    @JvmStatic
    fun getDefaultTestSuite(project: Project): JvmTestSuite {
        val message = "The Java plugin must be applied to access the default test suite."

        val testing: TestingExtension? = project.getExtensions().findByType<TestingExtension?>(TestingExtension::class.java)
        if (testing == null) {
            throw GradleException(message)
        }

        val defaultTestSuite = testing.getSuites().findByName(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME)
        if (defaultTestSuite !is JvmTestSuite) {
            throw GradleException(message)
        }

        return defaultTestSuite
    }
}
