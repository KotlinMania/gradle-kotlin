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
package org.gradle.tooling.internal.provider.runner

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.api.problems.DocLink
import org.gradle.api.problems.FileLocation
import org.gradle.api.problems.LineInFileLocation
import org.gradle.api.problems.OffsetInFileLocation
import org.gradle.api.problems.ProblemDefinition
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemLocation
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.DefaultProblemProgressDetails
import org.gradle.api.problems.internal.DefaultProblemsSummaryProgressDetails
import org.gradle.api.problems.internal.DeprecationData
import org.gradle.api.problems.internal.GeneralData
import org.gradle.api.problems.internal.PluginIdLocation
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.ProblemSummaryData
import org.gradle.api.problems.internal.StackTraceLocation
import org.gradle.api.problems.internal.TaskLocation
import org.gradle.api.problems.internal.TypeValidationData
import org.gradle.api.problems.internal.TypedAdditionalData
import org.gradle.internal.build.event.types.DefaultContextualLabel
import org.gradle.internal.build.event.types.DefaultDetails
import org.gradle.internal.build.event.types.DefaultDocumentationLink
import org.gradle.internal.build.event.types.DefaultFailure
import org.gradle.internal.build.event.types.DefaultFileLocation
import org.gradle.internal.build.event.types.DefaultInternalAdditionalData
import org.gradle.internal.build.event.types.DefaultInternalPayloadSerializedAdditionalData
import org.gradle.internal.build.event.types.DefaultLineInFileLocation
import org.gradle.internal.build.event.types.DefaultOffsetInFileLocation
import org.gradle.internal.build.event.types.DefaultPluginIdLocation
import org.gradle.internal.build.event.types.DefaultProblemDefinition
import org.gradle.internal.build.event.types.DefaultProblemDescriptor
import org.gradle.internal.build.event.types.DefaultProblemDetails
import org.gradle.internal.build.event.types.DefaultProblemEvent
import org.gradle.internal.build.event.types.DefaultProblemGroup
import org.gradle.internal.build.event.types.DefaultProblemId
import org.gradle.internal.build.event.types.DefaultProblemSummary
import org.gradle.internal.build.event.types.DefaultProblemsSummariesDetails
import org.gradle.internal.build.event.types.DefaultSeverity
import org.gradle.internal.build.event.types.DefaultSolution
import org.gradle.internal.build.event.types.DefaultTaskPathLocation
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.tooling.internal.protocol.InternalFailure
import org.gradle.tooling.internal.protocol.InternalProblemDefinition
import org.gradle.tooling.internal.protocol.InternalProblemEventVersion2
import org.gradle.tooling.internal.protocol.InternalProblemGroup
import org.gradle.tooling.internal.protocol.InternalProblemId
import org.gradle.tooling.internal.protocol.InternalProblemSummary
import org.gradle.tooling.internal.protocol.events.InternalProblemDescriptor
import org.gradle.tooling.internal.protocol.problem.InternalAdditionalData
import org.gradle.tooling.internal.protocol.problem.InternalContextualLabel
import org.gradle.tooling.internal.protocol.problem.InternalDetails
import org.gradle.tooling.internal.protocol.problem.InternalDocumentationLink
import org.gradle.tooling.internal.protocol.problem.InternalLocation
import org.gradle.tooling.internal.protocol.problem.InternalSeverity
import org.gradle.tooling.internal.protocol.problem.InternalSolution
import org.jspecify.annotations.NullMarked
import java.util.Map
import java.util.Optional
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors

@NullMarked
object ProblemsProgressEventUtils {
    private val ADVICE: InternalSeverity = DefaultSeverity(0)
    private val WARNING: InternalSeverity = DefaultSeverity(1)
    private val ERROR: InternalSeverity = DefaultSeverity(2)

