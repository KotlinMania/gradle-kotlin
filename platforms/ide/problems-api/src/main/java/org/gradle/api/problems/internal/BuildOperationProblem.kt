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
import org.gradle.api.problems.ProblemDefinition
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.Severity
import org.gradle.operations.problems.DocumentationLink
import org.gradle.operations.problems.PluginIdLocation
import org.gradle.operations.problems.Problem
import org.gradle.operations.problems.ProblemLocation
import org.gradle.operations.problems.ProblemSeverity
import org.gradle.operations.problems.StackTraceLocation

class BuildOperationProblem(private val problem: ProblemInternal) : Problem {
    val definition: ProblemDefinition
        get() = BuildOperationProblemDefinition(problem.getDefinition())

    val severity: ProblemSeverity
        get() {
            when (problem.getDefinition().getSeverity()) {
                Severity.ADVICE -> return ProblemSeverity.ADVICE
                Severity.WARNING -> return ProblemSeverity.WARNING
                Severity.ERROR -> return ProblemSeverity.ERROR
                else -> throw IllegalArgumentException("Unknown severity: " + problem.getDefinition().getSeverity())
            }
        }

    val contextualLabel: String?
        get() = problem.getContextualLabel()

    val solutions: MutableList<String>
        get() = problem.getSolutions()

    val details: String?
        get() = problem.getDetails()

    val originLocations: MutableList<ProblemLocation>
        get() = convertProblemLocations(problem.getOriginLocations())

    val contextualLocations: MutableList<ProblemLocation>
        get() = convertProblemLocations(problem.getContextualLocations())

    private fun convertProblemLocations(locations: MutableList<org.gradle.api.problems.ProblemLocation>): ImmutableList<ProblemLocation> {
        val builder = ImmutableList.builder<ProblemLocation>()
        for (location in locations) {
            val buildOperationLocation: ProblemLocation? = convertToLocation(location)
            if (buildOperationLocation != null) {
                builder.add(buildOperationLocation)
            }
        }
        return builder.build()
    }

    private class BuildOperationProblemDefinition(private val definition: ProblemDefinition) : org.gradle.operations.problems.ProblemDefinition {
        val name: String
            get() = definition.id.name

        val displayName: String
            get() = definition.id.displayName

        val group: ProblemGroup
            get() = BuildOperationProblemGroup(definition.id.group)

        val documentationLink: DocumentationLink?
            get() {
                val documentationLink = definition.documentationLink as DocLinkInternal?
                return if (documentationLink == null) null else BuildOperationDocumentationLink(documentationLink)
            }

        private class BuildOperationProblemGroup(private val currentGroup: ProblemGroup) : org.gradle.operations.problems.ProblemGroup {
            val name: String
                get() = currentGroup.name

            val displayName: String
                get() = currentGroup.displayName

            val parent: ProblemGroup?
                get() {
                    val parent = currentGroup.parent
                    return if (parent == null) null else BuildOperationProblemGroup(parent)
                }
        }

        private class BuildOperationDocumentationLink(private val documentationLink: DocLinkInternal) : DocumentationLink {
            val url: String
                get() = documentationLink.getUrl()
        }
    }

    private open class BuildOperationFileLocation(private val location: FileLocation) : org.gradle.operations.problems.FileLocation {
        val path: String
            get() = location.getPath()

        val displayName: String
            get() = "file '" + path + "'"
    }

    private class BuildOperationLineInFileLocation(private val lineInFileLocation: LineInFileLocation) : BuildOperationFileLocation(
        lineInFileLocation
    ), org.gradle.operations.problems.LineInFileLocation {
        val line: Int
            get() = lineInFileLocation.getLine()

        val column: Int?
            get() = if (lineInFileLocation.getColumn() <= 0) null else lineInFileLocation.getColumn()

        val length: Int?
            get() = if (lineInFileLocation.getLength() <= 0) null else lineInFileLocation.getLength()

        override fun getDisplayName(): String {
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

    private class BuildOperationStackTraceLocation(private val stackTraceLocation: StackTraceLocation) : StackTraceLocation {
        val fileLocation: FileLocation?
            get() = if (stackTraceLocation.getFileLocation() == null)
                null
            else
                Companion.convertToBuildOperationFileLocation(stackTraceLocation.getFileLocation()!!)

        val stackTrace: MutableList<StackTraceElement>
            get() = stackTraceLocation.getStackTrace()

        val displayName: String
            get() = "stack trace location " + stackTraceLocation.getFileLocation()
    }

    private class BuildOperationOffsetInFileLocation(private val offsetInFileLocation: OffsetInFileLocation) : BuildOperationFileLocation(
        offsetInFileLocation
    ), org.gradle.operations.problems.OffsetInFileLocation {
        val offset: Int
            get() = offsetInFileLocation.getOffset()

        val length: Int
            get() = offsetInFileLocation.getLength()

        override fun getDisplayName(): String {
            return "offset in file '" + path + ":" + offset + ":" + length + "'"
        }
    }

    private class BuildOperationPluginIdLocation(private val pluginId: PluginIdLocation) : PluginIdLocation {
        override fun getPluginId(): String {
            return pluginId.getPluginId()
        }

        val displayName: String
            get() = "plugin '" + pluginId.getPluginId() + "'"
    }

    companion object {
        private fun convertToLocation(location: org.gradle.api.problems.ProblemLocation): ProblemLocation? {
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

        private fun convertToBuildOperationFileLocation(location: org.gradle.api.problems.ProblemLocation): org.gradle.operations.problems.FileLocation {
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
