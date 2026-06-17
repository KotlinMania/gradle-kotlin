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
package org.gradle.tooling.internal.consumer

import org.gradle.tooling.events.internal.OperationDescriptorWrapper
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor
import org.gradle.tooling.internal.protocol.test.InternalDebugOptions
import org.gradle.tooling.internal.protocol.test.InternalJvmTestRequest
import org.gradle.tooling.internal.protocol.test.InternalTaskSpec
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionRequest
import org.gradle.util.internal.CollectionUtils.collect
import java.util.function.Function

class TestExecutionRequest(
    operationDescriptors: Iterable<TestOperationDescriptor?>,
    testClassNames: MutableCollection<String?>?,
    internalJvmTestRequests: MutableSet<InternalJvmTestRequest?>?,
    debugOptions: InternalDebugOptions?,
    testTasks: MutableMap<String?, MutableList<InternalJvmTestRequest?>?>?,
    isRunDefaultTasks: Boolean,
    taskSpecs: MutableList<InternalTaskSpec?>?
) : InternalTestExecutionRequest {
    private val testDescriptors: MutableCollection<InternalTestDescriptor?>
    private val testClassNames: MutableCollection<String?>?
    val internalJvmTestRequests: MutableCollection<InternalJvmTestRequest?>?
    val debugOptions: InternalDebugOptions?
    val taskAndTests: MutableMap<String?, MutableList<InternalJvmTestRequest?>?>?
    private val isRunDefaultTasks: Boolean
    val taskSpecs: MutableList<InternalTaskSpec?>?

    init {
        this.testDescriptors = adaptDescriptors(operationDescriptors)
        this.testClassNames = testClassNames
        this.internalJvmTestRequests = internalJvmTestRequests
        this.debugOptions = debugOptions
        this.taskAndTests = testTasks
        this.isRunDefaultTasks = isRunDefaultTasks
        this.taskSpecs = taskSpecs
    }

    override fun getTestExecutionDescriptors(): MutableCollection<InternalTestDescriptor?> {
        return testDescriptors
    }

    override fun getTestClassNames(): MutableCollection<String?>? {
        return testClassNames
    }

    private fun adaptDescriptors(operationDescriptors: Iterable<TestOperationDescriptor?>): MutableCollection<InternalTestDescriptor?> {
        return collect<InternalTestDescriptor?, TestOperationDescriptor?>(
            operationDescriptors,
            Function { operationDescriptor: TestOperationDescriptor? -> (operationDescriptor as OperationDescriptorWrapper).internalOperationDescriptor as InternalTestDescriptor? })
    }

    override fun isRunDefaultTasks(): Boolean {
        return isRunDefaultTasks
    }
}
