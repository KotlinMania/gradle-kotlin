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
package org.gradle.api.plugins

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.file.Directory
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.plugins.jvm.JvmTestSuiteTarget
import org.gradle.api.plugins.jvm.internal.DefaultJvmTestSuite
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.base.TestingExtension
import org.gradle.testing.base.plugins.TestSuiteBasePlugin

/**
 * A [Plugin] that adds extensions for declaring, compiling and running [JvmTestSuite]s.
 *
 *
 * This plugin provides conventions for several things:
 *
 *  * All other `JvmTestSuite` will use the JUnit Jupiter testing framework unless specified otherwise.
 *  * A single test suite target is added to each `JvmTestSuite`.
 *
 *
 *
 * @since 7.3
 * @see [Test Suite plugin reference](https://docs.gradle.org/current/userguide/jvm_test_suite_plugin.html)
 */
@Incubating
abstract class JvmTestSuitePlugin : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(TestSuiteBasePlugin::class.java)
        project.getPluginManager().apply(JavaBasePlugin::class.java)

        val java = project.getExtensions().getByType<JavaPluginExtension>(JavaPluginExtension::class.java)
        project.getTasks().withType<Test?>(Test::class.java).configureEach(Action { test: Test? ->
            test!!.modularity.getInferModulePath().convention(java.modularity.getInferModulePath())
        })

        val testing = project.getExtensions().getByType<TestingExtension>(TestingExtension::class.java)
        testing.getSuites().registerBinding<JvmTestSuite?>(JvmTestSuite::class.java, DefaultJvmTestSuite::class.java)

        testing.getSuites().withType<JvmTestSuite?>(JvmTestSuite::class.java).all(Action { testSuite: JvmTestSuite? ->
            testSuite!!.getTargets().all({ target: JvmTestSuiteTarget? ->
                target!!.getTestTask().configure(Action { test: Test? ->
                    test!!.getConventionMapping().map("testClassesDirs", java.util.concurrent.Callable { testSuite.getSources().output!!.classesDirs })
                    test.getConventionMapping().map("classpath", java.util.concurrent.Callable { testSuite.getSources().runtimeClasspath })
                })
                target.getBinaryResultsDirectory().convention(target.getTestTask().flatMap<Directory?>(Transformer { obj: Test? -> obj!!.getBinaryResultsDirectory() }))
            })
        })
    }

    companion object {
        const val DEFAULT_TEST_SUITE_NAME: String = "test"
    }
}
