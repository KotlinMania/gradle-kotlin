/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling.internal.provider.runner

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestExecutionException
import org.gradle.execution.EntryTaskSelector
import org.gradle.execution.TaskSelection
import org.gradle.execution.TaskSelectionException
import org.gradle.execution.plan.ExecutionPlan
import org.gradle.execution.plan.QueryableExecutionPlan
import org.gradle.internal.build.event.types.DefaultTestDescriptor
import org.gradle.process.JavaDebugOptions
import org.gradle.process.internal.DefaultJavaDebugOptions
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor
import org.gradle.tooling.internal.protocol.events.InternalSourceAwareTestDescriptor
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor
import org.gradle.tooling.internal.protocol.test.InternalJvmTestRequest
import org.gradle.tooling.internal.protocol.test.InternalTestSpec
import org.gradle.tooling.internal.protocol.test.source.InternalClassSource
import org.gradle.tooling.internal.protocol.test.source.InternalFilesystemSource
import org.gradle.tooling.internal.protocol.test.source.InternalMethodSource
import org.gradle.tooling.internal.protocol.test.source.InternalTestSource
import org.gradle.tooling.internal.provider.action.TestExecutionRequestAction
import org.jspecify.annotations.NullMarked
import java.util.function.Consumer

@NullMarked
internal class TestExecutionBuildConfigurationAction(private val testExecutionRequest: TestExecutionRequestAction) : EntryTaskSelector {
    override fun applyTasksTo(context: EntryTaskSelector.Context, plan: ExecutionPlan) {
        val allTasksToRun: MutableSet<Task> = LinkedHashSet<Task>()
        collectTasksForTestDescriptors(context, allTasksToRun)
        collectTasksForInternalJvmTestRequest(context.getGradle(), allTasksToRun)
        collectTestTasks(context, allTasksToRun)
        configureTestTasks(allTasksToRun)
        addEntryTasksTo(plan, allTasksToRun)
    }

    override fun postProcessExecutionPlan(context: EntryTaskSelector.Context, plan: QueryableExecutionPlan) {
        configureTestTasksForTestDescriptors(context)
        configureTestTasksForInternalJvmTestRequest(plan)
        configureTestTasksInBuild(context)
    }

    private fun configureTestTasks(tasks: MutableSet<Task>) {
        for (task in tasks) {
            if (task is AbstractTestTask) {
                configureTestTask(task)
            }
        }
    }

    private fun configureTestTask(test: AbstractTestTask) {
        test.getFilter().setFailOnNoMatchingTests(false)
        test.getOutputs().upToDateWhen(Specs.SATISFIES_NONE)
        if (test is Test) {
            val debugOptions = testExecutionRequest.getDebugOptions()
            if (debugOptions.isDebugMode()) {
                test.debugOptions(Action { javaDebugOptions: JavaDebugOptions? ->
                    val options = javaDebugOptions as DefaultJavaDebugOptions
                    options.getEnabled().set(true)
                    options.getPort().set(debugOptions.getPort())
                    options.getServer().set(false)
                    options.getSuspend().set(false)
                })
            } else {
                test.debugOptions(Action { javaDebugOptions: JavaDebugOptions? ->
                    val options = javaDebugOptions as DefaultJavaDebugOptions
                    options.getEnabled().set(false)
                })
            }
        }
    }

