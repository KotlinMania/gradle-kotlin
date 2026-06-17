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

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.tasks.testing.AbstractTestDescriptor
import org.gradle.api.internal.tasks.testing.DecoratingTestDescriptor
import org.gradle.api.internal.tasks.testing.DefaultParameterizedTestDescriptor
import org.gradle.api.internal.tasks.testing.operations.ExecuteTestBuildOperationType
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestFailure
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.source.ClassSource
import org.gradle.api.tasks.testing.source.ClasspathResourceSource
import org.gradle.api.tasks.testing.source.DirectorySource
import org.gradle.api.tasks.testing.source.FilePosition
import org.gradle.api.tasks.testing.source.FileSource
import org.gradle.api.tasks.testing.source.MethodSource
import org.gradle.api.tasks.testing.source.NoSource
import org.gradle.api.tasks.testing.source.TestSource
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.build.event.types.AbstractTestResult
import org.gradle.internal.build.event.types.DefaultFileComparisonTestAssertionFailure
import org.gradle.internal.build.event.types.DefaultFilePosition
import org.gradle.internal.build.event.types.DefaultTestAssertionFailure
import org.gradle.internal.build.event.types.DefaultTestDescriptor
import org.gradle.internal.build.event.types.DefaultTestFailureResult
import org.gradle.internal.build.event.types.DefaultTestFinishedProgressEvent
import org.gradle.internal.build.event.types.DefaultTestFrameworkFailure
import org.gradle.internal.build.event.types.DefaultTestSkippedResult
import org.gradle.internal.build.event.types.DefaultTestStartedProgressEvent
import org.gradle.internal.build.event.types.DefaultTestSuccessResult
import org.gradle.internal.build.event.types.test.source.DefaultClassSource
import org.gradle.internal.build.event.types.test.source.DefaultClasspathResourceSource
import org.gradle.internal.build.event.types.test.source.DefaultDirectorySource
import org.gradle.internal.build.event.types.test.source.DefaultFileSource
import org.gradle.internal.build.event.types.test.source.DefaultMethodSource
import org.gradle.internal.build.event.types.test.source.DefaultNoSource
import org.gradle.internal.build.event.types.test.source.DefaultOtherSource
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.internal.protocol.InternalFailure
import org.gradle.tooling.internal.protocol.events.InternalFilePosition
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor
import org.gradle.tooling.internal.protocol.test.source.InternalTestSource

internal class TestOperationMapper(private val taskTracker: TaskForTestEventTracker) : BuildOperationMapper<ExecuteTestBuildOperationType.Details?, InternalTestDescriptor?> {
    override fun isEnabled(subscriptions: BuildEventSubscriptions): Boolean {
        return subscriptions.isRequested(OperationType.TEST)
    }

    val detailsType: Class<ExecuteTestBuildOperationType.Details?>?
        get() = ExecuteTestBuildOperationType.Details::class.java

    val trackers: MutableList<out BuildOperationTracker?>?
        get() = ImmutableList.of<TaskForTestEventTracker?>(taskTracker)

    override fun createDescriptor(details: ExecuteTestBuildOperationType.Details, buildOperation: BuildOperationDescriptor, parent: OperationIdentifier?): InternalTestDescriptor? {
        val testDescriptor = details.getTestDescriptor()
        return if (testDescriptor.isComposite()) toTestDescriptorForSuite(buildOperation.getId()!!, parent!!, testDescriptor) else toTestDescriptorForTest(
            buildOperation.getId()!!,
            parent!!,
            testDescriptor
        )
    }

    override fun createStartedEvent(descriptor: InternalTestDescriptor?, details: ExecuteTestBuildOperationType.Details, startEvent: OperationStartEvent?): InternalOperationStartedProgressEvent? {
        return DefaultTestStartedProgressEvent(details.getStartTime(), descriptor)
    }

    override fun createFinishedEvent(descriptor: InternalTestDescriptor?, details: ExecuteTestBuildOperationType.Details?, finishEvent: OperationFinishEvent): InternalOperationFinishedProgressEvent? {
        val testResult = (finishEvent.getResult() as ExecuteTestBuildOperationType.Result).getResult()
        return DefaultTestFinishedProgressEvent(testResult.getEndTime(), descriptor, adapt(testResult))
    }

