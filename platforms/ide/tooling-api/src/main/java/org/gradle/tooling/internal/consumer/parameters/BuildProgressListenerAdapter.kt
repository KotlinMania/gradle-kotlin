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
package org.gradle.tooling.internal.consumer.parameters

import com.google.common.collect.ImmutableList
import java.io.File
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.event.ListenerBroadcast
import org.gradle.tooling.Failure
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.OperationResult
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.PluginIdentifier
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.StartEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationFinishEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationDescriptor
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationResult
import org.gradle.tooling.events.configuration.ProjectConfigurationProgressEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationStartEvent
import org.gradle.tooling.events.configuration.internal.DefaultPluginApplicationResult
import org.gradle.tooling.events.configuration.internal.DefaultProjectConfigurationFailureResult
import org.gradle.tooling.events.configuration.internal.DefaultProjectConfigurationFinishEvent
import org.gradle.tooling.events.configuration.internal.DefaultProjectConfigurationOperationDescriptor
import org.gradle.tooling.events.configuration.internal.DefaultProjectConfigurationStartEvent
import org.gradle.tooling.events.configuration.internal.DefaultProjectConfigurationSuccessResult
import org.gradle.tooling.events.download.FileDownloadFinishEvent
import org.gradle.tooling.events.download.FileDownloadOperationDescriptor
import org.gradle.tooling.events.download.FileDownloadProgressEvent
import org.gradle.tooling.events.download.FileDownloadResult
import org.gradle.tooling.events.download.FileDownloadStartEvent
import org.gradle.tooling.events.download.internal.DefaultFileDownloadFailureResult
import org.gradle.tooling.events.download.internal.DefaultFileDownloadFinishEvent
import org.gradle.tooling.events.download.internal.DefaultFileDownloadOperationDescriptor
import org.gradle.tooling.events.download.internal.DefaultFileDownloadStartEvent
import org.gradle.tooling.events.download.internal.DefaultFileDownloadSuccessResult
import org.gradle.tooling.events.download.internal.NotFoundFileDownloadSuccessResult
import org.gradle.tooling.events.internal.DefaultBinaryPluginIdentifier
import org.gradle.tooling.events.internal.DefaultFinishEvent
import org.gradle.tooling.events.internal.DefaultOperationDescriptor
import org.gradle.tooling.events.internal.DefaultOperationFailureResult
import org.gradle.tooling.events.internal.DefaultOperationSuccessResult
import org.gradle.tooling.events.internal.DefaultScriptPluginIdentifier
import org.gradle.tooling.events.internal.DefaultStartEvent
import org.gradle.tooling.events.internal.DefaultStatusEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseFinishEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseOperationDescriptor
import org.gradle.tooling.events.lifecycle.BuildPhaseProgressEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseStartEvent
import org.gradle.tooling.events.lifecycle.internal.DefaultBuildPhaseFinishEvent
import org.gradle.tooling.events.lifecycle.internal.DefaultBuildPhaseOperationDescriptor
import org.gradle.tooling.events.lifecycle.internal.DefaultBuildPhaseStartEvent
import org.gradle.tooling.events.problems.AdditionalData
import org.gradle.tooling.events.problems.ContextualLabel
import org.gradle.tooling.events.problems.Details
import org.gradle.tooling.events.problems.DocumentationLink
import org.gradle.tooling.events.problems.Location
import org.gradle.tooling.events.problems.Problem
import org.gradle.tooling.events.problems.ProblemContext
import org.gradle.tooling.events.problems.ProblemDefinition
import org.gradle.tooling.events.problems.ProblemEvent
import org.gradle.tooling.events.problems.ProblemGroup
import org.gradle.tooling.events.problems.ProblemId
import org.gradle.tooling.events.problems.ProblemSummary
import org.gradle.tooling.events.problems.Severity
import org.gradle.tooling.events.problems.Solution
import org.gradle.tooling.events.problems.internal.DefaultAdditionalData
import org.gradle.tooling.events.problems.internal.DefaultContextualLabel
import org.gradle.tooling.events.problems.internal.DefaultCustomAdditionalData
import org.gradle.tooling.events.problems.internal.DefaultDetails
import org.gradle.tooling.events.problems.internal.DefaultFileLocation
import org.gradle.tooling.events.problems.internal.DefaultLineInFileLocation
import org.gradle.tooling.events.problems.internal.DefaultOffsetInFileLocation
import org.gradle.tooling.events.problems.internal.DefaultPluginIdLocation
import org.gradle.tooling.events.problems.internal.DefaultProblem
import org.gradle.tooling.events.problems.internal.DefaultProblemAggregation
import org.gradle.tooling.events.problems.internal.DefaultProblemAggregationEvent
import org.gradle.tooling.events.problems.internal.DefaultProblemDefinition
import org.gradle.tooling.events.problems.internal.DefaultProblemGroup
import org.gradle.tooling.events.problems.internal.DefaultProblemId
import org.gradle.tooling.events.problems.internal.DefaultProblemSummariesEvent
import org.gradle.tooling.events.problems.internal.DefaultProblemSummary
import org.gradle.tooling.events.problems.internal.DefaultProblemsOperationContext
import org.gradle.tooling.events.problems.internal.DefaultSeverity.Companion.from
import org.gradle.tooling.events.problems.internal.DefaultSingleProblemEvent
import org.gradle.tooling.events.problems.internal.DefaultSolution
import org.gradle.tooling.events.problems.internal.DefaultTaskPathLocation
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.task.TaskOperationResult
import org.gradle.tooling.events.task.TaskProgressEvent
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.tooling.events.task.internal.DefaultTaskFailureResult
import org.gradle.tooling.events.task.internal.DefaultTaskFinishEvent
import org.gradle.tooling.events.task.internal.DefaultTaskOperationDescriptor
import org.gradle.tooling.events.task.internal.DefaultTaskSkippedResult
import org.gradle.tooling.events.task.internal.DefaultTaskStartEvent
import org.gradle.tooling.events.task.internal.DefaultTaskSuccessResult
import org.gradle.tooling.events.task.internal.TaskExecutionDetails
import org.gradle.tooling.events.task.internal.TaskExecutionDetails.Companion.unsupported
import org.gradle.tooling.events.task.internal.java.DefaultAnnotationProcessorResult
import org.gradle.tooling.events.task.internal.java.DefaultJavaCompileTaskSuccessResult
import org.gradle.tooling.events.task.java.JavaCompileTaskOperationResult
import org.gradle.tooling.events.test.Destination.Companion.fromCode
import org.gradle.tooling.events.test.JvmTestKind
import org.gradle.tooling.events.test.TestFinishEvent
import org.gradle.tooling.events.test.TestMetadataEvent
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.events.test.TestOperationResult
import org.gradle.tooling.events.test.TestOutputDescriptor
import org.gradle.tooling.events.test.TestOutputEvent
import org.gradle.tooling.events.test.TestProgressEvent
import org.gradle.tooling.events.test.TestStartEvent
import org.gradle.tooling.events.test.internal.DefaultJvmTestOperationDescriptor
import org.gradle.tooling.events.test.internal.DefaultTestFailureResult
import org.gradle.tooling.events.test.internal.DefaultTestFileAttachmentMetadataEvent
import org.gradle.tooling.events.test.internal.DefaultTestFinishEvent
import org.gradle.tooling.events.test.internal.DefaultTestKeyValueMetadataEvent
import org.gradle.tooling.events.test.internal.DefaultTestOperationDescriptor
import org.gradle.tooling.events.test.internal.DefaultTestOutputEvent
import org.gradle.tooling.events.test.internal.DefaultTestOutputOperationDescriptor
import org.gradle.tooling.events.test.internal.DefaultTestSkippedResult
import org.gradle.tooling.events.test.internal.DefaultTestStartEvent
import org.gradle.tooling.events.test.internal.DefaultTestSuccessResult
import org.gradle.tooling.events.test.internal.source.DefaultClassSource
import org.gradle.tooling.events.test.internal.source.DefaultClasspathResourceSource
import org.gradle.tooling.events.test.internal.source.DefaultDirectorySource
import org.gradle.tooling.events.test.internal.source.DefaultFilePosition
import org.gradle.tooling.events.test.internal.source.DefaultFileSource
import org.gradle.tooling.events.test.internal.source.DefaultMethodSource
import org.gradle.tooling.events.test.internal.source.DefaultNoSource
import org.gradle.tooling.events.test.internal.source.DefaultOtherSource
import org.gradle.tooling.events.test.source.FilePosition
import org.gradle.tooling.events.test.source.TestSource
import org.gradle.tooling.events.transform.TransformFinishEvent
import org.gradle.tooling.events.transform.TransformOperationDescriptor
import org.gradle.tooling.events.transform.TransformOperationResult
import org.gradle.tooling.events.transform.TransformProgressEvent
import org.gradle.tooling.events.transform.TransformStartEvent
import org.gradle.tooling.events.transform.internal.DefaultTransformFailureResult
import org.gradle.tooling.events.transform.internal.DefaultTransformFinishEvent
import org.gradle.tooling.events.transform.internal.DefaultTransformOperationDescriptor
import org.gradle.tooling.events.transform.internal.DefaultTransformStartEvent
import org.gradle.tooling.events.transform.internal.DefaultTransformSuccessResult
import org.gradle.tooling.events.work.WorkItemFinishEvent
import org.gradle.tooling.events.work.WorkItemOperationDescriptor
import org.gradle.tooling.events.work.WorkItemOperationResult
import org.gradle.tooling.events.work.WorkItemProgressEvent
import org.gradle.tooling.events.work.WorkItemStartEvent
import org.gradle.tooling.events.work.internal.DefaultWorkItemFailureResult
import org.gradle.tooling.events.work.internal.DefaultWorkItemFinishEvent
import org.gradle.tooling.events.work.internal.DefaultWorkItemOperationDescriptor
import org.gradle.tooling.events.work.internal.DefaultWorkItemStartEvent
import org.gradle.tooling.events.work.internal.DefaultWorkItemSuccessResult
import org.gradle.tooling.internal.consumer.DefaultFailure
import org.gradle.tooling.internal.consumer.DefaultFileComparisonTestAssertionFailure
import org.gradle.tooling.internal.consumer.DefaultTestAssertionFailure
import org.gradle.tooling.internal.consumer.DefaultTestFrameworkFailure
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion3
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion4
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.tooling.internal.protocol.InternalFailure
import org.gradle.tooling.internal.protocol.InternalFileComparisonTestAssertionFailure
import org.gradle.tooling.internal.protocol.InternalProblemAggregationDetailsV2
import org.gradle.tooling.internal.protocol.InternalProblemAggregationDetailsVersion3
import org.gradle.tooling.internal.protocol.InternalProblemContextDetails
import org.gradle.tooling.internal.protocol.InternalProblemContextDetailsV2
import org.gradle.tooling.internal.protocol.InternalProblemDefinition
import org.gradle.tooling.internal.protocol.InternalProblemEvent
import org.gradle.tooling.internal.protocol.InternalProblemEventVersion2
import org.gradle.tooling.internal.protocol.InternalProblemGroup
import org.gradle.tooling.internal.protocol.InternalProblemId
import org.gradle.tooling.internal.protocol.InternalProblemSummariesDetails
import org.gradle.tooling.internal.protocol.InternalProblemSummary
import org.gradle.tooling.internal.protocol.InternalTestAssertionFailure
import org.gradle.tooling.internal.protocol.InternalTestFrameworkFailure
import org.gradle.tooling.internal.protocol.events.InternalBinaryPluginIdentifier
import org.gradle.tooling.internal.protocol.events.InternalBuildPhaseDescriptor
import org.gradle.tooling.internal.protocol.events.InternalFailureResult
import org.gradle.tooling.internal.protocol.events.InternalFileDownloadDescriptor
import org.gradle.tooling.internal.protocol.events.InternalFileDownloadResult
import org.gradle.tooling.internal.protocol.events.InternalFilePosition
import org.gradle.tooling.internal.protocol.events.InternalIncrementalTaskResult
import org.gradle.tooling.internal.protocol.events.InternalJavaCompileTaskOperationResult
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor
import org.gradle.tooling.internal.protocol.events.InternalNotFoundFileDownloadResult
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationResult
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalPluginIdentifier
import org.gradle.tooling.internal.protocol.events.InternalProblemDescriptor
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationDescriptor
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationResult
import org.gradle.tooling.internal.protocol.events.InternalRootOperationDescriptor
import org.gradle.tooling.internal.protocol.events.InternalScriptPluginIdentifier
import org.gradle.tooling.internal.protocol.events.InternalSourceAwareTestDescriptor
import org.gradle.tooling.internal.protocol.events.InternalStatusEvent
import org.gradle.tooling.internal.protocol.events.InternalSuccessResult
import org.gradle.tooling.internal.protocol.events.InternalTaskCachedResult
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor
import org.gradle.tooling.internal.protocol.events.InternalTaskFailureResult
import org.gradle.tooling.internal.protocol.events.InternalTaskResult
import org.gradle.tooling.internal.protocol.events.InternalTaskSkippedResult
import org.gradle.tooling.internal.protocol.events.InternalTaskSuccessResult
import org.gradle.tooling.internal.protocol.events.InternalTaskWithExtraInfoDescriptor
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor
import org.gradle.tooling.internal.protocol.events.InternalTestFailureResult
import org.gradle.tooling.internal.protocol.events.InternalTestFinishedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalTestMetadataDescriptor
import org.gradle.tooling.internal.protocol.events.InternalTestMetadataEvent
import org.gradle.tooling.internal.protocol.events.InternalTestMetadataEventVersion2
import org.gradle.tooling.internal.protocol.events.InternalTestOutputDescriptor
import org.gradle.tooling.internal.protocol.events.InternalTestOutputEvent
import org.gradle.tooling.internal.protocol.events.InternalTestProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalTestResult
import org.gradle.tooling.internal.protocol.events.InternalTestSkippedResult
import org.gradle.tooling.internal.protocol.events.InternalTestStartedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalTestSuccessResult
import org.gradle.tooling.internal.protocol.events.InternalTransformDescriptor
import org.gradle.tooling.internal.protocol.events.InternalWorkItemDescriptor
import org.gradle.tooling.internal.protocol.problem.InternalAdditionalData
import org.gradle.tooling.internal.protocol.problem.InternalBasicProblemDetails
import org.gradle.tooling.internal.protocol.problem.InternalBasicProblemDetailsVersion2
import org.gradle.tooling.internal.protocol.problem.InternalContextualLabel
import org.gradle.tooling.internal.protocol.problem.InternalDetails
import org.gradle.tooling.internal.protocol.problem.InternalDocumentationLink
import org.gradle.tooling.internal.protocol.problem.InternalFileLocation
import org.gradle.tooling.internal.protocol.problem.InternalLabel
import org.gradle.tooling.internal.protocol.problem.InternalLineInFileLocation
import org.gradle.tooling.internal.protocol.problem.InternalLocation
import org.gradle.tooling.internal.protocol.problem.InternalOffsetInFileLocation
import org.gradle.tooling.internal.protocol.problem.InternalPluginIdLocation
import org.gradle.tooling.internal.protocol.problem.InternalProblemCategory
import org.gradle.tooling.internal.protocol.problem.InternalProxiedAdditionalData
import org.gradle.tooling.internal.protocol.problem.InternalSeverity
import org.gradle.tooling.internal.protocol.problem.InternalSolution
import org.gradle.tooling.internal.protocol.problem.InternalTaskPathLocation
import org.gradle.tooling.internal.protocol.test.source.InternalClassSource
import org.gradle.tooling.internal.protocol.test.source.InternalClasspathResourceSource
import org.gradle.tooling.internal.protocol.test.source.InternalDirectorySource
import org.gradle.tooling.internal.protocol.test.source.InternalFileSource
import org.gradle.tooling.internal.protocol.test.source.InternalMethodSource
import org.gradle.tooling.internal.protocol.test.source.InternalMissingSource
import org.gradle.tooling.internal.protocol.test.source.InternalTestSource

