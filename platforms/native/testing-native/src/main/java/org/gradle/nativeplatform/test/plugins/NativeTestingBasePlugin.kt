/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.nativeplatform.test.plugins

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.ComponentWithTargetMachines
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.plugins.NativeBasePlugin
import org.gradle.nativeplatform.TargetMachineFactory
import org.gradle.nativeplatform.internal.DefaultTargetMachineFactory
import org.gradle.nativeplatform.test.TestSuiteComponent
import org.gradle.testing.base.plugins.TestingBasePlugin
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * Common base plugin for all native testing plugins.
 *
 *
 * Expects plugins to register the native test suites in the [Project.getComponents] container, and defines a number of rules that act on these components to configure them.
 *
 *
 *
 *  * Adds a `"test"` task.
 *
 *  * Configures the `"test"` task to run the tests of the `test` component, if present. Expects the test component to be of type [TestSuiteComponent].
 *
 *
 * @since 4.5
 */
@Incubating
abstract class NativeTestingBasePlugin @Inject constructor(private val targetMachineFactory: TargetMachineFactory) : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(LifecycleBasePlugin::class.java)
        project.getPluginManager().apply(NativeBasePlugin::class.java)
        project.getPluginManager().apply(TestingBasePlugin::class.java)

        // Create test lifecycle task
        val tasks = project.getTasks()

        val test: TaskProvider<Task?> = tasks.register(TEST_TASK_NAME, Action { task: Task? ->
            task!!.dependsOn(Callable {
                val unitTestSuite = project.getComponents().withType<TestSuiteComponent?>(TestSuiteComponent::class.java).findByName(TEST_COMPONENT_NAME)
                if (unitTestSuite != null && unitTestSuite.testBinary.isPresent()) {
                    return@Callable unitTestSuite.testBinary.get().getRunTask()
                }
                null
            } as Callable<Any?>)
        })

        project.getComponents().withType<TestSuiteComponent?>(TestSuiteComponent::class.java, Action { testSuiteComponent: TestSuiteComponent? ->
            if (testSuiteComponent is ComponentWithTargetMachines) {
                val componentWithTargetMachines = testSuiteComponent as ComponentWithTargetMachines
                if (TEST_COMPONENT_NAME == testSuiteComponent.getName()) {
                    test.configure(Action { task: Task? ->
                        task!!.dependsOn(Callable {
                            val currentHost = (targetMachineFactory as DefaultTargetMachineFactory).host()
                            val targetsCurrentMachine: Boolean = componentWithTargetMachines.targetMachines.get().stream()
                                .anyMatch({ targetMachine -> currentHost.operatingSystemFamily!!.equals(targetMachine.getOperatingSystemFamily()) })
                            if (!targetsCurrentMachine) {
                                task.getLogger().warn("'" + testSuiteComponent.getName() + "' component in project '" + project.getPath() + "' does not target this operating system.")
                            }
                            mutableListOf<Any?>()
                        } as Callable<*>)
                    })
                }
            }
        })

        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, Action { task: Task? -> task!!.dependsOn(test) })
    }

    companion object {
        private const val TEST_TASK_NAME = "test"
        private const val TEST_COMPONENT_NAME = "test"
    }
}
