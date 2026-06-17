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
package org.gradle.nativeplatform.test.xctest.internal.execution

import org.gradle.api.internal.tasks.testing.ClassTestDefinition
import org.gradle.api.internal.tasks.testing.TestDefinitionProcessor
import org.gradle.api.internal.tasks.testing.TestExecuter
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.detection.TestDetector
import org.gradle.api.internal.tasks.testing.processors.TestMainAction
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.internal.SystemProperties
import org.gradle.internal.id.IdGenerator
import org.gradle.internal.id.LongIdGenerator
import org.gradle.internal.io.LineBufferingOutputStream
import org.gradle.internal.io.TextStream
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.internal.time.Clock
import org.gradle.process.ProcessExecutionException
import org.gradle.process.internal.ClientExecHandleBuilder
import org.gradle.process.internal.ClientExecHandleBuilderFactory.newExecHandleBuilder
import org.gradle.process.internal.ExecHandle
import java.io.File
import java.io.OutputStream
import java.util.ArrayDeque
import java.util.Deque
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Takes an XCTestTestExecutionSpec and executes the given test binary.
 *
 * This class is mostly responsible for managing the starting/stopping of the test process and wiring together the
 * different test execution bits (output scraping, event generation, process handling).
 *
 * NOTE: Eventually, we would like to replace some of this with a lower level integration with XCTest, which would
 * get rid of the output scraping and allow us to do things like:
 *
 * - Parallel test execution
 * - Smarter/fancier test filtering
 * - Test probing (so we know which tests exist without executing them)
 */
abstract class XCTestExecuter : TestExecuter<XCTestTestExecutionSpec?> {
    @get:Inject
    abstract val execHandleFactory: ClientExecHandleBuilderFactory?

    @get:Inject
    abstract val workerLeaseService: WorkerLeaseService?

    val idGenerator: IdGenerator<*>
        get() = LongIdGenerator()

    @get:Inject
    abstract val clock: Clock?

    @get:Inject
    abstract val timeProvider: Clock?

    override fun execute(testExecutionSpec: XCTestTestExecutionSpec, testResultProcessor: TestResultProcessor?) {
        val executable = testExecutionSpec.getRunScript()
        val workingDir = testExecutionSpec.getWorkingDir()

        val rootTestSuiteId = testExecutionSpec.getPath()

        val processor = XCTestProcessor(
            this.clock, executable, workingDir, this.execHandleFactory.newExecHandleBuilder(),
            this.idGenerator, rootTestSuiteId
        )

        val detector: TestDetector = XCTestDetector(processor, testExecutionSpec.getTestSelection())

        TestMainAction(detector, processor, testResultProcessor, this.workerLeaseService, this.timeProvider, rootTestSuiteId, "Gradle Test Run " + testExecutionSpec.getPath()).run()
    }

    override fun stopNow() {
        throw UnsupportedOperationException("XCTest does not support failing fast on first test failure.")
    }

    private class XCTestDetector(private val testClassProcessor: XCTestProcessor, private val testSelection: XCTestSelection) : TestDetector {
        override fun detect() {
            for (includedTests in testSelection.getIncludedTests()) {
                val testDefinition = ClassTestDefinition(includedTests)
                testClassProcessor.processTestDefinition(testDefinition)
            }
        }
    }

    internal class XCTestProcessor @Inject constructor(
        private val clock: Clock?,
        executable: File,
        workingDir: File?,
        private val execHandleBuilder: ClientExecHandleBuilder,
        private val idGenerator: IdGenerator<*>?,
        private val rootTestSuiteId: String?
    ) : TestDefinitionProcessor<ClassTestDefinition?> {
        private var resultProcessor: TestResultProcessor? = null
        private var execHandle: ExecHandle? = null

        init {
            execHandleBuilder.executable = executable.getAbsolutePath()
            execHandleBuilder.setWorkingDir(workingDir)
        }

        override fun startProcessing(resultProcessor: TestResultProcessor?) {
            this.resultProcessor = resultProcessor
        }

        override fun processTestDefinition(testDefinition: ClassTestDefinition) {
            val testSuiteIds: MutableMap<String?, Any?> = ConcurrentHashMap<String?, Any?>()
            val testDescriptors: Deque<XCTestDescriptor?> = ArrayDeque<XCTestDescriptor?>()
            val stdOut: TextStream = XCTestScraper(TestOutputEvent.Destination.StdOut, resultProcessor, idGenerator, clock, rootTestSuiteId, testDescriptors, testSuiteIds)
            val stdErr: TextStream = XCTestScraper(TestOutputEvent.Destination.StdErr, resultProcessor, idGenerator, clock, rootTestSuiteId, testDescriptors, testSuiteIds)

            val lineSeparator = SystemProperties.getInstance().getLineSeparator()
            execHandle = executeTest(testDefinition.getTestClassName(), LineBufferingOutputStream(stdOut, lineSeparator), LineBufferingOutputStream(stdErr, lineSeparator))

            try {
                execHandle!!.start()
                val result = execHandle!!.waitForFinish()
                // Exit code 0 = success
                // Exit code 1 = failed test(s)
                // anything else is considered an execution failure
                if (result!!.exitValue !== 0 && result.exitValue !== 1) {
                    result.rethrowFailure()!!.assertNormalExitValue()
                }
            } catch (e: ProcessExecutionException) {
                stdOut.endOfStream(e)
                stdErr.endOfStream(null)
            } finally {
                execHandle = null
            }
        }

        private fun executeTest(testName: String, outputStream: OutputStream, errorStream: OutputStream): ExecHandle? {
            execHandleBuilder.setArgs(toTestArgs(testName))
            execHandleBuilder.setStandardOutput(outputStream)
            execHandleBuilder.setErrorOutput(errorStream)
            return execHandleBuilder.build()
        }

        override fun stop() {
            if (execHandle != null) {
                execHandle!!.abort()
                execHandle!!.waitForFinish()
            }
        }

        override fun stopNow() {
            throw UnsupportedOperationException("XCTest does not support failing fast on first test failure.")
        }

        companion object {
            private fun toTestArgs(testName: String): MutableList<String?> {
                val args: MutableList<String?> = ArrayList<String?>()
                if (testName != XCTestSelection.Companion.INCLUDE_ALL_TESTS) {
                    if (current()!!.isMacOsX) {
                        args.add("-XCTest")
                    }
                    args.add(testName)
                }
                return args
            }
        }
    }
}