/**
 * Converts progress events sent from the tooling provider to the tooling client to the corresponding event types available on the public Tooling API, and broadcasts the converted events to the
 * matching progress listeners. This adapter handles all the different incoming progress event types (except the original logging-derived progress listener).
 *
 * This adapts tooling provider internal types into the public types on the consumer.
 */
class BuildProgressListenerAdapter(listeners: MutableMap<OperationType, MutableList<ProgressListener>>) : InternalBuildProgressListener {
    private val testProgressListeners = ListenerBroadcast<ProgressListener?>(ProgressListener::class.java as Class<ProgressListener?>)
    private val taskProgressListeners = ListenerBroadcast<ProgressListener?>(ProgressListener::class.java as Class<ProgressListener?>)
    private val buildOperationProgressListeners = ListenerBroadcast<ProgressListener?>(ProgressListener::class.java as Class<ProgressListener?>)
    private val workItemProgressListeners = ListenerBroadcast<ProgressListener?>(ProgressListener::class.java as Class<ProgressListener?>)
    private val projectConfigurationProgressListeners = ListenerBroadcast<ProgressListener?>(ProgressListener::class.java as Class<ProgressListener?>)
    private val transformProgressListeners = ListenerBroadcast<ProgressListener?>(ProgressListener::class.java as Class<ProgressListener?>)
    private val testOutputProgressListeners = ListenerBroadcast<ProgressListener?>(ProgressListener::class.java as Class<ProgressListener?>)
    private val testMetadataProgressListeners = ListenerBroadcast<ProgressListener?>(ProgressListener::class.java as Class<ProgressListener?>)
    private val fileDownloadListeners = ListenerBroadcast<ProgressListener?>(ProgressListener::class.java as Class<ProgressListener?>)
    private val buildPhaseListeners = ListenerBroadcast<ProgressListener?>(ProgressListener::class.java as Class<ProgressListener?>)
    private val problemListeners = ListenerBroadcast<ProgressListener?>(ProgressListener::class.java as Class<ProgressListener?>)
    private val rootBuildListeners = ListenerBroadcast<ProgressListener?>(ProgressListener::class.java as Class<ProgressListener?>)

