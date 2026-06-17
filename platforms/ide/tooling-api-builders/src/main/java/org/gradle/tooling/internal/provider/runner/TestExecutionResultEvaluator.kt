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

import com.google.common.base.Strings
import org.gradle.api.Task
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.operations.ExecuteTestBuildOperationType
import org.gradle.api.tasks.testing.TestExecutionException
import org.gradle.internal.build.event.types.DefaultTestDescriptor
import org.gradle.internal.operations.BuildOperationAncestryTracker
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.tooling.internal.protocol.test.InternalJvmTestRequest
import org.gradle.tooling.internal.protocol.test.InternalTaskSpec
import org.gradle.tooling.internal.protocol.test.InternalTestSpec
import org.gradle.tooling.internal.provider.action.TestExecutionRequestAction
import java.util.Arrays
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function
import java.util.function.Supplier

internal class TestExecutionResultEvaluator(
    private val ancestryTracker: BuildOperationAncestryTracker,
    private val internalTestExecutionRequest: TestExecutionRequestAction
) : BuildOperationListener {
    private val resultCount = AtomicLong()
    private val runningTasks: MutableMap<Any?, String?> = ConcurrentHashMap<Any?, String?>()
    private val failedTests: Queue<FailedTest> = ConcurrentLinkedQueue<FailedTest>()

    fun hasUnmatchedTests(): Boolean {
        if (noTestsSelected()) {
            return false
        } else {
            return resultCount.get() == 0L
        }
    }

    private fun noTestsSelected(): Boolean {
        return noTestDescriptorsSelected() && noTestsClassesNorMethodsSelected() && noTestsSelectedInTaskSpecs()
    }

    private fun noTestDescriptorsSelected(): Boolean {
        return internalTestExecutionRequest.getTestExecutionDescriptors().isEmpty()
    }

    private fun noTestsClassesNorMethodsSelected(): Boolean {
        return internalTestExecutionRequest.getInternalJvmTestRequests().isEmpty()
    }

    private fun noTestsSelectedInTaskSpecs(): Boolean {
        return internalTestExecutionRequest.getTaskSpecs()
            .stream()
            .filter { obj: InternalTaskSpec? -> InternalTestSpec::class.java.isInstance(obj) }
            .map<InternalTestSpec?> { obj: InternalTaskSpec? -> InternalTestSpec::class.java.cast(obj) }
            .allMatch { spec: InternalTestSpec? -> Companion.emptyTest(spec!!) }
    }

    fun hasFailedTests(): Boolean {
        return !failedTests.isEmpty()
    }

    fun evaluate() {
        if (hasUnmatchedTests()) {
            val formattedTestRequest = formatInternalTestExecutionRequest()
            throw TestExecutionException("No matching tests found in any candidate test task.\n" + formattedTestRequest)
        }
        if (hasFailedTests()) {
            val failedTestsMessage = StringBuilder("Test failed.\n")
                .append(INDENT).append("Failed tests:")
            for (failedTest in failedTests) {
                failedTestsMessage.append("\n").append(twoIndent()).append(failedTest.description)
            }
            throw TestExecutionException(failedTestsMessage.toString())
        }
    }

    private fun formatInternalTestExecutionRequest(): String {
        val requestDetails = StringBuilder(INDENT).append("Requested tests:")
        for (internalTestDescriptor in internalTestExecutionRequest.getTestExecutionDescriptors()) {
            requestDetails.append("\n").append(twoIndent()).append(internalTestDescriptor.displayName)
            requestDetails.append(" (Task: '").append((internalTestDescriptor as DefaultTestDescriptor).getTaskPath()).append("')")
        }
        val internalJvmTestRequests: MutableCollection<InternalJvmTestRequest> = internalTestExecutionRequest.getInternalJvmTestRequests()

        for (internalJvmTestRequest in internalJvmTestRequests) {
            val className = internalJvmTestRequest.getClassName()
            val methodName = internalJvmTestRequest.getMethodName()
            if (methodName == null) {
                requestDetails.append("\n").append(twoIndent()).append("Test class ").append(className)
            } else {
                requestDetails.append("\n").append(twoIndent()).append("Test method ").append(className).append(".").append(methodName).append("()")
            }
        }

        for (taskSpec in internalTestExecutionRequest.getTaskSpecs()) {
            if (taskSpec is InternalTestSpec) {
                val testSpec = taskSpec
                for (cls in testSpec.getClasses()!!) {
                    requestDetails.append("\n").append(twoIndent()).append("Test class: ").append(cls).append(" in task " + taskSpec.getTaskPath())
                }
                for (methods in testSpec.getMethods()!!.entries) {
                    for (method in methods.value!!) {
                        requestDetails.append("\n").append(twoIndent()).append("Test method ").append(methods.key).append(".").append(method).append("()").append(" in task " + taskSpec.getTaskPath())
                    }
                }
                for (pkg in testSpec.getPackages()!!) {
                    requestDetails.append("\n").append(twoIndent()).append("Test package ").append(pkg).append(" in task " + taskSpec.getTaskPath())
                }
                for (pattern in testSpec.getPatterns()!!) {
                    requestDetails.append("\n").append(twoIndent()).append("Test pattern ").append(pattern).append(" in task " + taskSpec.getTaskPath())
                }
            }
        }

        return requestDetails.toString()
    }

    override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent) {
        if (buildOperation.getDetails() is ExecuteTaskBuildOperationDetails) {
            val task: Task = (buildOperation.getDetails() as ExecuteTaskBuildOperationDetails).getTask()
            runningTasks.put(buildOperation.getId(), task.getPath())
        }
    }

    override fun progress(buildOperationId: OperationIdentifier, progressEvent: OperationProgressEvent) {
    }

    override fun finished(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent) {
        if (buildOperation.getDetails() is ExecuteTaskBuildOperationDetails) {
            runningTasks.remove(buildOperation.getId())
        } else if (finishEvent.getResult() is ExecuteTestBuildOperationType.Result) {
            val testDescriptor = (buildOperation.getDetails() as ExecuteTestBuildOperationType.Details).getTestDescriptor() as TestDescriptorInternal
            val testResult = (finishEvent.getResult() as ExecuteTestBuildOperationType.Result).getResult()
            if (testDescriptor.getParent() == null) {
                resultCount.addAndGet(testResult.getTestCount())
            }
            if (!testDescriptor.isComposite() && testResult.getFailedTestCount() != 0L) {
                failedTests.add(TestExecutionResultEvaluator.FailedTest(testDescriptor.getName(), testDescriptor.getClassName()!!, getTaskPath(buildOperation.getId(), testDescriptor)))
            }
        }
    }

    private fun getTaskPath(buildOperationId: OperationIdentifier?, descriptor: TestDescriptorInternal?): String {
        return ancestryTracker.findClosestExistingAncestor<String>(buildOperationId, Function { key: OperationIdentifier? -> runningTasks.get(key) })
            .orElseThrow<IllegalStateException?>(Supplier { IllegalStateException("No parent task for test " + descriptor) })
    }

    private class FailedTest(val name: String?, val className: String, val taskPath: String?) {
        val description: String
            get() = "Test " + className + "#" + name + " (Task: " + taskPath + ")"
    }

    companion object {
        private const val INDENT = "    "

        private fun emptyTest(spec: InternalTestSpec): Boolean {
            return allEmpty(spec.getClasses(), spec.getMethods()!!.keys, spec.getPackages(), spec.getPatterns())
        }

        private fun allEmpty(vararg collections: MutableCollection<*>?): Boolean {
            return Arrays.stream<MutableCollection<*>?>(collections).allMatch { obj: MutableCollection<*>? -> obj!!.isEmpty() }
        }

        private fun twoIndent(): String {
            return Strings.repeat(INDENT, 2)
        }
    }
}