    private fun configureTestTasksForTestDescriptors(context: EntryTaskSelector.Context) {
        val taskAndTests = testExecutionRequest.getTaskAndTests()
        for (entry in taskAndTests.entries) {
            val testTaskPath = entry.key
            for (testTask in queryTestTasks(context, testTaskPath)) {
                configureTestTask(testTask)
                for (jvmTestRequest in entry.value) {
                    val filter = testTask.getFilter()
                    filter.includeTest(jvmTestRequest.getClassName(), jvmTestRequest.getMethodName())
                }
            }
        }

        for (taskSpec in testExecutionRequest.getTaskSpecs()) {
            if (taskSpec is InternalTestSpec) {
                val testSpec = taskSpec
                val tasks: MutableSet<AbstractTestTask> = Companion.queryTestTasks(context, taskSpec.getTaskPath()!!)
                for (task in tasks) {
                    val filter = task.getFilter() as DefaultTestFilter
                    for (cls in testSpec.getClasses()!!) {
                        filter.includeCommandLineTest(cls, null)
                    }
                    for (entry in testSpec.getMethods()!!.entries) {
                        val cls: String = entry.key!!
                        for (method in entry.value!!) {
                            filter.includeCommandLineTest(cls, method)
                        }
                    }
                    val commandLineIncludePatterns = filter.getCommandLineIncludePatterns()
                    commandLineIncludePatterns.addAll(testSpec.getPatterns())
                    for (pkg in testSpec.getPackages()!!) {
                        commandLineIncludePatterns.add(pkg + ".*")
                    }
                }
            }
        }
    }

    private fun configureTestTasksForInternalJvmTestRequest(plan: QueryableExecutionPlan) {
        val internalJvmTestRequests: MutableCollection<InternalJvmTestRequest> = testExecutionRequest.getInternalJvmTestRequests()
        if (internalJvmTestRequests.isEmpty()) {
            return
        }

        forEachTaskIn(plan, Consumer { task: Task ->
            if (task is AbstractTestTask) {
                val testTask = task
                configureTestTask(testTask)
                for (jvmTestRequest in internalJvmTestRequests) {
                    val filter = testTask.getFilter()
                    filter.includeTest(jvmTestRequest.getClassName(), jvmTestRequest.getMethodName())
                }
            }
        })
    }

    private fun configureTestTasksInBuild(context: EntryTaskSelector.Context) {
        val testDescriptors: MutableCollection<InternalTestDescriptor> = testExecutionRequest.getTestExecutionDescriptors()
        warnIfUnsupportedTestRerunningForResourceBasedTests(testDescriptors)
        for (descriptor in testDescriptors) {
            val testTaskPath: String = taskPathOf(descriptor)
            for (testTask in queryTestTasks(context, testTaskPath)) {
                configureTestTask(testTask)
                for (testDescriptor in testDescriptors) {
                    if (taskPathOf(testDescriptor) == testTaskPath) {
                        includeTestMatching(testDescriptor as InternalJvmTestDescriptor, testTask)
                    }
                }
            }
        }
    }

    private fun collectTasksForTestDescriptors(context: EntryTaskSelector.Context, tasksToRun: MutableCollection<Task>) {
        val taskAndTests = testExecutionRequest.getTaskAndTests()
        for (entry in taskAndTests.entries) {
            val testTaskPath = entry.key
            tasksToRun.addAll(queryTestTasks(context, testTaskPath))
        }

        for (taskSpec in testExecutionRequest.getTaskSpecs()) {
            if (taskSpec is InternalTestSpec) {
                tasksToRun.addAll(Companion.queryTestTasks(context, taskSpec.getTaskPath()!!))
            } else {
                tasksToRun.addAll(Companion.queryTasks(context, taskSpec.getTaskPath()!!))
            }
        }
    }

    private fun collectTestTasks(context: EntryTaskSelector.Context, testTasksToRun: MutableCollection<Task>) {
        for (descriptor in testExecutionRequest.getTestExecutionDescriptors()) {
            val testTaskPath: String = taskPathOf(descriptor)
            testTasksToRun.addAll(queryTestTasks(context, testTaskPath))
        }
    }