    private val descriptorCache: MutableMap<Any, OperationDescriptor> = HashMap<Any, OperationDescriptor>()

    init {
        testProgressListeners.addAll(getOrDefault(listeners, OperationType.TEST))
        taskProgressListeners.addAll(getOrDefault(listeners, OperationType.TASK))
        buildOperationProgressListeners.addAll(getOrDefault(listeners, OperationType.GENERIC))
        workItemProgressListeners.addAll(getOrDefault(listeners, OperationType.WORK_ITEM))
        projectConfigurationProgressListeners.addAll(getOrDefault(listeners, OperationType.PROJECT_CONFIGURATION))
        transformProgressListeners.addAll(getOrDefault(listeners, OperationType.TRANSFORM))
        testOutputProgressListeners.addAll(getOrDefault(listeners, OperationType.TEST_OUTPUT))
        testMetadataProgressListeners.addAll(getOrDefault(listeners, OperationType.TEST_METADATA))
        fileDownloadListeners.addAll(getOrDefault(listeners, OperationType.FILE_DOWNLOAD))
        buildPhaseListeners.addAll(getOrDefault(listeners, OperationType.BUILD_PHASE))
        problemListeners.addAll(getOrDefault(listeners, OperationType.PROBLEMS))
        rootBuildListeners.addAll(getOrDefault(listeners, OperationType.ROOT))
    }

    override val subscribedOperations: MutableList<String?>?
        get() {
        val operations: MutableList<String?> = ArrayList<String?>()

        if (!testProgressListeners.isEmpty) {
            operations.add(InternalBuildProgressListener.TEST_EXECUTION)
        }
        if (!taskProgressListeners.isEmpty) {
            operations.add(InternalBuildProgressListener.TASK_EXECUTION)
        }
        if (!buildOperationProgressListeners.isEmpty) {
            operations.add(InternalBuildProgressListener.BUILD_EXECUTION)
        }
        if (!workItemProgressListeners.isEmpty) {
            operations.add(InternalBuildProgressListener.WORK_ITEM_EXECUTION)
        }
        if (!projectConfigurationProgressListeners.isEmpty) {
            operations.add(InternalBuildProgressListener.PROJECT_CONFIGURATION_EXECUTION)
        }
        if (!transformProgressListeners.isEmpty) {
            operations.add(InternalBuildProgressListener.TRANSFORM_EXECUTION)
        }
        if (!testOutputProgressListeners.isEmpty) {
            operations.add(InternalBuildProgressListener.TEST_OUTPUT)
        }
        if (!testMetadataProgressListeners.isEmpty) {
            operations.add(InternalBuildProgressListener.TEST_METADATA)
        }
        if (!fileDownloadListeners.isEmpty) {
            operations.add(InternalBuildProgressListener.FILE_DOWNLOAD)
        }
        if (!buildPhaseListeners.isEmpty) {
            operations.add(InternalBuildProgressListener.BUILD_PHASE)
        }
        if (!problemListeners.isEmpty) {
            operations.add(InternalBuildProgressListener.PROBLEMS)
        }
        if (!rootBuildListeners.isEmpty) {
            operations.add(InternalBuildProgressListener.ROOT)
        }
        return operations
    }

    override fun onEvent(event: Any?) {
        if (event is ProgressEvent) {
            broadcastProgressEvent(event)
        } else if (event is InternalTestProgressEvent) {
            // Special case for events defined prior to InternalProgressEvent
            broadcastTestProgressEvent(event)
        } else if (event is InternalProgressEvent) {
            broadcastInternalProgressEvent(event)
        } else {
            throw IllegalArgumentException("Unexpected event type: " + event)
        }
    }

    private fun broadcastProgressEvent(event: ProgressEvent) {
        if (event is TestProgressEvent) {
            testProgressListeners.getSource()!!.statusChanged(event)
        } else if (event is TaskProgressEvent) {
            taskProgressListeners.getSource()!!.statusChanged(event)
        } else if (event is WorkItemProgressEvent) {
            workItemProgressListeners.getSource()!!.statusChanged(event)
        } else if (event is ProjectConfigurationProgressEvent) {
            projectConfigurationProgressListeners.getSource()!!.statusChanged(event)
        } else if (event is TransformProgressEvent) {
            transformProgressListeners.getSource()!!.statusChanged(event)
        } else if (event is TestOutputEvent) {
            testOutputProgressListeners.getSource()!!.statusChanged(event)
        } else if (event is TestMetadataEvent) {
            testMetadataProgressListeners.getSource()!!.statusChanged(event)
        } else if (event is BuildPhaseProgressEvent) {
            buildPhaseListeners.getSource()!!.statusChanged(event)
        } else if (event is ProblemEvent) {
            problemListeners.getSource()!!.statusChanged(event)
        } else if (event is FileDownloadProgressEvent || event is DefaultStatusEvent) {
            fileDownloadListeners.getSource()!!.statusChanged(event)
        } else {
            // Everything else treat as a generic operation
            buildOperationProgressListeners.getSource()!!.statusChanged(event)
        }
    }

    private fun broadcastTestProgressEvent(event: InternalTestProgressEvent) {
        val testProgressEvent = toTestProgressEvent(event)
        if (testProgressEvent != null) {
            testProgressListeners.getSource()!!.statusChanged(testProgressEvent)
        }
    }

    private fun broadcastInternalProgressEvent(progressEvent: InternalProgressEvent) {
        val descriptor = progressEvent.descriptor
        if (descriptor is InternalTaskDescriptor) {
            broadcastTaskProgressEvent(progressEvent, descriptor)
        } else if (descriptor is InternalWorkItemDescriptor) {
            broadcastWorkItemProgressEvent(progressEvent, descriptor)
        } else if (descriptor is InternalProjectConfigurationDescriptor) {
            broadcastProjectConfigurationProgressEvent(progressEvent, descriptor)
        } else if (descriptor is InternalTransformDescriptor) {
            broadcastTransformProgressEvent(progressEvent, descriptor)
        } else if (descriptor is InternalTestOutputDescriptor) {
            broadcastTestOutputEvent(progressEvent, descriptor)
        } else if (descriptor is InternalTestMetadataDescriptor) {
            broadcastTestMetadataEvent(progressEvent, descriptor)
        } else if (descriptor is InternalFileDownloadDescriptor) {
            if (progressEvent is InternalStatusEvent) {
                broadcastStatusEvent(progressEvent)
            } else {
                broadcastFileDownloadEvent(progressEvent, descriptor)
            }
        } else if (descriptor is InternalBuildPhaseDescriptor) {
            broadcastBuildPhaseEvent(progressEvent, descriptor)
        } else if (descriptor is InternalProblemDescriptor) {
            broadcastProblemEvent(progressEvent, descriptor)
        } else if (descriptor is InternalRootOperationDescriptor) {
            broadcastRootBuildEvent(progressEvent)
        } else {
            broadcastGenericProgressEvent(progressEvent)
        }
    }

    /*
     * This represents a file download update event
     */
    private fun broadcastStatusEvent(progressEvent: InternalStatusEvent) {
        val internalDescriptor = progressEvent.descriptor!!
        val descriptor: OperationDescriptor = descriptorCache.get(internalDescriptor.id)!!
        checkNotNull(descriptor) { String.format("No operation with id %s in progress.", internalDescriptor.id) }
        fileDownloadListeners.getSource()!!.statusChanged(
            DefaultStatusEvent(
                progressEvent.eventTime,
                descriptor,
                progressEvent.total,
                progressEvent.progress,
                progressEvent.units
            )
        )
    }

    private fun broadcastTaskProgressEvent(event: InternalProgressEvent, descriptor: InternalTaskDescriptor) {
        val taskProgressEvent = toTaskProgressEvent(event, descriptor)
        if (taskProgressEvent != null) {
            taskProgressListeners.getSource()!!.statusChanged(taskProgressEvent)
        }
    }

    private fun broadcastWorkItemProgressEvent(event: InternalProgressEvent, descriptor: InternalWorkItemDescriptor) {
        val workItemProgressEvent = toWorkItemProgressEvent(event, descriptor)
        if (workItemProgressEvent != null) {
            workItemProgressListeners.getSource()!!.statusChanged(workItemProgressEvent)
        }
    }

    private fun broadcastProjectConfigurationProgressEvent(event: InternalProgressEvent, descriptor: InternalProjectConfigurationDescriptor) {
        val projectConfigurationProgressEvent = toProjectConfigurationProgressEvent(event, descriptor)
        if (projectConfigurationProgressEvent != null) {
            projectConfigurationProgressListeners.getSource()!!.statusChanged(projectConfigurationProgressEvent)
        }
    }

    private fun broadcastTransformProgressEvent(event: InternalProgressEvent, descriptor: InternalTransformDescriptor) {
        val transformProgressEvent = toTransformProgressEvent(event, descriptor)
        if (transformProgressEvent != null) {
            transformProgressListeners.getSource()!!.statusChanged(transformProgressEvent)
        }
    }