    fun createProblemEvent(buildOperationId: OperationIdentifier, details: DefaultProblemProgressDetails, operationIdentifierSupplier: Supplier<OperationIdentifier>): InternalProblemEventVersion2 {
        val problem = details.problem
        return DefaultProblemEvent(
            createDefaultProblemDescriptor(buildOperationId, operationIdentifierSupplier),
            createDefaultProblemDetails(problem)
        )
    }

    fun createProblemSummaryEvent(
        buildOperationId: OperationIdentifier?,
        details: DefaultProblemsSummaryProgressDetails,
        operationIdentifierSupplier: Supplier<OperationIdentifier>
    ): InternalProblemEventVersion2 {
        return ProblemsProgressEventUtils.createProblemSummaryEvent(buildOperationId!!, details.problemIdCounts, operationIdentifierSupplier)
    }

    private fun createProblemSummaryEvent(
        buildOperationId: OperationIdentifier,
        problemIdCounts: MutableList<ProblemSummaryData>,
        operationIdentifierSupplier: Supplier<OperationIdentifier>
    ): InternalProblemEventVersion2 {
        val internalIdCounts: MutableList<InternalProblemSummary> = problemIdCounts.stream()
            .map<DefaultProblemSummary> { it: ProblemSummaryData? -> DefaultProblemSummary(toInternalId(it!!.problemId), it.count) }
            .collect(ImmutableList.toImmutableList<InternalProblemSummary>())
        return DefaultProblemEvent(
            createDefaultProblemDescriptor(buildOperationId, operationIdentifierSupplier),
            DefaultProblemsSummariesDetails(internalIdCounts)
        )
    }

    private fun toInternalFailure(ex: Throwable?): InternalFailure? {
        if (ex == null) {
            return null
        }
        return DefaultFailure.fromThrowable(ex)
    }

    private fun createDefaultProblemDescriptor(parentBuildOperationId: OperationIdentifier, operationIdentifierSupplier: Supplier<OperationIdentifier>): InternalProblemDescriptor {
        return DefaultProblemDescriptor(
            operationIdentifierSupplier.get(),
            parentBuildOperationId
        )
    }

    fun createDefaultProblemDetails(problem: ProblemInternal): DefaultProblemDetails {
        return DefaultProblemDetails(
            toInternalDefinition(problem.definition),
            toInternalDetails(problem.details),
            toInternalContextualLabel(problem.contextualLabel),
            ProblemsProgressEventUtils.toInternalLocations(problem.originLocations),
            ProblemsProgressEventUtils.toInternalLocations(problem.contextualLocations),
            ProblemsProgressEventUtils.toInternalSolutions(problem.solutions!!),
            toInternalAdditionalData(problem),
            toInternalFailure(problem.exception)
        )
    }

    private fun toInternalDefinition(definition: ProblemDefinition): InternalProblemDefinition {
        return DefaultProblemDefinition(
            ProblemsProgressEventUtils.toInternalId(definition.id!!),
            ProblemsProgressEventUtils.toInternalSeverity(definition.severity!!),
            toInternalDocumentationLink(definition.documentationLink)
        )
    }

    private fun toInternalId(problemId: ProblemId): InternalProblemId {
        return DefaultProblemId(problemId.name!!, problemId.displayName!!, ProblemsProgressEventUtils.toInternalGroup(problemId.group!!))
    }

    private fun toInternalGroup(group: ProblemGroup): InternalProblemGroup {
        return DefaultProblemGroup(group.name!!, group.displayName!!, if (group.parent == null) null else ProblemsProgressEventUtils.toInternalGroup(group.parent!!))
    }

    private fun toInternalContextualLabel(contextualLabel: String?): InternalContextualLabel? {
        return if (contextualLabel == null) null else DefaultContextualLabel(contextualLabel)
    }

    private fun toInternalDetails(details: String?): InternalDetails? {
        return if (details == null) null else DefaultDetails(details)
    }

    private fun toInternalSeverity(severity: Severity): InternalSeverity {
        when (severity) {
            Severity.ADVICE -> return ADVICE
            Severity.WARNING -> return WARNING
            Severity.ERROR -> return ERROR
            else -> throw RuntimeException("No mapping defined for severity level " + severity)
        }
    }