    private fun collectTasksForInternalJvmTestRequest(gradle: GradleInternal, tasksToExecute: MutableCollection<Task>) {
        val internalJvmTestRequests: MutableCollection<InternalJvmTestRequest> = testExecutionRequest.getInternalJvmTestRequests()
        if (internalJvmTestRequests.isEmpty()) {
            return
        }

        gradle.getOwner().ensureProjectsConfigured()
        for (projectState in gradle.getOwner().getProjects().getAllProjects()) {
            projectState.ensureConfigured()
            projectState.applyToMutableState(Consumer { project: ProjectInternal? ->
                val testTasks: MutableCollection<AbstractTestTask> = project!!.getTasks().withType<AbstractTestTask>(AbstractTestTask::class.java)
                tasksToExecute.addAll(testTasks)
            })
        }
    }

    companion object {
        private val LOG: Logger = getLogger(TestExecutionBuildConfigurationAction::class.java)!!

        private fun addEntryTasksTo(plan: ExecutionPlan, allTasksToRun: MutableSet<Task>) {
            for (task in allTasksToRun) {
                plan.addEntryTask(task)
            }
        }

        private fun warnIfUnsupportedTestRerunningForResourceBasedTests(testDescriptors: MutableCollection<InternalTestDescriptor>) {
            val seenTasks: MutableSet<String> = LinkedHashSet<String>()
            for (descriptor in testDescriptors) {
                if (descriptor is InternalSourceAwareTestDescriptor) {
                    val sd = descriptor
                    if (sd.source is InternalFilesystemSource) {
                        val taskPath: String = taskPathOf(descriptor)
                        if (!seenTasks.contains(taskPath)) {
                            LOG.warn("Re-running resource-based tests is not supported via TestLauncher API. The '{}' task will be scheduled without further filtering.", taskPath)
                            seenTasks.add(taskPath)
                        }
                    }
                }
            }
        }

        private fun includeTestMatching(descriptor: InternalJvmTestDescriptor, testTask: AbstractTestTask) {
            val className = descriptor.className
            val methodName = descriptor.methodName
            // for resource-based tests, don't apply any class-based filtering
            if (isClassBasedTestDescriptor(descriptor)) {
                if (className == null && methodName == null) {
                    testTask.getFilter().includeTestsMatching("*")
                } else {
                    testTask.getFilter().includeTest(className, methodName)
                }
            }
        }

        private fun isClassBasedTestDescriptor(descriptor: InternalJvmTestDescriptor): Boolean {
            if (descriptor is InternalSourceAwareTestDescriptor) {
                val source: InternalTestSource? = descriptor.source
                return source is InternalClassSource || source is InternalMethodSource
            } else {
                // assume class-based when no source information is available
                return true
            }
        }

        private fun queryTasks(context: EntryTaskSelector.Context, testTaskPath: String): MutableSet<Task> {
            val taskSelection: TaskSelection?
            try {
                taskSelection = context.getSelection(testTaskPath)
            } catch (e: TaskSelectionException) {
                throw TestExecutionException(String.format("Requested test task with path '%s' cannot be found.", testTaskPath))
            }

            val tasks = taskSelection.getTasks()
            if (tasks.isEmpty()) {
                throw TestExecutionException(String.format("Requested test task with path '%s' cannot be found.", testTaskPath))
            }

            return tasks
        }

        private fun queryTestTasks(context: EntryTaskSelector.Context, testTaskPath: String): MutableSet<AbstractTestTask> {
            val result: MutableSet<AbstractTestTask> = LinkedHashSet<AbstractTestTask>()
            for (task in queryTasks(context, testTaskPath)) {
                if (task !is AbstractTestTask) {
                    throw TestExecutionException(String.format("Task '%s' of type '%s' not supported for executing tests via TestLauncher API.", testTaskPath, task.javaClass.getName()))
                }
                result.add(task)
            }
            return result
        }

        private fun forEachTaskIn(plan: QueryableExecutionPlan, taskConsumer: Consumer<Task>) {
            plan.getTasks().forEach(taskConsumer)
        }

        private fun taskPathOf(descriptor: InternalTestDescriptor): String {
            return (descriptor as DefaultTestDescriptor).getTaskPath()!!
        }
    }
}