    private fun broadcastTestOutputEvent(event: InternalProgressEvent, descriptor: InternalTestOutputDescriptor) {
        val outputEvent = toTestOutputEvent(event, descriptor)
        if (outputEvent != null) {
            testOutputProgressListeners.getSource()!!.statusChanged(outputEvent)
        }
    }

    private fun broadcastTestMetadataEvent(event: InternalProgressEvent, descriptor: InternalTestMetadataDescriptor) {
        val metadataEvent = toTestMetadataEvent(event, descriptor)
        if (metadataEvent != null) {
            testMetadataProgressListeners.getSource()!!.statusChanged(metadataEvent)
        }
    }

    private fun broadcastProblemEvent(progressEvent: InternalProgressEvent, descriptor: InternalProblemDescriptor) {
        val problemEvent = toProblemEvent(progressEvent, descriptor)
        if (problemEvent != null) {
            problemListeners.getSource()!!.statusChanged(problemEvent)
        }
    }

    private fun broadcastRootBuildEvent(event: InternalProgressEvent) {
        val progressEvent = toGenericProgressEvent(event)
        if (progressEvent != null) {
            rootBuildListeners.getSource()!!.statusChanged(progressEvent)
        }
    }

    /*
     * Does not handle file download update events, see #broadcastStatusEvent for those
     */
    private fun broadcastFileDownloadEvent(event: InternalProgressEvent, descriptor: InternalFileDownloadDescriptor) {
        val progressEvent: ProgressEvent? = toFileDownloadProgressEvent(event, descriptor)
        if (progressEvent != null) {
            fileDownloadListeners.getSource()!!.statusChanged(progressEvent)
        }
    }

    private fun broadcastBuildPhaseEvent(event: InternalProgressEvent, descriptor: InternalBuildPhaseDescriptor) {
        val progressEvent: ProgressEvent? = toBuildPhaseEvent(event, descriptor)
        if (progressEvent != null) {
            buildPhaseListeners.getSource()!!.statusChanged(progressEvent)
        }
    }

    private fun toBuildPhaseEvent(event: InternalProgressEvent, descriptor: InternalBuildPhaseDescriptor): BuildPhaseProgressEvent? {
        if (event is InternalOperationStartedProgressEvent) {
            return buildPhaseStartEvent(event, descriptor)
        } else if (event is InternalOperationFinishedProgressEvent) {
            return buildPhaseFinishEvent(event)
        } else {
            return null
        }
    }

    private fun buildPhaseStartEvent(event: InternalOperationStartedProgressEvent, descriptor: InternalBuildPhaseDescriptor): BuildPhaseStartEvent {
        val parent = getParentDescriptor(descriptor.parentId)
        val newDescriptor: BuildPhaseOperationDescriptor = addDescriptor<DefaultBuildPhaseOperationDescriptor>(
            event.descriptor,
            org.gradle.tooling.events.lifecycle.internal.DefaultBuildPhaseOperationDescriptor(descriptor, parent)
        )!!
        return DefaultBuildPhaseStartEvent(event.eventTime, event.displayName, newDescriptor)
    }

    private fun buildPhaseFinishEvent(event: InternalOperationFinishedProgressEvent): BuildPhaseFinishEvent {
        val descriptor = removeDescriptor<BuildPhaseOperationDescriptor>(BuildPhaseOperationDescriptor::class.java, event.descriptor)!!
        val result: OperationResult?
        if (event.result is InternalFailureResult) {
            val internalResult = event.result as InternalFailureResult
            result = DefaultOperationFailureResult(internalResult.startTime, internalResult.endTime, toFailures(internalResult.failures ?: mutableListOf()))
        } else {
            result = DefaultOperationSuccessResult(event.result!!.startTime, event.result!!.endTime)
        }
        return DefaultBuildPhaseFinishEvent(event.eventTime, event.displayName, descriptor, result)
    }

    private fun broadcastGenericProgressEvent(event: InternalProgressEvent) {
        val progressEvent = toGenericProgressEvent(event)
        if (progressEvent != null) {
            buildOperationProgressListeners.getSource()!!.statusChanged(progressEvent)
        }
    }

    private fun toTestProgressEvent(event: InternalTestProgressEvent): TestProgressEvent? {
        if (event is InternalTestStartedProgressEvent) {
            return testStartedEvent(event)
        } else if (event is InternalTestFinishedProgressEvent) {
            return testFinishedEvent(event)
        } else {
            return null
        }
    }

    private fun toTaskProgressEvent(event: InternalProgressEvent, descriptor: InternalTaskDescriptor): TaskProgressEvent? {
        if (event is InternalOperationStartedProgressEvent) {
            return taskStartedEvent(event, descriptor)
        } else if (event is InternalOperationFinishedProgressEvent) {
            return taskFinishedEvent(event)
        } else {
            return null
        }
    }

    private fun toWorkItemProgressEvent(event: InternalProgressEvent, descriptor: InternalWorkItemDescriptor): WorkItemProgressEvent? {
        if (event is InternalOperationStartedProgressEvent) {
            return workItemStartedEvent(event, descriptor)
        } else if (event is InternalOperationFinishedProgressEvent) {
            return workItemFinishedEvent(event)
        } else {
            return null
        }
    }

    private fun toProjectConfigurationProgressEvent(event: InternalProgressEvent, descriptor: InternalProjectConfigurationDescriptor): ProjectConfigurationProgressEvent? {
        if (event is InternalOperationStartedProgressEvent) {
            return projectConfigurationStartedEvent(event, descriptor)
        } else if (event is InternalOperationFinishedProgressEvent) {
            return projectConfigurationFinishedEvent(event)
        } else {
            return null
        }
    }

    private fun toTransformProgressEvent(event: InternalProgressEvent, descriptor: InternalTransformDescriptor): TransformProgressEvent? {
        if (event is InternalOperationStartedProgressEvent) {
            return transformStartedEvent(event, descriptor)
        } else if (event is InternalOperationFinishedProgressEvent) {
            return transformFinishedEvent(event)
        } else {
            return null
        }
    }

    private fun toFileDownloadProgressEvent(event: InternalProgressEvent, descriptor: InternalFileDownloadDescriptor): FileDownloadProgressEvent? {
        if (event is InternalOperationStartedProgressEvent) {
            return fileDownloadStartEvent(event, descriptor)
        } else if (event is InternalOperationFinishedProgressEvent) {
            return fileDownloadFinishedEvent(event)
        } else {
            return null
        }
    }

    private fun toTestOutputEvent(event: InternalProgressEvent, descriptor: InternalTestOutputDescriptor): TestOutputEvent? {
        if (event is InternalTestOutputEvent) {
            return transformTestOutput(event, descriptor)
        } else {
            return null
        }
    }

    private fun transformTestOutput(event: InternalTestOutputEvent, descriptor: InternalTestOutputDescriptor): TestOutputEvent {
        val clientDescriptor = addDescriptor<TestOutputDescriptor>(event.descriptor, toTestOutputDescriptor(event, descriptor))!!
        return DefaultTestOutputEvent(event.eventTime, clientDescriptor)
    }

    private fun toTestMetadataEvent(event: InternalProgressEvent, descriptor: InternalTestMetadataDescriptor): TestMetadataEvent? {
        if (event is InternalTestMetadataEventVersion2) {
            val clientDescriptor = addDescriptor<OperationDescriptor>(event.descriptor, toDescriptor(descriptor))!!
            return DefaultTestFileAttachmentMetadataEvent(event.eventTime, clientDescriptor, event.file!!, event.mediaType)
        } else if (event is InternalTestMetadataEvent) {
            val clientDescriptor = addDescriptor<OperationDescriptor>(event.descriptor, toDescriptor(descriptor))!!
            val values = event.values
            val keyValues: MutableMap<String, String> = LinkedHashMap<String, String>()
            values!!.forEach { (key: String?, value: Any?) -> keyValues.put(key!!, value.toString()) }
            return DefaultTestKeyValueMetadataEvent(event.eventTime, clientDescriptor, keyValues)
        } else {
            return null
        }
    }

    private fun toProblemEvent(progressEvent: InternalProgressEvent, descriptor: InternalProblemDescriptor): ProblemEvent? {
        if (progressEvent is InternalProblemEvent) {
            val problemEvent = progressEvent
            return createProblemEvent(problemEvent, descriptor)
        } else if (progressEvent is InternalProblemEventVersion2) {
            val problemEvent = progressEvent
            return createProblemEvent(problemEvent, descriptor)
        }
        return null
    }

    private fun createProblemEvent(problemEvent: InternalProblemEvent, descriptor: InternalProblemDescriptor): ProblemEvent? {
        val details = problemEvent.details
        val parentDescriptor = getParentDescriptor(descriptor.parentId)

        if (details is InternalBasicProblemDetails) {
            val basicProblemDetails = details
            return DefaultSingleProblemEvent(
                problemEvent.eventTime,
                parentDescriptor,
                toProblem(basicProblemDetails)
            )
        } else if (details is InternalProblemAggregationDetailsV2) {
            val problemAggregationDetails = details
            return DefaultProblemAggregationEvent(
                problemEvent.eventTime,
                parentDescriptor,
                DefaultProblemAggregation(
                    toProblemDefinition(
                        problemAggregationDetails.label!!,
                        problemAggregationDetails.category!!,
                        problemAggregationDetails.severity!!,
                        problemAggregationDetails.documentationLink
                    ),
                    toProblemContextDetails(problemAggregationDetails.problems ?: mutableListOf())
                )
            )
        }
        return null
    }