    private fun toTestDescriptorForSuite(buildOperationId: OperationIdentifier, parentId: OperationIdentifier, suite: TestDescriptor): InternalTestDescriptor {
        var methodName: String? = null
        var operationDisplayName = suite.toString()
        val originalDescriptor: TestDescriptor = getOriginalDescriptor(suite)
        if (originalDescriptor is AbstractTestDescriptor) {
            methodName = originalDescriptor.getMethodName()
            operationDisplayName = adjustOperationDisplayNameForIntelliJ(operationDisplayName, originalDescriptor)
        } else {
            operationDisplayName = getLegacyOperationDisplayName(operationDisplayName, originalDescriptor)
        }
        val testSource: InternalTestSource = toInternalTestSource(suite.getSource())
        return DefaultTestDescriptor(
            buildOperationId,
            suite.getName(),
            operationDisplayName,
            suite.getDisplayName(),
            InternalJvmTestDescriptor.KIND_SUITE,
            suite.getName(),
            suite.getClassName(),
            methodName,
            parentId,
            taskTracker.getTaskPath(buildOperationId),
            testSource
        )
    }

    private fun toTestDescriptorForTest(buildOperationId: OperationIdentifier, parentId: OperationIdentifier, test: TestDescriptor): InternalTestDescriptor {
        var operationDisplayName = test.toString()
        val originalDescriptor: TestDescriptor = getOriginalDescriptor(test)
        if (originalDescriptor is AbstractTestDescriptor) {
            operationDisplayName = adjustOperationDisplayNameForIntelliJ(operationDisplayName, originalDescriptor)
        } else {
            operationDisplayName = getLegacyOperationDisplayName(operationDisplayName, originalDescriptor)
        }
        val testSource: InternalTestSource = toInternalTestSource(test.getSource())
        return DefaultTestDescriptor(
            buildOperationId,
            test.getName(),
            operationDisplayName,
            test.getDisplayName(),
            InternalJvmTestDescriptor.KIND_ATOMIC,
            null,
            test.getClassName(),
            test.getName(),
            parentId,
            taskTracker.getTaskPath(buildOperationId),
            testSource
        )
    }

    /**
     * This is a workaround to preserve backward compatibility with IntelliJ IDEA.
     * The problem only occurs in IntelliJ IDEA because it parses [OperationDescriptor.getDisplayName] to get the test display name.
     * Once its code is updated to use [org.gradle.tooling.events.test.TestOperationDescriptor.getTestDisplayName], the workaround can be removed as well.
     * Alternatively, it can be removed in Gradle 10.
     * See [this issue](https://github.com/gradle/gradle/issues/24538) for more details.
     */
    private fun adjustOperationDisplayNameForIntelliJ(operationDisplayName: String, descriptor: AbstractTestDescriptor): String {
        val displayName = descriptor.getDisplayName()
        if (descriptor.getName() != displayName && !(descriptor.getClassDisplayName() != null && descriptor.getName().endsWith(descriptor.getClassDisplayName()))) {
            return descriptor.getDisplayName()
        } else if (descriptor is DefaultParameterizedTestDescriptor) { // for spock parameterized tests
            return descriptor.getDisplayName()
        }
        return operationDisplayName
    }

