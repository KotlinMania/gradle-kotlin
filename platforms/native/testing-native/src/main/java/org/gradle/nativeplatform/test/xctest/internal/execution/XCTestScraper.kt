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

import com.google.common.base.Joiner
import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestMethodDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent
import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.tasks.testing.TestFailure
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestResult
import org.gradle.internal.SystemProperties
import org.gradle.internal.id.IdGenerator
import org.gradle.internal.io.TextStream
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.internal.time.Clock
import org.gradle.util.internal.TextUtil
import java.util.Deque
import java.util.Scanner
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Scrapes XCTest's output and converts them into `TestResultProcessor` events.
 *
 * NOTE: We eventually want to get rid of this and use our own hooks in the test process itself.
 */
internal class XCTestScraper(
    private val destination: TestOutputEvent.Destination?,
    private val processor: TestResultProcessor,
    private val idGenerator: IdGenerator<*>,
    private val clock: Clock,
    private val rootTestSuiteId: String?,
    private val testDescriptors: Deque<XCTestDescriptor>,
    private val testSuiteIds: MutableMap<String?, Any?>
) : TextStream {
    private var lastDescriptor: TestDescriptorInternal? = null
    private var textBuilder = StringBuilder()

    override fun text(textFragment: String) {
        textBuilder.append(textFragment)
        if (!textFragment.endsWith(SystemProperties.getInstance().getLineSeparator())) {
            return
        }
        val text = textBuilder.toString()
        textBuilder = StringBuilder()
        synchronized(testDescriptors) {
            val scanner = Scanner(text).useDelimiter("'")
            if (scanner.hasNext()) {
                val token = scanner.next().trim { it <= ' ' }
                if (token == "Test Suite") {
                    // Test Suite 'PassingTestSuite' started at 2017-10-30 10:45:47.828
                    val testSuite = scanner.next()
                    if (testSuite == "All tests" || testSuite == "Selected tests" || testSuite.endsWith(".xctest")) {
                        // ignore these test suites
                        return
                    }
                    val status = scanner.next()
                    val started = status.contains("started at")

                    if (started) {
                        val testDescriptor: TestDescriptorInternal = DefaultTestClassDescriptor(idGenerator.generateId(), testSuite) // Using DefaultTestClassDescriptor to fake JUnit test
                        testSuiteIds.put(testSuite, testDescriptor.getId())
                        processor.started(testDescriptor, TestStartEvent(clock.currentTime))
                        testDescriptors.push(XCTestDescriptor(testDescriptor))
                    } else {
                        val xcTestDescriptor = testDescriptors.pop()
                        lastDescriptor = xcTestDescriptor.getDescriptorInternal()
                        val testDescriptor = xcTestDescriptor.getDescriptorInternal()
                        var resultType = TestResult.ResultType.SUCCESS
                        val failed = status.contains("failed at")
                        if (failed) {
                            resultType = TestResult.ResultType.FAILURE
                        }

                        processor.completed(testDescriptor.getId(), TestCompleteEvent(clock.currentTime, resultType))
                        // No longer should get any events needing this test suite id, clean up
                        testSuiteIds.remove(testSuite)
                    }
                } else if (token == "Test Case") {
                    // (macOS) Looks like: Test Case '-[AppTest.PassingTestSuite testCanPassTestCaseWithAssertion]' started.
                    // (Linux) Looks like: Test Case 'PassingTestSuite.testCanPassTestCaseWithAssertion' started.
                    val testSuiteAndCase = scanner.next()
                    val splits: Array<String?> = testSuiteAndCase.replace
                    ('[', ' ').replace
                    (']', ' ').split
                    ("[. ]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val testSuite: String?
                    val testCase: String?
                    if (current()!!.isMacOsX) {
                        testSuite = splits[2]
                        testCase = splits[3]
                    } else {
                        testSuite = splits[0]
                        testCase = splits[1]
                    }

                    val status = scanner.next().trim { it <= ' ' }
                    val started = status.contains("started")

                    if (started) {
                        val testDescriptor: TestDescriptorInternal = DefaultTestMethodDescriptor(idGenerator.generateId(), testSuite, testCase)
                        val parentId = testSuiteIds.get(testSuite)
                        processor.started(testDescriptor, TestStartEvent(clock.currentTime, parentId))
                        testDescriptors.push(XCTestDescriptor(testDescriptor))
                    } else {
                        val xcTestDescriptor = testDescriptors.pop()
                        lastDescriptor = xcTestDescriptor.getDescriptorInternal()
                        val testDescriptor = xcTestDescriptor.getDescriptorInternal()
                        var resultType = TestResult.ResultType.SUCCESS
                        val failed = status.contains("failed")
                        if (failed) {
                            resultType = TestResult.ResultType.FAILURE
                            val failure = Throwable(Joiner.on(TextUtil.getPlatformLineSeparator()).join(xcTestDescriptor.getMessages()))
                            processor.failure(testDescriptor.getId(), TestFailure.fromTestFrameworkFailure(failure))
                        }

                        processor.completed(testDescriptor.getId(), TestCompleteEvent(clock.currentTime, resultType))
                    }
                } else {
                    val xcTestDescriptor = testDescriptors.peek()
                    if (xcTestDescriptor != null) {
                        val testDescriptor = xcTestDescriptor.getDescriptorInternal()

                        processor.output(testDescriptor.getId(), DefaultTestOutputEvent(clock.currentTime, destination, text))

                        val failureMessageMatcher: Matcher = TEST_FAILURE_PATTERN.matcher(text)
                        if (failureMessageMatcher.find()) {
                            val testSuite = failureMessageMatcher.group(2)
                            val testCase = failureMessageMatcher.group(3)
                            val message = failureMessageMatcher.group(4)

                            if (testDescriptor.getClassName() == testSuite && testDescriptor.getName() == testCase) {
                                xcTestDescriptor.getMessages().add(message!!)
                            }
                        }

                        // If no current test can be associated to the output, the last known descriptor is used.
                        // See https://bugs.swift.org/browse/SR-1127 for more information.
                    } else if (lastDescriptor != null) {
                        processor.output(lastDescriptor!!.getId(), DefaultTestOutputEvent(clock.currentTime, destination, text))
                    } else {
                        // If there is no known last descriptor, associate it with the root test suite
                        processor.output(rootTestSuiteId, DefaultTestOutputEvent(clock.currentTime, destination, text))
                    }
                }
            }
        }
    }

    override fun endOfStream(failure: Throwable?) {
        if (failure != null) {
            synchronized(testDescriptors) {
                val testId: Any?
                if (!testDescriptors.isEmpty()) {
                    testId = testDescriptors.pop().getDescriptorInternal().getId()
                } else {
                    testId = rootTestSuiteId
                }
                processor.failure(testId, TestFailure.fromTestFrameworkFailure(failure))
                testDescriptors.clear()
            }
        }
    }

    companion object {
        private val TEST_FAILURE_PATTERN: Pattern = Pattern.compile(":\\d+: error: (-\\[\\p{Alnum}+.)?(\\p{Alnum}+)[ .](\\p{Alnum}+)]? : (.*)")
    }
}