    private fun createProblemEvent(problemEvent: InternalProblemEventVersion2, descriptor: InternalProblemDescriptor): ProblemEvent? {
        val details = problemEvent.details
        val parentDescriptor = getParentDescriptor(descriptor.parentId)

        if (details is InternalBasicProblemDetailsVersion3) {
            val basicProblemDetails = details
            return DefaultSingleProblemEvent(
                problemEvent.eventTime,
                parentDescriptor,
                toProblem(basicProblemDetails)
            )
        } else if (details is InternalProblemAggregationDetailsVersion3) {
            val problemAggregationDetails = details
            return DefaultProblemAggregationEvent(
                problemEvent.eventTime,
                parentDescriptor,
                DefaultProblemAggregation(
                    toProblemDefinition(problemAggregationDetails.definition!!),
                    toProblemContextDetails(problemAggregationDetails.problems ?: mutableListOf())
                )
            )
        } else if (details is InternalProblemSummariesDetails) {
            val problemSummariesDetails = details
            return DefaultProblemSummariesEvent(
                problemEvent.eventTime, parentDescriptor!!,
                toProblemIdSummaries(problemSummariesDetails.problemIdCounts ?: mutableListOf())
            )
        }
        return null
    }

    private fun toGenericProgressEvent(event: InternalProgressEvent): ProgressEvent? {
        if (event is InternalOperationStartedProgressEvent) {
            return genericStartedEvent(event)
        } else if (event is InternalOperationFinishedProgressEvent) {
            return genericFinishedEvent(event)
        } else {
            return null
        }
    }

    private fun testStartedEvent(event: InternalTestStartedProgressEvent): TestStartEvent {
        val clientDescriptor = addDescriptor<TestOperationDescriptor>(event.descriptor, toTestDescriptor(event.descriptor!!))!!
        return DefaultTestStartEvent(event.eventTime, event.displayName, clientDescriptor)
    }

    private fun taskStartedEvent(event: InternalOperationStartedProgressEvent, descriptor: InternalTaskDescriptor): TaskStartEvent {
        val clientDescriptor = addDescriptor<TaskOperationDescriptor>(event.descriptor, toTaskDescriptor(descriptor))!!
        return DefaultTaskStartEvent(event.eventTime, event.displayName, clientDescriptor)
    }

    private fun workItemStartedEvent(event: InternalOperationStartedProgressEvent, descriptor: InternalWorkItemDescriptor): WorkItemStartEvent {
        val clientDescriptor = addDescriptor<WorkItemOperationDescriptor>(event.descriptor, toWorkItemDescriptor(descriptor))!!
        return DefaultWorkItemStartEvent(event.eventTime, event.displayName, clientDescriptor)
    }

    private fun projectConfigurationStartedEvent(event: InternalOperationStartedProgressEvent, descriptor: InternalProjectConfigurationDescriptor): ProjectConfigurationStartEvent {
        val clientDescriptor = addDescriptor<ProjectConfigurationOperationDescriptor>(event.descriptor, toProjectConfigurationDescriptor(descriptor))!!
        return DefaultProjectConfigurationStartEvent(event.eventTime, event.displayName, clientDescriptor)
    }

    private fun transformStartedEvent(event: InternalOperationStartedProgressEvent, descriptor: InternalTransformDescriptor): TransformStartEvent {
        val clientDescriptor = addDescriptor<TransformOperationDescriptor>(event.descriptor, toTransformDescriptor(descriptor))!!
        return DefaultTransformStartEvent(event.eventTime, event.displayName, clientDescriptor)
    }

    private fun fileDownloadStartEvent(event: InternalOperationStartedProgressEvent, descriptor: InternalFileDownloadDescriptor): FileDownloadStartEvent {
        val clientDescriptor = addDescriptor<FileDownloadOperationDescriptor>(event.descriptor, toFileDownloadDescriptor(descriptor))!!
        return DefaultFileDownloadStartEvent(event.eventTime, event.displayName, clientDescriptor)
    }

    private fun genericStartedEvent(event: InternalOperationStartedProgressEvent): StartEvent {
        val clientDescriptor = addDescriptor<OperationDescriptor>(event.descriptor, toDescriptor(event.descriptor!!))!!
        return DefaultStartEvent(event.eventTime, event.displayName, clientDescriptor)
    }

    private fun testFinishedEvent(event: InternalTestFinishedProgressEvent): TestFinishEvent {
        val clientDescriptor = removeDescriptor<TestOperationDescriptor>(TestOperationDescriptor::class.java, event.descriptor)!!
        return DefaultTestFinishEvent(event.eventTime, event.displayName, clientDescriptor, toTestResult(event.result!!))
    }

    private fun taskFinishedEvent(event: InternalOperationFinishedProgressEvent): TaskFinishEvent {
        // do not remove task descriptors because they might be needed to describe subsequent tasks' dependencies
        val descriptor: TaskOperationDescriptor = Companion.assertDescriptorType<TaskOperationDescriptor>(
            org.gradle.tooling.events.task.TaskOperationDescriptor::class.java,
            getParentDescriptor(event.descriptor!!.id)
        )!!
        return DefaultTaskFinishEvent(
            event.eventTime,
            event.displayName,
            descriptor,
            Companion.toTaskResult((event.result as org.gradle.tooling.internal.protocol.events.InternalTaskResult?)!!)
        )
    }

    private fun workItemFinishedEvent(event: InternalOperationFinishedProgressEvent): WorkItemFinishEvent {
        val descriptor = removeDescriptor<WorkItemOperationDescriptor>(WorkItemOperationDescriptor::class.java, event.descriptor)!!
        return DefaultWorkItemFinishEvent(event.eventTime, event.displayName, descriptor, toWorkItemResult(event.result!!))
    }

    private fun projectConfigurationFinishedEvent(event: InternalOperationFinishedProgressEvent): ProjectConfigurationFinishEvent {
        val descriptor = removeDescriptor<ProjectConfigurationOperationDescriptor>(
            org.gradle.tooling.events.configuration.ProjectConfigurationOperationDescriptor::class.java,
            event.descriptor
        )!!
        return DefaultProjectConfigurationFinishEvent(
            event.eventTime,
            event.displayName,
            descriptor,
            Companion.toProjectConfigurationResult((event.result as org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationResult?)!!)
        )
    }

    private fun transformFinishedEvent(event: InternalOperationFinishedProgressEvent): TransformFinishEvent {
        // do not remove task descriptors because they might be needed to describe subsequent tasks' dependencies
        val descriptor: TransformOperationDescriptor = Companion.assertDescriptorType<TransformOperationDescriptor>(
            org.gradle.tooling.events.transform.TransformOperationDescriptor::class.java,
            getParentDescriptor(event.descriptor!!.id)
        )!!
        return DefaultTransformFinishEvent(event.eventTime, event.displayName, descriptor, toTransformResult(event.result!!))
    }

    private fun fileDownloadFinishedEvent(event: InternalOperationFinishedProgressEvent): FileDownloadFinishEvent {
        val descriptor = removeDescriptor<FileDownloadOperationDescriptor>(FileDownloadOperationDescriptor::class.java, event.descriptor)!!
        return DefaultFileDownloadFinishEvent(event.eventTime, event.displayName, descriptor, toFileDownloadResult(event.result!!))
    }

    private fun genericFinishedEvent(event: InternalOperationFinishedProgressEvent): FinishEvent {
        val descriptor: OperationDescriptor? = removeDescriptor<OperationDescriptor>(OperationDescriptor::class.java, event.descriptor)
        return DefaultFinishEvent<OperationDescriptor?, OperationResult?>(event.eventTime, event.displayName, descriptor, toResult(event.result!!))
    }

    @Synchronized
    private fun <T : OperationDescriptor?> addDescriptor(descriptor: InternalOperationDescriptor?, clientDescriptor: T?): T? {
        val operationDescriptor = descriptor!!
        val id = operationDescriptor.id!!
        check(!this.descriptorCache.containsKey(id)) { String.format("Operation %s already available.", operationDescriptor) }
        descriptorCache.put(id, clientDescriptor!!)
        return clientDescriptor
    }

    @Synchronized
    private fun <T : OperationDescriptor?> removeDescriptor(type: Class<out OperationDescriptor>, descriptor: InternalOperationDescriptor?): T? {
        val operationDescriptor = descriptor!!
        val cachedTestDescriptor: OperationDescriptor = this.descriptorCache.remove(operationDescriptor.id!!)!!
        checkNotNull(cachedTestDescriptor) { String.format("Operation %s is not available.", operationDescriptor) }
        return assertDescriptorType<T?>(type, cachedTestDescriptor)
    }

