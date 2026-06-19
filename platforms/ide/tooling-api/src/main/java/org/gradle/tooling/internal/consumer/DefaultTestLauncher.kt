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

import java.util.Arrays
import java.util.function.Function
import kotlin.Int
import kotlin.Throwable
import org.gradle.api.Action
import org.gradle.tooling.Failure
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.TestExecutionException
import org.gradle.tooling.TestLauncher
import org.gradle.tooling.TestSpecs
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.events.test.internal.DefaultDebugOptions
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.ConsumerAction
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.tooling.internal.protocol.test.InternalJvmTestRequest
import org.gradle.tooling.internal.protocol.test.InternalTaskSpec
import org.gradle.util.internal.CollectionUtils.collect
import org.gradle.util.internal.CollectionUtils.toList

class DefaultTestLauncher(connection: AsyncConsumerActionExecutor, parameters: ConnectionParameters) : AbstractLongRunningOperation<DefaultTestLauncher>(parameters), TestLauncher {
    private val connection: AsyncConsumerActionExecutor
    private val operationDescriptors: MutableSet<TestOperationDescriptor?> = LinkedHashSet<TestOperationDescriptor?>()
    private val testClassNames: MutableSet<String?> = LinkedHashSet<String?>()
    private val internalJvmTestRequests: MutableSet<InternalJvmTestRequest?> = LinkedHashSet<InternalJvmTestRequest?>()
    private val debugOptions = DefaultDebugOptions()
    private val tasksAndTests: MutableMap<String?, MutableList<InternalJvmTestRequest?>?> = HashMap<String?, MutableList<InternalJvmTestRequest?>?>()
    private var isRunDefaultTasks = false
    private val taskSpecs: MutableList<InternalTaskSpec?> = ArrayList<InternalTaskSpec?>()

    init {
        operationParamsBuilder.setTasks(mutableListOf())
        operationParamsBuilder.setEntryPoint("TestLauncher API")
        this.connection = connection
    }

    override val `this`: DefaultTestLauncher
        get() = this

    override fun withTests(vararg testDescriptors: TestOperationDescriptor?): TestLauncher {
        withTests(Arrays.asList<TestOperationDescriptor?>(*testDescriptors))
        return this
    }

    override fun withTests(descriptors: Iterable<out TestOperationDescriptor?>?): TestLauncher {
        operationDescriptors.addAll((descriptors ?: emptyList()).toList())
        return this
    }

    override fun withJvmTestClasses(vararg classNames: String?): TestLauncher {
        withJvmTestClasses(Arrays.asList<String?>(*classNames))
        return this
    }

    override fun withJvmTestClasses(testClasses: Iterable<String?>?): TestLauncher {
        val newRequests = collect<InternalJvmTestRequest?, String?>((testClasses ?: emptyList()), Function { testClass: String? -> DefaultInternalJvmTestRequest(testClass, null, null) })
        internalJvmTestRequests.addAll(newRequests)
        testClassNames.addAll((testClasses ?: emptyList()).toList())
        return this
    }

    override fun withJvmTestMethods(testClass: String?, vararg methods: String?): TestLauncher {
        withJvmTestMethods(testClass, Arrays.asList<String?>(*methods))
        return this
    }

    override fun withJvmTestMethods(testClass: String?, methods: Iterable<String?>?): TestLauncher {
        val newRequests = collect<InternalJvmTestRequest?, String?>((methods ?: emptyList()), Function { methodName: String? -> DefaultInternalJvmTestRequest(testClass, methodName, null) })
        this.internalJvmTestRequests.addAll(newRequests)
        this.testClassNames.add(testClass)
        return this
    }

    override fun withTaskAndTestClasses(task: String?, testClasses: Iterable<String?>?): TestLauncher {
        val tests = collect<InternalJvmTestRequest?, String?>((testClasses ?: emptyList()), Function { testClass: String? -> DefaultInternalJvmTestRequest(testClass, null, null) })

        addTests(task, tests)
        return this
    }

    override fun withTaskAndTestMethods(task: String?, testClass: String?, methods: Iterable<String?>?): TestLauncher {
        val tests = collect<InternalJvmTestRequest?, String?>((methods ?: emptyList()), Function { methodName: String? -> DefaultInternalJvmTestRequest(testClass, methodName, null) })
        addTests(task, tests)
        return this
    }

    private fun addTests(task: String?, tests: MutableList<InternalJvmTestRequest?>) {
        val existing = tasksAndTests.get(task)
        if (existing == null) {
            tasksAndTests.put(task, tests)
        } else {
            existing.addAll(tests)
            tasksAndTests.put(task, existing)
        }
    }

    override fun debugTestsOn(port: Int): TestLauncher {
        this.debugOptions.setPort(port)
        return this
    }

    override fun forTasks(vararg tasks: String?): TestLauncher {
        this.isRunDefaultTasks = tasks.size == 0
        for (task in tasks) {
            taskSpecs.add(DefaultTaskSpec(task))
        }
        return this
    }

    override fun run() {
        val handler = BlockingResultHandler<Void>(Void::class.java as Class<Void?>)
        run(handler)
        handler.result
    }

    override fun run(handler: ResultHandler<in Void?>?) {
        if (operationDescriptors.isEmpty() && internalJvmTestRequests.isEmpty() && tasksAndTests.isEmpty() && taskSpecs.isEmpty()) {
            throw TestExecutionException("No test declared for execution.")
        }
        for (entry in tasksAndTests.entries) {
            if (entry.value!!.isEmpty()) {
                throw TestExecutionException("No test for task " + entry.key + " declared for execution.")
            }
        }
        val operationParameters = consumerOperationParameters
        val testExecutionRequest = TestExecutionRequest(
            operationDescriptors,
            ArrayList<String?>(testClassNames),
            LinkedHashSet<InternalJvmTestRequest?>(internalJvmTestRequests),
            debugOptions,
            LinkedHashMap<String?, MutableList<InternalJvmTestRequest?>?>(tasksAndTests),
            isRunDefaultTasks,
            taskSpecs
        )
        connection.run<Void?>(object : ConsumerAction<Void?> {
            override val parameters = operationParameters

            override fun run(connection: ConsumerConnection): Void? {
                connection.runTests(testExecutionRequest, parameters)
                return null
            }
        }, ResultHandlerAdapter(handler))
    }

    private inner class ResultHandlerAdapter(handler: ResultHandler<in Void?>?) : org.gradle.tooling.internal.consumer.ResultHandlerAdapter<Void?>(
        handler,
        this@DefaultTestLauncher.createExceptionTransformer(object : ConnectionExceptionTransformer.ConnectionFailureMessageProvider {
            override fun getConnectionFailureMessage(throwable: Throwable?): String? {
                return String.format("Could not execute tests using %s.", connection.displayName)
            }
        })
    )

    override fun withTestsFor(testSpec: Action<TestSpecs?>?): TestLauncher {
        val testSpecs = DefaultTestSpecs()
        testSpec!!.execute(testSpecs)
        taskSpecs.addAll(testSpecs.testSpecs)
        return this
    }
}