    private fun toInternalLocations(locations: MutableList<ProblemLocation>): MutableList<InternalLocation> {
        return locations.stream()
            .filter { location: ProblemLocation? -> !(location is StackTraceLocation && location.fileLocation == null) }
            .map<ProblemLocation> { location: ProblemLocation? ->
                if (location is StackTraceLocation)
                    location.fileLocation
                else
                    location
            }
            .map { location: ProblemLocation? ->
                if (location is LineInFileLocation) {
                    val fileLocation = location
                    return@map DefaultLineInFileLocation(fileLocation.path!!, fileLocation.line, fileLocation.column, fileLocation.length)
                } else if (location is OffsetInFileLocation) {
                    val fileLocation = location
                    return@map DefaultOffsetInFileLocation(fileLocation.path!!, fileLocation.offset, fileLocation.length)
                } else if (location is FileLocation) { // generic class must be after the subclasses in the if-elseif chain.
                    val fileLocation = location
                    return@map DefaultFileLocation(fileLocation.path!!)
                } else if (location is PluginIdLocation) {
                    val pluginLocation = location
                    return@map DefaultPluginIdLocation(pluginLocation.pluginId!!)
                } else if (location is TaskLocation) {
                    val taskLocation = location
                    return@map DefaultTaskPathLocation(taskLocation.buildTreePath!!)
                } else {
                    throw RuntimeException("No mapping defined for " + location!!.javaClass.getName())
                }
            }.collect(ImmutableList.toImmutableList<InternalLocation>())
    }

    private fun toInternalDocumentationLink(link: DocLink?): InternalDocumentationLink? {
        return if (link == null || link.url == null) null else DefaultDocumentationLink(link.url!!)
    }

    private fun toInternalSolutions(solutions: MutableList<String>): MutableList<InternalSolution> {
        return solutions.stream()
            .map<DefaultSolution> { solution: String? -> DefaultSolution(solution) }
            .collect(ImmutableList.toImmutableList<InternalSolution>())
    }


    private fun toInternalAdditionalData(problem: ProblemInternal): InternalAdditionalData {
        val additionalData: Any? = problem.additionalData
        if (additionalData is DeprecationData) {
            // For now, we only expose deprecation data to the tooling API with generic additional data
            val data = additionalData
            return DefaultInternalAdditionalData(ImmutableMap.of<K, V>("type", data.type.name()))
        } else if (additionalData is TypeValidationData) {
            val data = additionalData
            val builder = ImmutableMap.builder<String, Any>()
            Optional.ofNullable<Any>(data.pluginId).ifPresent(Consumer { pluginId: Any? -> builder.put("pluginId", pluginId!!) })
            Optional.ofNullable<Any>(data.propertyName).ifPresent(Consumer { propertyName: Any? -> builder.put("propertyName", propertyName!!) })
            Optional.ofNullable<Any>(data.parentPropertyName).ifPresent(Consumer { parentPropertyName: Any? -> builder.put("parentPropertyName", parentPropertyName!!) })
            Optional.ofNullable<Any>(data.typeName).ifPresent(Consumer { typeName: Any? -> builder.put("typeName", typeName!!) })
            return DefaultInternalAdditionalData(builder.build())
        } else if (additionalData is GeneralData) {
            val data = additionalData
            return DefaultInternalAdditionalData(
                data.asMap.entrySet().stream()
                    .filter({ entry -> isSupportedType(entry.getValue()) })
                    .collect(Collectors.toMap(Function { Map.Entry.key }, Function { Map.Entry.value }))
            )
        } else if (additionalData is TypedAdditionalData) {
            val typedData = additionalData
            return DefaultInternalPayloadSerializedAdditionalData(typedData.bytesForIsolatedObject!!, typedData.serializedType!!)
        } else {
            return DefaultInternalAdditionalData(mutableMapOf<String, Any>())
        }
    }

    private fun isSupportedType(type: Any): Boolean {
        return type is String
    }
}