    private fun toTestDescriptor(descriptor: InternalTestDescriptor): TestOperationDescriptor {
        val parent = getParentDescriptor(descriptor.parentId)
        if (descriptor is InternalSourceAwareTestDescriptor) {
            val testSource: TestSource = toTestSource(descriptor)
            val jvmTestDescriptor = descriptor
            return DefaultJvmTestOperationDescriptor(
                jvmTestDescriptor,
                parent,
                toJvmTestKind(jvmTestDescriptor.testKind),
                jvmTestDescriptor.suiteName,
                jvmTestDescriptor.className,
                jvmTestDescriptor.methodName,
                testSource
            )
        } else if (descriptor is InternalJvmTestDescriptor) {
            val jvmTestDescriptor = descriptor
            val testSource: TestSource = inferLegacyTestSource(jvmTestDescriptor)
            return DefaultJvmTestOperationDescriptor(
                jvmTestDescriptor,
                parent,
                toJvmTestKind(jvmTestDescriptor.testKind),
                jvmTestDescriptor.suiteName,
                jvmTestDescriptor.className,
                jvmTestDescriptor.methodName,
                testSource
            )
        } else {
            return DefaultTestOperationDescriptor(descriptor, parent)
        }
    }

    private fun toTaskDescriptor(descriptor: InternalTaskDescriptor): TaskOperationDescriptor {
        val parent = getParentDescriptor(descriptor.parentId)
        if (descriptor is InternalTaskWithExtraInfoDescriptor) {
            val descriptorWithExtras = descriptor
            val dependencies = collectDescriptors(descriptorWithExtras.dependencies)
            val originPlugin: PluginIdentifier? = toPluginIdentifier(descriptorWithExtras.originPlugin)
            return DefaultTaskOperationDescriptor(descriptor, parent, descriptor.taskPath, dependencies, originPlugin)
        }
        return DefaultTaskOperationDescriptor(descriptor, parent, descriptor.taskPath)
    }

    private fun toWorkItemDescriptor(descriptor: InternalWorkItemDescriptor): WorkItemOperationDescriptor {
        val parent = getParentDescriptor(descriptor.parentId)
        return DefaultWorkItemOperationDescriptor(descriptor, parent)
    }

    private fun toProjectConfigurationDescriptor(descriptor: InternalProjectConfigurationDescriptor): ProjectConfigurationOperationDescriptor {
        val parent = getParentDescriptor(descriptor.parentId)
        return DefaultProjectConfigurationOperationDescriptor(descriptor, parent)
    }

    private fun toTransformDescriptor(descriptor: InternalTransformDescriptor): TransformOperationDescriptor {
        val parent = getParentDescriptor(descriptor.parentId)
        return DefaultTransformOperationDescriptor(descriptor, parent, collectDescriptors(descriptor.dependencies))
    }

    private fun toFileDownloadDescriptor(descriptor: InternalFileDownloadDescriptor): FileDownloadOperationDescriptor {
        val parent = getParentDescriptor(descriptor.parentId)
        return DefaultFileDownloadOperationDescriptor(descriptor, parent)
    }

    private fun toTestOutputDescriptor(event: InternalTestOutputEvent, descriptor: InternalTestOutputDescriptor): TestOutputDescriptor {
        val parent = getParentDescriptor(descriptor.parentId)
        val outputResult = event.result!!
        val destination = fromCode(outputResult.destination)
        val message = outputResult.message
        return DefaultTestOutputOperationDescriptor(descriptor, parent, destination, message)
    }

    private fun collectDescriptors(dependencies: MutableSet<out InternalOperationDescriptor?>?): MutableSet<OperationDescriptor?> {
        val result: MutableSet<OperationDescriptor?> = LinkedHashSet<OperationDescriptor?>()
        for (dependency in dependencies.orEmpty()) {
            val dependencyDescriptor = descriptorCache.get(dependency!!.id!!)
            if (dependencyDescriptor != null) {
                result.add(dependencyDescriptor)
            }
        }
        return result
    }

    private fun toDescriptor(descriptor: InternalOperationDescriptor?): OperationDescriptor {
        val internalDescriptor = descriptor!!
        val parent = getParentDescriptor(internalDescriptor.parentId)
        return DefaultOperationDescriptor(internalDescriptor, parent)
    }

    @Synchronized
    private fun getParentDescriptor(parentId: Any?): OperationDescriptor? {
        if (parentId == null) {
            return null
        } else {
            val operationDescriptor: OperationDescriptor = descriptorCache.get(parentId)!!
            checkNotNull(operationDescriptor) { String.format("Parent operation with id %s not available.", parentId) }
            return operationDescriptor
        }
    }

