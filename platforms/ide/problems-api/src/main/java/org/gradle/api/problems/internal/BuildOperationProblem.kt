/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.api.problems.internal

import com.google.common.collect.ImmutableList
import org.gradle.api.problems.FileLocation
import org.gradle.api.problems.LineInFileLocation
import org.gradle.api.problems.OffsetInFileLocation
import org.gradle.api.problems.ProblemDefinition as ApiProblemDefinition
import org.gradle.api.problems.ProblemGroup as ApiProblemGroup
import org.gradle.api.problems.Severity
import org.gradle.operations.problems.DocumentationLink as OperationDocumentationLink
import org.gradle.operations.problems.FileLocation as OperationFileLocation
import org.gradle.operations.problems.LineInFileLocation as OperationLineInFileLocation
import org.gradle.operations.problems.OffsetInFileLocation as OperationOffsetInFileLocation
import org.gradle.operations.problems.PluginIdLocation as OperationPluginIdLocation
import org.gradle.operations.problems.Problem as OperationProblem
import org.gradle.operations.problems.ProblemDefinition as OperationProblemDefinition
import org.gradle.operations.problems.ProblemGroup as OperationProblemGroup
import org.gradle.operations.problems.ProblemLocation as OperationProblemLocation
import org.gradle.operations.problems.ProblemSeverity
import org.gradle.operations.problems.StackTraceLocation as OperationStackTraceLocation

class BuildOperationProblem(private val problem: ProblemInternal) : OperationProblem {
    override val definition: OperationProblemDefinition
        get() = BuildOperationProblemDefinition(problem.getDefinition()!!)

    override val severity: ProblemSeverity
        get() {
            when (problem.getDefinition()!!.getSeverity()) {
                Severity.ADVICE -> return ProblemSeverity.ADVICE
                Severity.WARNING -> return ProblemSeverity.WARNING
                Severity.ERROR -> return ProblemSeverity.ERROR
                else -> throw IllegalArgumentException("Unknown severity: " + problem.getDefinition()!!.getSeverity())
            }
        }

    override val contextualLabel: String?
        get() = problem.getContextualLabel()

    override val solutions: MutableList<String>
        get() = problem.getSolutions()!!

    override val details: String?
        get() = problem.getDetails()

    override val originLocations: MutableList<OperationProblemLocation>
        get() = convertProblemLocations(problem.getOriginLocations()!!)

    override val contextualLocations: MutableList<OperationProblemLocation>
        get() = convertProblemLocations(problem.getContextualLocations()!!)

    private fun convertProblemLocations(locations: MutableList<org.gradle.api.problems.ProblemLocation>): ImmutableList<OperationProblemLocation> {
        val builder = ImmutableList.builder<OperationProblemLocation>()
        for (location in locations) {
            val buildOperationLocation: OperationProblemLocation? = convertToLocation(location)
            if (buildOperationLocation != null) {
                builder.add(buildOperationLocation)
            }
        }
        return builder.build()
    }

    private class BuildOperationProblemDefinition(private val definition: ApiProblemDefinition) : OperationProblemDefinition {
        override val name: String?
            get() = definition.getId()?.getName()

        override val displayName: String?
            get() = definition.getId()?.getDisplayName()

        override val group: OperationProblemGroup?
            get() = definition.getId()?.getGroup()?.let { BuildOperationProblemGroup(it) }

        override val documentationLink: OperationDocumentationLink?
            get() {
                val documentationLink = definition.getDocumentationLink() as DocLinkInternal?
                return if (documentationLink == null) null else BuildOperationDocumentationLink(documentationLink)
            }

        private class BuildOperationProblemGroup(private val currentGroup: ApiProblemGroup) : OperationProblemGroup {
            override val name: String?
                get() = currentGroup.getName()

            override val displayName: String?
                get() = currentGroup.getDisplayName()

            override val parent: OperationProblemGroup?
                get() {
                    val parent = currentGroup.getParent()
                    return if (parent == null) null else BuildOperationProblemGroup(parent)
                }
        }

        private class BuildOperationDocumentationLink(private val documentationLink: DocLinkInternal) : OperationDocumentationLink {
            override val url: String?
                get() = documentationLink.getUrl()
        }
    }

    private open class BuildOperationFileLocation(private val location: FileLocation) : OperationFileLocation {
        override val path: String?
            get() = location.getPath()

        override val displayName: String
            get() = "file '" + path + "'"
    }

    private class BuildOperationLineInFileLocation(private val lineInFileLocation: LineInFileLocation) : BuildOperationFileLocation(
        lineInFileLocation
    ), OperationLineInFileLocation {
        override val line: Int
            get() = lineInFileLocation.getLine()

        override val column: Int?
            get() = if (lineInFileLocation.getColumn() <= 0) null else lineInFileLocation.getColumn()

        override val length: Int?
            get() = if (lineInFileLocation.getLength() <= 0) null else lineInFileLocation.getLength()

        override val displayName: String
            get() {
            var location = path + ":" + line
            if (column != null) {
                location += ":" + column
            }
            if (length != null) {
                location += ":" + length
            }
            return "file '" + location + "'"
            }
    }

    private class BuildOperationStackTraceLocation(private val stackTraceLocation: org.gradle.api.problems.internal.StackTraceLocation) : OperationStackTraceLocation {
        override val fileLocation: OperationFileLocation?
            get() = stackTraceLocation.getFileLocation()?.let { Companion.convertToBuildOperationFileLocation(it) }

        override val stackTrace: MutableList<StackTraceElement>?
            get() = stackTraceLocation.getStackTrace()

        override val displayName: String
            get() = "stack trace location " + stackTraceLocation.getFileLocation()
    }

    private class BuildOperationOffsetInFileLocation(private val offsetInFileLocation: OffsetInFileLocation) : BuildOperationFileLocation(
        offsetInFileLocation
    ), OperationOffsetInFileLocation {
        override val offset: Int
            get() = offsetInFileLocation.getOffset()

        override val length: Int
            get() = offsetInFileLocation.getLength()

        override val displayName: String
            get() = "offset in file '" + path + ":" + offset + ":" + length + "'"
    }

    private class BuildOperationPluginIdLocation(private val pluginIdLocation: org.gradle.api.problems.internal.PluginIdLocation) : OperationPluginIdLocation {
        override val pluginId: String?
            get() = pluginIdLocation.getPluginId()

        override val displayName: String
            get() = "plugin '" + pluginIdLocation.getPluginId() + "'"
    }

    companion object {
        private fun convertToLocation(location: org.gradle.api.problems.ProblemLocation): OperationProblemLocation? {
            if (location is FileLocation) {
                return convertToBuildOperationFileLocation(location)
            } else if (location is TaskLocation) {
                // The Develocity plugin will infer the task location from the build operation hierarchy - no need to send this contextual information
                return null
            } else if (location is org.gradle.api.problems.internal.PluginIdLocation) {
                return BuildOperationPluginIdLocation(location)
            } else if (location is org.gradle.api.problems.internal.StackTraceLocation) {
                return BuildOperationStackTraceLocation(location)
            }
            throw IllegalArgumentException("Unknown location type: " + location.javaClass + ", location: '" + location + "'")
        }

        private fun convertToBuildOperationFileLocation(location: org.gradle.api.problems.ProblemLocation): OperationFileLocation {
            if (location is LineInFileLocation) {
                return BuildOperationLineInFileLocation(location)
            } else if (location is OffsetInFileLocation) {
                return BuildOperationOffsetInFileLocation(location)
            } else {
                return BuildOperationFileLocation(location as FileLocation)
            }
        }
    }
}