    companion object {
        private fun toInternalTestSource(source: TestSource?): InternalTestSource {
            if (source is FileSource) {
                val fileSource = source
                return DefaultFileSource(fileSource.getFile(), toFilePosition(fileSource.getPosition()))
            } else if (source is DirectorySource) {
                return DefaultDirectorySource(source.getFile())
            } else if (source is ClassSource) {
                val classSource = source
                return DefaultClassSource(classSource.getClassName())
            } else if (source is MethodSource) {
                val methodSource = source
                return DefaultMethodSource(methodSource.getClassName(), methodSource.getMethodName())
            } else if (source is ClasspathResourceSource) {
                val classpathResourceSource = source
                return DefaultClasspathResourceSource(classpathResourceSource.getClasspathResourceName(), toFilePosition(classpathResourceSource.getPosition()))
            } else if (source is NoSource) {
                return DefaultNoSource.getInstance()
            } else {
                return DefaultOtherSource.getInstance()
            }
        }

        private fun toFilePosition(position: FilePosition?): InternalFilePosition? {
            if (position == null) {
                return null
            }
            return DefaultFilePosition(position.getLine(), position.getColumn())
        }

        /**
         * This is a workaround for Kotlin Gradle Plugin [overriding TestDescriptor](https://github.com/JetBrains/kotlin/blob/1d38040a6bef2dba31d447bf28c220b81665a710/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/internal/MppTestReportHelper.kt#L55-L64).
         * The problem only occurs in IntelliJ IDEA with multiplatform projects.
         * Once this code is removed, the workaround can be removed as well and [AbstractTestDescriptor.getMethodName] can be moved to [TestDescriptor].
         * Alternatively, it can be removed in Gradle 10.
         */
        private fun getLegacyOperationDisplayName(operationDisplayName: String, testDescriptor: TestDescriptor): String {
            // if toString() is not overridden, use the display name for test operation
            if (operationDisplayName.endsWith("@" + Integer.toHexString(testDescriptor.hashCode()))) {
                return testDescriptor.getDisplayName()
            } else {
                return operationDisplayName
            }
        }

        /**
         * can be removed once the workaround above ([1][.getLegacyOperationDisplayName] and
         * [2][.adjustOperationDisplayNameForIntelliJ]) are removed
         */
        private fun getOriginalDescriptor(testDescriptor: TestDescriptor): TestDescriptor {
            if (testDescriptor is DecoratingTestDescriptor) {
                return getOriginalDescriptor(testDescriptor.getDescriptor())
            } else {
                return testDescriptor
            }
        }

        private fun adapt(result: TestResult): AbstractTestResult {
            val resultType = result.getResultType()
            when (resultType) {
                TestResult.ResultType.SUCCESS -> return DefaultTestSuccessResult(result.getStartTime(), result.getEndTime())
                TestResult.ResultType.SKIPPED -> return DefaultTestSkippedResult(result.getStartTime(), result.getEndTime())
                TestResult.ResultType.FAILURE -> return DefaultTestFailureResult(result.getStartTime(), result.getEndTime(), convertExceptions(result.getFailures()))
                else -> throw IllegalStateException("Unknown test result type: " + resultType)
            }
        }

        private fun convertExceptions(failures: MutableList<TestFailure>): MutableList<InternalFailure?> {
            val result: MutableList<InternalFailure?> = ArrayList<InternalFailure?>(failures.size)
            for (failure in failures) {
                if (failure.getDetails().isAssertionFailure()) {
                    if (failure.getDetails().isFileComparisonFailure()) {
                        result.add(
                            DefaultFileComparisonTestAssertionFailure.create(
                                failure.getRawFailure(),
                                failure.getDetails().getMessage()!!,
                                failure.getDetails().getClassName(),
                                failure.getDetails().getStacktrace(),
                                failure.getDetails().getExpected()!!,
                                failure.getDetails().getActual()!!,
                                convertExceptions(failure.getCauses()),
                                failure.getDetails().getExpectedContent()!!,
                                failure.getDetails().getActualContent()!!
                            )
                        )
                    } else {
                        result.add(
                            DefaultTestAssertionFailure.create(
                                failure.getRawFailure(),
                                failure.getDetails().getMessage(),
                                failure.getDetails().getClassName(),
                                failure.getDetails().getStacktrace(),
                                failure.getDetails().getExpected(),
                                failure.getDetails().getActual(),
                                convertExceptions(failure.getCauses())
                            )
                        )
                    }
                } else {
                    result.add(
                        DefaultTestFrameworkFailure.create(
                            failure.getRawFailure(),
                            failure.getDetails().getMessage(),
                            failure.getDetails().getClassName(),
                            failure.getDetails().getStacktrace()
                        )
                    )
                }
            }
            return result
        }
    }
}