    companion object {
        private fun getOrDefault(listeners: MutableMap<OperationType, MutableList<ProgressListener>>, operationType: OperationType): MutableList<ProgressListener> {
            val progressListeners = listeners.get(operationType)
            if (progressListeners == null) {
                return mutableListOf<ProgressListener>()
            }
            return progressListeners
        }

        fun toProblemIdSummaries(problemIdCounts: MutableList<InternalProblemSummary>): MutableList<ProblemSummary?> {
            val groupedSummaries: MutableMap<ProblemId, MutableList<InternalProblemSummary>> = getGroupedMap(problemIdCounts)

            val problemSummaries: MutableList<ProblemSummary?> = ArrayList<ProblemSummary?>()
            for (groupEntry in groupedSummaries.entries) {
                problemSummaries.add(DefaultProblemSummary(groupEntry.key, getCount(groupEntry)))
            }
            return problemSummaries
        }

        fun getGroupedMap(problemIdCounts: MutableList<InternalProblemSummary>): MutableMap<ProblemId, MutableList<InternalProblemSummary>> {
            val groupedSummaries: MutableMap<ProblemId, MutableList<InternalProblemSummary>> = HashMap<ProblemId, MutableList<InternalProblemSummary>>()
            for (internalSummary in problemIdCounts) {
                val problemId: ProblemId = toProblemId(internalSummary.problemId)
                getOrDefault(groupedSummaries, problemId).add(internalSummary)
            }
            return groupedSummaries
        }

        private fun getOrDefault(groupedSummaries: MutableMap<ProblemId, MutableList<InternalProblemSummary>>, problemId: ProblemId): MutableList<InternalProblemSummary> {
            var internalProblemSummaries = groupedSummaries.get(problemId)
            if (internalProblemSummaries == null) {
                internalProblemSummaries = ArrayList<InternalProblemSummary>()
                groupedSummaries.put(problemId, internalProblemSummaries)
            }
            return internalProblemSummaries
        }

        fun getCount(groupEntry: MutableMap.MutableEntry<ProblemId, MutableList<InternalProblemSummary>>): Int {
            var count = 0
            for (internalProblemSummary in groupEntry.value) {
                count += internalProblemSummary.count ?: 0
            }
            return count
        }

        private fun toSingleProblemContextDetail(details: InternalProblemContextDetails): DefaultProblemsOperationContext {
            if (details is InternalProblemContextDetailsV2) {
                val detailsV2 = details
                return DefaultProblemsOperationContext(
                    toProblemDetails(detailsV2.details),
                    toLocations(detailsV2.originLocations),
                    toLocations(detailsV2.contextualLocations),
                    toSolutions(detailsV2.solutions),
                    toAdditionalData(detailsV2.additionalData),
                    Companion.toFailure(detailsV2.failure!!)
                )
            } else {
                return DefaultProblemsOperationContext(
                    toProblemDetails(details.details),
                    toLocations(details.locations),
                    ImmutableList.of<Location>(),
                    toSolutions(details.solutions),
                    toAdditionalData(details.additionalData),
                    Companion.toFailure(details.failure!!)
                )
            }
        }

        private fun toProblemContextDetails(problems: MutableList<InternalProblemContextDetails>?): MutableList<ProblemContext> {
            if (problems == null) {
                return mutableListOf()
            }
            val result = ImmutableList.builderWithExpectedSize<ProblemContext>(problems.size)
            for (problem in problems) {
                result.add(toSingleProblemContextDetail(problem))
            }
            return result.build()
        }


        @Suppress("UNCHECKED_CAST")
        private fun <T : OperationDescriptor?> assertDescriptorType(type: Class<out OperationDescriptor>, descriptor: OperationDescriptor?): T? {
            val operationDescriptor = descriptor!!
            val descriptorClass: Class<out OperationDescriptor> = operationDescriptor.javaClass
            check(type.isAssignableFrom(descriptorClass)) { String.format("Unexpected operation type. Required %s but found %s", type.getName(), descriptorClass.getName()) }
            return operationDescriptor as T?
        }

        private fun inferLegacyTestSource(descriptor: InternalJvmTestDescriptor): TestSource {
            if (descriptor.className != null && descriptor.methodName != null) {
                return DefaultMethodSource(descriptor.className!!, descriptor.methodName!!)
            } else if (descriptor.className != null && descriptor.methodName == null) {
                return DefaultClassSource(descriptor.className!!)
            } else {
                return DefaultNoSource
            }
        }

        private fun toTestSource(descriptor: InternalSourceAwareTestDescriptor): TestSource {
            val testSource = descriptor.source
            return toTestSource(testSource)
        }

        private fun toTestSource(testSource: InternalTestSource?): TestSource {
            if (testSource == null) {
                return DefaultOtherSource
            }
            if (testSource is InternalFileSource) {
                val fileSource = testSource
                return DefaultFileSource(fileSource.getFile()!!, toFilePosition(testSource.getPosition()))
            } else if (testSource is InternalDirectorySource) {
                return DefaultDirectorySource(testSource.getFile()!!)
            } else if (testSource is InternalClassSource) {
                val classSource = testSource
                return DefaultClassSource(classSource.getClassName()!!)
            } else if (testSource is InternalMethodSource) {
                val methodSource = testSource
                return DefaultMethodSource(methodSource.getClassName()!!, methodSource.getMethodName()!!)
            } else if (testSource is InternalClasspathResourceSource) {
                val classpathResourceSource = testSource
                return DefaultClasspathResourceSource(classpathResourceSource.getClasspathResourceName()!!, toFilePosition(classpathResourceSource.getPosition()))
            } else if (testSource is InternalMissingSource) {
                return DefaultNoSource
            } else {
                return DefaultOtherSource
            }
        }

        private fun toFilePosition(position: InternalFilePosition?): FilePosition? {
            if (position == null) {
                return null
            }
            return DefaultFilePosition(position.line, position.column)
        }

        private fun toJvmTestKind(testKind: String?): JvmTestKind {
            if (InternalJvmTestDescriptor.KIND_SUITE == testKind) {
                return JvmTestKind.SUITE
            } else if (InternalJvmTestDescriptor.KIND_ATOMIC == testKind) {
                return JvmTestKind.ATOMIC
            } else {
                return JvmTestKind.UNKNOWN
            }
        }

        private fun toProblem(basicProblemDetails: InternalBasicProblemDetails): Problem {
            return DefaultProblem(
                toProblemDefinition(basicProblemDetails.label, basicProblemDetails.category, basicProblemDetails.severity, basicProblemDetails.documentationLink),
                toContextualLabel(basicProblemDetails.label!!.label),
                toProblemDetails(basicProblemDetails.details) ?: DefaultDetails(""),
                toLocations(basicProblemDetails.locations),
                mutableListOf<Location>(),
                toSolutions(basicProblemDetails.solutions),
                toAdditionalData(basicProblemDetails.additionalData),
                toFailure(basicProblemDetails)
            )
        }

        private fun toProblem(basicProblemDetails: InternalBasicProblemDetailsVersion3): Problem {
            val originLocations: MutableList<InternalLocation>?
            val contextualLocations: MutableList<InternalLocation>?
            if (basicProblemDetails is InternalBasicProblemDetailsVersion4) {
                originLocations = basicProblemDetails.originLocations
                contextualLocations = basicProblemDetails.contextualLocations
            } else {
                originLocations = basicProblemDetails.locations
                contextualLocations = mutableListOf<InternalLocation>()
            }
            return DefaultProblem(
                toProblemDefinition(basicProblemDetails.definition),
                toContextualLabel(basicProblemDetails.contextualLabel)!!,
                toProblemDetails(basicProblemDetails.details) ?: DefaultDetails(""),
                toLocations(originLocations),
                toLocations(contextualLocations),
                toSolutions(basicProblemDetails.solutions),
                toAdditionalData(basicProblemDetails.additionalData),
                Companion.toFailure(basicProblemDetails.failure!!)
            )
        }

        private fun toProblemDefinition(problemDefinition: InternalProblemDefinition?): ProblemDefinition {
            val definition = problemDefinition!!
            return DefaultProblemDefinition(
                toProblemId(definition.id),
                toProblemSeverity(definition.severity),
                toDocumentationLink(definition.documentationLink)
            )
        }

        private fun toProblemDefinition(label: InternalLabel?, category: InternalProblemCategory?, severity: InternalSeverity?, documentationLink: InternalDocumentationLink?): ProblemDefinition {
            return DefaultProblemDefinition(
                toProblemId(label!!, category!!),
                toProblemSeverity(severity),
                toDocumentationLink(documentationLink)
            )
        }

        private fun toProblemId(problemId: InternalProblemId?): ProblemId {
            val id = problemId!!
            return DefaultProblemId(id.name!!, id.displayName!!, toProblemGroup(id.group)!!)
        }

        private fun toProblemId(label: InternalLabel, category: InternalProblemCategory): ProblemId {
            val categories: MutableList<String> = ArrayList<String>()
            categories.add(category.category!!)
            categories.addAll(category.subcategories.orEmpty())

            return DefaultProblemId(categories.removeAt(categories.size - 1), label.label!!, toProblemGroup(categories)!!)
        }

        private fun toProblemGroup(groupNames: MutableList<String>): ProblemGroup? {
            if (groupNames.isEmpty()) {
                return null
            } else {
                val groupName = groupNames.removeAt(groupNames.size - 1)
                return DefaultProblemGroup(groupName, groupName, toProblemGroup(groupNames))
            }
        }

        private fun toProblemGroup(problemGroup: InternalProblemGroup?): ProblemGroup? {
            if (problemGroup == null) {
                return null
            }
            return DefaultProblemGroup(problemGroup.name!!, problemGroup.displayName!!, if (problemGroup.parent == null) null else Companion.toProblemGroup(problemGroup.parent!!))
        }

        private fun toAdditionalData(additionalData: InternalAdditionalData?): AdditionalData {
            if (additionalData is InternalProxiedAdditionalData) {
                val proxy = additionalData.proxy
                return DefaultCustomAdditionalData(additionalData.asMap ?: mutableMapOf(), proxy!!)
            }
            if (additionalData == null) {
                return DefaultAdditionalData(mutableMapOf<String, Any>())
            }
            return DefaultAdditionalData(additionalData.asMap ?: mutableMapOf())
        }

        private fun toContextualLabel(contextualLabel: InternalContextualLabel?): ContextualLabel? {
            return if (contextualLabel == null) null else DefaultContextualLabel(contextualLabel.contextualLabel)
        }

        private fun toContextualLabel(contextualLabel: String?): ContextualLabel {
            return (if (contextualLabel == null) null else org.gradle.tooling.events.problems.internal.DefaultContextualLabel(contextualLabel))!!
        }

        private fun toProblemSeverity(severity: InternalSeverity?): Severity {
            return from(if (severity != null) severity.severity else Severity.WARNING.severity)
        }

        private fun toLocations(locations: MutableList<InternalLocation>?): MutableList<Location> {
            val result: MutableList<Location> = ArrayList<Location>(locations?.size ?: 0)
            for (location in locations.orEmpty()) {
                if (location is InternalLineInFileLocation) {
                    val l = location
                    result.add(DefaultLineInFileLocation(l.path!!, l.line, l.column, l.length))
                } else if (location is InternalOffsetInFileLocation) {
                    val l = location
                    result.add(DefaultOffsetInFileLocation(l.path!!, l.offset, l.length))
                } else if (location is InternalFileLocation) {
                    val l = location
                    result.add(DefaultFileLocation(l.path!!))
                } else if (location is InternalPluginIdLocation) {
                    val pluginLocation = location
                    result.add(DefaultPluginIdLocation(pluginLocation.pluginId!!))
                } else if (location is InternalTaskPathLocation) {
                    val taskLocation = location
                    result.add(DefaultTaskPathLocation(taskLocation.buildTreePath!!))
                }
            }
            return result
        }

        private fun toDocumentationLink(link: InternalDocumentationLink?): DocumentationLink {
            val url = link?.url
            return (if (url == null) null else org.gradle.tooling.events.problems.internal.DefaultDocumentationLink(url))!!
        }

        private fun toSolutions(solutions: MutableList<InternalSolution>?): MutableList<Solution> {
            val result: MutableList<Solution> = ArrayList<Solution>(solutions?.size ?: 0)
            for (solution in solutions.orEmpty()) {
                result.add(DefaultSolution(solution.solution!!))
            }
            return result
        }

        private fun toProblemDetails(details: InternalDetails?): Details? {
            if (details != null) {
                return DefaultDetails(details.details!!)
            }
            return null
        }

        private fun toFileDownloadResult(result: InternalOperationResult): FileDownloadResult? {
            val fileDownloadResult = result as InternalFileDownloadResult
            if (result is InternalNotFoundFileDownloadResult) {
                return NotFoundFileDownloadSuccessResult(result.startTime, result.endTime)
            }
            if (result is InternalSuccessResult) {
                return DefaultFileDownloadSuccessResult(result.startTime, result.endTime, fileDownloadResult.bytesDownloaded)
            }
            if (result is InternalFailureResult) {
                return DefaultFileDownloadFailureResult(result.startTime, result.endTime, toFailures(result.failures), fileDownloadResult.bytesDownloaded)
            }
            return null
        }

        private fun toTestResult(result: InternalTestResult): TestOperationResult? {
            if (result is InternalTestSuccessResult) {
                return DefaultTestSuccessResult(result.startTime, result.endTime)
            } else if (result is InternalTestSkippedResult) {
                return DefaultTestSkippedResult(result.startTime, result.endTime)
            } else if (result is InternalTestFailureResult) {
                return DefaultTestFailureResult(result.startTime, result.endTime, toFailures(result.failures))
            } else {
                return null
            }
        }

        @JvmStatic
        fun toTaskResult(result: InternalTaskResult): TaskOperationResult? {
            if (result is InternalTaskSuccessResult) {
                val successResult = result
                if (result is InternalJavaCompileTaskOperationResult) {
                    val annotationProcessorResults: MutableList<JavaCompileTaskOperationResult.AnnotationProcessorResult?>? =
                        toAnnotationProcessorResults((result as InternalJavaCompileTaskOperationResult).annotationProcessorResults)
                    return DefaultJavaCompileTaskSuccessResult(
                        result.startTime,
                        result.endTime,
                        successResult.isUpToDate,
                        isFromCache(result),
                        toTaskExecutionDetails(result),
                        annotationProcessorResults
                    )
                }
                return DefaultTaskSuccessResult(result.startTime, result.endTime, successResult.isUpToDate, isFromCache(result), toTaskExecutionDetails(result))
            } else if (result is InternalTaskSkippedResult) {
                return DefaultTaskSkippedResult(result.startTime, result.endTime, result.skipMessage)
            } else if (result is InternalTaskFailureResult) {
                return DefaultTaskFailureResult(result.startTime, result.endTime, toFailures(result.failures), toTaskExecutionDetails(result))
            } else {
                return null
            }
        }

        private fun isFromCache(result: InternalTaskResult): Boolean {
            if (result is InternalTaskCachedResult) {
                return result.isFromCache
            }
            return false
        }

        private fun toTaskExecutionDetails(result: InternalTaskResult): TaskExecutionDetails {
            if (result is InternalIncrementalTaskResult) {
                val taskResult = result
                return TaskExecutionDetails.of(taskResult.isIncremental, taskResult.executionReasons ?: mutableListOf())
            }
            return unsupported()
        }

        private fun toWorkItemResult(result: InternalOperationResult): WorkItemOperationResult? {
            if (result is InternalSuccessResult) {
                return DefaultWorkItemSuccessResult(result.startTime, result.endTime)
            } else if (result is InternalFailureResult) {
                return DefaultWorkItemFailureResult(result.startTime, result.endTime, toFailures(result.failures))
            } else {
                return null
            }
        }

        private fun toProjectConfigurationResult(result: InternalProjectConfigurationResult): ProjectConfigurationOperationResult? {
            if (result is InternalSuccessResult) {
                return DefaultProjectConfigurationSuccessResult(result.startTime, result.endTime, toPluginApplicationResults(result.pluginApplicationResults))
            } else if (result is InternalFailureResult) {
                return DefaultProjectConfigurationFailureResult(
                    result.startTime,
                    result.endTime,
                    toFailures(result.failures),
                    toPluginApplicationResults(result.pluginApplicationResults)
                )
            } else {
                return null
            }
        }

        private fun toPluginApplicationResults(pluginApplicationResults: MutableList<out InternalProjectConfigurationResult.InternalPluginApplicationResult?>?): MutableList<out ProjectConfigurationOperationResult.PluginApplicationResult> {
            val results: MutableList<ProjectConfigurationOperationResult.PluginApplicationResult> = ArrayList<ProjectConfigurationOperationResult.PluginApplicationResult>()
            for (result in pluginApplicationResults.orEmpty()) {
                val plugin: PluginIdentifier? = toPluginIdentifier(result!!.plugin)
                if (plugin != null) {
                    results.add(DefaultPluginApplicationResult(plugin, result.totalConfigurationTime))
                }
            }
            return results
        }

        private fun toPluginIdentifier(pluginIdentifier: InternalPluginIdentifier?): PluginIdentifier? {
            if (pluginIdentifier == null) {
                return null
            }
            if (pluginIdentifier is InternalBinaryPluginIdentifier) {
                val binaryPlugin = pluginIdentifier
                return DefaultBinaryPluginIdentifier(binaryPlugin.displayName!!, binaryPlugin.className!!, binaryPlugin.pluginId!!)
            } else if (pluginIdentifier is InternalScriptPluginIdentifier) {
                val scriptPlugin = pluginIdentifier
                return DefaultScriptPluginIdentifier(scriptPlugin.displayName!!, scriptPlugin.uri!!)
            } else {
                return null
            }
        }

        private fun toTransformResult(result: InternalOperationResult): TransformOperationResult? {
            if (result is InternalSuccessResult) {
                return DefaultTransformSuccessResult(result.startTime, result.endTime)
            } else if (result is InternalFailureResult) {
                return DefaultTransformFailureResult(result.startTime, result.endTime, toFailures(result.failures))
            } else {
                return null
            }
        }

        private fun toResult(result: InternalOperationResult): OperationResult? {
            if (result is InternalSuccessResult) {
                return DefaultOperationSuccessResult(result.startTime, result.endTime)
            } else if (result is InternalFailureResult) {
                return DefaultOperationFailureResult(result.startTime, result.endTime, toFailures(result.failures))
            } else {
                return null
            }
        }

        fun toFailures(causes: MutableCollection<out InternalFailure?>?): MutableList<Failure> {
            if (causes == null) {
                return mutableListOf()
            }
            val failures: MutableList<Failure> = ArrayList<Failure>(causes.size)
            for (cause in causes) {
                val f: Failure? = toFailure(cause)
                if (f != null) {
                    failures.add(f)
                }
            }
            return failures
        }

        private fun toFailure(problemDetails: InternalBasicProblemDetails): Failure? {
            if (problemDetails !is InternalBasicProblemDetailsVersion2) {
                return null
            }
            return Companion.toFailure(problemDetails.getFailure()!!)
        }

        private fun toFailure(origFailure: InternalFailure?): Failure? {
            if (origFailure == null) {
                return null
            }
            val problemDetails: MutableList<InternalBasicProblemDetailsVersion3?> = ArrayList<InternalBasicProblemDetailsVersion3?>()
            try {
                problemDetails.addAll(origFailure.problems.orEmpty())
            } catch (ignore: AbstractMethodError) {
                // Older Gradle versions don't have this method
            }
            val clientProblems: MutableList<Problem?> = ArrayList<Problem?>(problemDetails.size)
            for (problemDetail in problemDetails) {
                if (problemDetail == null) { // Should not happen, but with some older snapshot versions we see this.
                    continue
                }
                clientProblems.add(toProblem(problemDetail))
            }
            if (origFailure is InternalTestAssertionFailure) {
                if (origFailure is InternalFileComparisonTestAssertionFailure) {
                    val assertionFailure = origFailure as InternalTestAssertionFailure
                    return DefaultFileComparisonTestAssertionFailure(
                        assertionFailure.message!!,
                        assertionFailure.description!!,
                        assertionFailure.expected!!,
                        assertionFailure.actual!!,
                        toFailures(origFailure.causes),
                        (origFailure as InternalTestAssertionFailure).className!!,
                        (origFailure as InternalTestAssertionFailure).stacktrace!!,
                        origFailure.expectedContent!!,
                        origFailure.actualContent!!
                    )
                }
                val assertionFailure = origFailure
                return DefaultTestAssertionFailure(
                    assertionFailure.message!!,
                    assertionFailure.description,
                    assertionFailure.expected!!,
                    assertionFailure.actual!!,
                    toFailures(origFailure.causes),
                    origFailure.className,
                    origFailure.stacktrace
                )
            } else if (origFailure is InternalTestFrameworkFailure) {
                val frameworkFailure = origFailure
                return DefaultTestFrameworkFailure(
                    frameworkFailure.message!!,
                    frameworkFailure.description,
                    toFailures(origFailure.causes),
                    origFailure.className,
                    origFailure.stacktrace
                )
            }
            return DefaultFailure(
                origFailure.message!!,
                origFailure.description,
                toFailures(origFailure.causes),
                clientProblems
            )
        }

        private fun toAnnotationProcessorResults(protocolResults: MutableList<InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult?>?): MutableList<JavaCompileTaskOperationResult.AnnotationProcessorResult?>? {
            if (protocolResults == null) {
                return null
            }
            val results: MutableList<JavaCompileTaskOperationResult.AnnotationProcessorResult?> = ArrayList<JavaCompileTaskOperationResult.AnnotationProcessorResult?>()
            for (result in protocolResults) {
                if (result != null) {
                    results.add(toAnnotationProcessorResult(result))
                }
            }
            return results
        }

        private fun toAnnotationProcessorResult(result: InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult): JavaCompileTaskOperationResult.AnnotationProcessorResult {
            return DefaultAnnotationProcessorResult(result.className!!, toAnnotationProcessorResultType(result.type!!), result.duration)
        }

        private fun toAnnotationProcessorResultType(type: String): JavaCompileTaskOperationResult.AnnotationProcessorResult.Type {
            if (type == InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult.TYPE_AGGREGATING) {
                return JavaCompileTaskOperationResult.AnnotationProcessorResult.Type.AGGREGATING
            }
            if (type == InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult.TYPE_ISOLATING) {
                return JavaCompileTaskOperationResult.AnnotationProcessorResult.Type.ISOLATING
            }
            return JavaCompileTaskOperationResult.AnnotationProcessorResult.Type.UNKNOWN
        }
    }
}
