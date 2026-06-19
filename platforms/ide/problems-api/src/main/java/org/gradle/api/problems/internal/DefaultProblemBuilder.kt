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
package org.gradle.api.problems.internal

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.problems.AdditionalData
import org.gradle.api.problems.DocLink
import org.gradle.api.problems.FileLocation
import org.gradle.api.problems.ProblemDefinition
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemLocation
import org.gradle.api.problems.Severity
import org.gradle.internal.code.UserCodeSource
import org.gradle.problems.Location
import org.gradle.problems.ProblemDiagnostics
import org.gradle.util.internal.TextUtil

class DefaultProblemBuilder(
    private val problemsInfrastructure: ProblemsInfrastructure
) : ProblemBuilderInternal {
    private var id: ProblemId? = null
    private var contextualLabel: String? = null
    private var severity: Severity? = null
    private val originLocations: MutableList<ProblemLocation> = ArrayList<ProblemLocation>()
    private val contextLocations: MutableList<ProblemLocation> = ArrayList<ProblemLocation>()
    private var details: String? = null
    private var docLink: DocLink? = null
    private var solutions: MutableList<String>
    var exception: Throwable? = null
        private set
    private var additionalData: AdditionalData? = null
    private var collectStackLocation = false
    private var diagnostics: ProblemDiagnostics? = null

    init {
        this.solutions = ArrayList<String>()
    }

    constructor(
        problem: ProblemInternal,
        infrastructure: ProblemsInfrastructure
    ) : this(infrastructure) {
        this.id = problem.getDefinition()!!.getId()
        this.contextualLabel = problem.getContextualLabel()
        this.solutions = ArrayList<String>(problem.getSolutions()!!)
        this.severity = problem.getDefinition()!!.getSeverity()
        this.originLocations.addAll(problem.getOriginLocations()!!)
        this.contextLocations.addAll(problem.getContextualLocations()!!)
        this.details = problem.getDetails()
        this.docLink = problem.getDefinition()!!.getDocumentationLink()
        this.exception = problem.getException()
        this.additionalData = problem.getAdditionalData()!!
    }

    override fun build(): ProblemInternal {
        // id is mandatory
        if (getId() == null) {
            return invalidProblem("missing-id", "Problem id must be specified", null)
        } else if (getId().getGroup() == null) {
            return invalidProblem("missing-parent", "Problem id must have a parent", null)
        }

        if (additionalData is UnsupportedAdditionalDataSpec) {
            return invalidProblem(
                "unsupported-additional-data", "Unsupported additional data type",
                "Unsupported additional data type: " + (additionalData as UnsupportedAdditionalDataSpec).type.getName() +
                        ". Supported types are: " + problemsInfrastructure.additionalDataBuilderFactory!!.supportedTypes
            )
        }

        val diagnostics = determineDiagnostics()
        if (diagnostics != null) {
            addLocationsFromDiagnostics(if (collectStackLocation) this.originLocations else this.contextLocations, diagnostics)
        }

        val problemDefinition: ProblemDefinition = DefaultProblemDefinition(getId(), getSeverity(), docLink)
        return DefaultProblem(
            problemDefinition,
            contextualLabel,
            solutions,
            originLocations,
            contextLocations,
            details,
            exception,
            additionalData
        )
    }

    private fun determineDiagnostics(): ProblemDiagnostics? {
        if (diagnostics != null) {
            return diagnostics
        }
        val problemStream = problemsInfrastructure.problemStream
        if (problemStream == null || (!collectStackLocation && areLocationsProvided())) {
            return null
        }
        return problemStream.forCurrentCaller(exceptionForStackLocation(this.severity == Severity.ERROR))
    }

    private fun areLocationsProvided(): Boolean {
        return !(contextLocations.isEmpty() && originLocations.isEmpty())
    }

    private fun exceptionForStackLocation(overruleStacktraceLimit: Boolean): Throwable? {
        return if (this.exception == null && overruleStacktraceLimit) java.lang.RuntimeException() else this.exception
    }

    private fun addLocationsFromDiagnostics(locations: MutableList<ProblemLocation>, diagnostics: ProblemDiagnostics) {
        val loc = diagnostics.getLocation()
        val fileLocation: FileLocation? = if (loc == null) null else getFileLocation(loc)
        if (fileLocation != null) {
            locations.remove(fileLocation)
        }
        if (collectStackLocation || fileLocation != null) {
            locations.add(DefaultStackTraceLocation(fileLocation, diagnostics.getStack()!!))
        }

        val pluginIdLocation: PluginIdLocation? = getDefaultPluginIdLocation(diagnostics)
        if (pluginIdLocation != null) {
            locations.add(pluginIdLocation)
        }
    }

    private fun invalidProblem(id: String, displayName: String, contextualLabel: String?): ProblemInternal {
        id(
            id, displayName, ProblemGroup.create(
                "problems-api",
                "Problems API"
            )
        ).stackLocation()
        val problemDefinition: ProblemDefinition = DefaultProblemDefinition(this.getId(), Severity.WARNING, null)
        val problemLocations: MutableList<ProblemLocation> = ArrayList<ProblemLocation>()
        val diagnostics = determineDiagnostics()
        if (diagnostics != null) {
            addLocationsFromDiagnostics(problemLocations, diagnostics)
        }
        return DefaultProblem(
            problemDefinition,
            contextualLabel,
            ImmutableList.of<String>(),
            problemLocations,
            ImmutableList.of<ProblemLocation>(),
            null,
            null,
            null
        )
    }

    protected fun getSeverity(): Severity {
        if (this.severity == null) {
            return Severity.WARNING
        }
        return this.severity!!
    }

    override fun contextualLabel(contextualLabel: String): ProblemBuilderInternal {
        // enforce contextual label to be a single line
        this.contextualLabel = TextUtil.replaceLineSeparatorsOf(contextualLabel, " ")
        return this
    }

    @Deprecated("")
    override fun severity(severity: Severity): ProblemBuilderInternal {
        // do nothing
        return this
    }

    override fun internalSeverity(severity: Severity): ProblemBuilderInternal {
        this.severity = severity
        return this
    }

    override fun getInfrastructure(): ProblemsInfrastructure {
        return problemsInfrastructure
    }

    override fun taskLocation(buildTreePath: String): ProblemBuilderInternal {
        this.contextLocations.add(DefaultTaskLocation(buildTreePath))
        return this
    }

    override fun fileLocation(path: String): ProblemBuilderInternal {
        addFileLocation(DefaultFileLocation.Companion.from(path))
        return this
    }

    override fun lineInFileLocation(path: String, line: Int): ProblemBuilderInternal {
        return addFileLocation(DefaultLineInFileLocation.Companion.from(path, line))
    }

    private fun addFileLocation(from: FileLocation): DefaultProblemBuilder {
        return addFileLocationTo(this.originLocations, from)
    }

    private fun addFileLocationTo(problemLocations: MutableList<ProblemLocation>, from: FileLocation): DefaultProblemBuilder {
        if (problemLocations.contains(from)) {
            return this
        }
        problemLocations.add(from)
        return this
    }

    override fun lineInFileLocation(path: String, line: Int, column: Int): ProblemBuilderInternal {
        addFileLocation(DefaultLineInFileLocation.Companion.from(path, line, column))
        return this
    }

    override fun offsetInFileLocation(path: String, offset: Int, length: Int): ProblemBuilderInternal {
        addFileLocation(DefaultOffsetInFileLocation.Companion.from(path, offset, length))
        return this
    }

    override fun lineInFileLocation(path: String, line: Int, column: Int, length: Int): ProblemBuilderInternal {
        addFileLocation(DefaultLineInFileLocation.Companion.from(path, line, column, length))
        return this
    }

    override fun stackLocation(): ProblemBuilderInternal {
        this.collectStackLocation = true
        return this
    }

    override fun diagnostics(diagnostics: ProblemDiagnostics): ProblemSpecInternal {
        this.diagnostics = diagnostics
        return this
    }

    override fun details(details: String): ProblemBuilderInternal {
        this.details = details
        return this
    }

    override fun documentedAt(doc: DocLink?): ProblemBuilderInternal {
        this.docLink = doc
        return this
    }

    override fun id(problemId: ProblemId): ProblemBuilderInternal {
        if (problemId is DefaultProblemId) {
            this.id = problemId
        } else {
            this.id = cloneId(problemId)
        }
        return this
    }

    override fun id(name: String, displayName: String, parent: ProblemGroup): ProblemBuilderInternal {
        this.id = ProblemId.create(name, displayName, cloneGroup(parent))
        return this
    }

    override fun documentedAt(url: String): ProblemBuilderInternal {
        this.docLink = DefaultDocLink(url)
        return this
    }


    override fun solution(solution: String): ProblemBuilderInternal {
        if (this.solutions == null) {
            this.solutions = ArrayList<String>()
        }
        this.solutions.add(solution)
        return this
    }

    override fun <U : AdditionalDataSpec?> additionalDataInternal(specType: Class<out U>, config: Action<in U?>): ProblemBuilderInternal {
        if (problemsInfrastructure.additionalDataBuilderFactory!!.hasProviderForSpec(specType)) {
            val additionalDataBuilder = problemsInfrastructure.additionalDataBuilderFactory.createAdditionalDataBuilder(specType, additionalData)
            config.execute(additionalDataBuilder as U)
            additionalData = additionalDataBuilder.build()
        } else {
            additionalData = UnsupportedAdditionalDataSpec(specType)
        }
        return this
    }

    override fun <T : AdditionalData> additionalData(type: Class<T>, config: Action<in T>): ProblemBuilderInternal {
        val additionalDataInstance = createAdditionalData(type, config)
        val isolated = problemsInfrastructure.isolatableFactory!!.isolate<AdditionalData>(additionalDataInstance)

        val serializedBaseClass = problemsInfrastructure.payloadSerializer!!.serialize(type)
        val serialized = this.problemsInfrastructure.isolatableSerializer!!.serialize(isolated)!!

        this.additionalData = DefaultTypedAdditionalData(serializedBaseClass, serialized)
        return this
    }

    private fun <T : AdditionalData> createAdditionalData(type: Class<T>, config: Action<in T>): AdditionalData {
        val additionalDataInstance = problemsInfrastructure.instantiator!!.newInstance(type)
        config.execute(additionalDataInstance)
        return additionalDataInstance
    }

    override fun withException(t: Throwable): ProblemBuilderInternal {
        this.exception = t
        return this
    }

    fun getId(): ProblemId {
        return id!!
    }

    private class UnsupportedAdditionalDataSpec(val type: Class<*>) : AdditionalData
    companion object {
        private fun getDefaultPluginIdLocation(problemDiagnostics: ProblemDiagnostics): PluginIdLocation? {
            val source = problemDiagnostics.getSource()
            if (source == null) {
                return null
            }
            if (source !is UserCodeSource.Binary) {
                return null
            }
            val pluginId = source.getPluginId()
            if (pluginId == null) {
                return null
            }
            return DefaultPluginIdLocation(pluginId)
        }

        private fun getFileLocation(loc: Location): FileLocation {
            val path = loc.filePath
            val line = loc.lineNumber
            return DefaultLineInFileLocation.Companion.from(path, line)
        }

        private fun cloneId(original: ProblemId): ProblemId {
            return ProblemId.create(original.getName()!!, original.getDisplayName()!!, cloneGroup(original.getGroup()!!))
        }

        private fun cloneGroup(original: ProblemGroup): ProblemGroup {
            return ProblemGroup.create(original.getName()!!, original.getDisplayName()!!, if (original.getParent() == null) null else Companion.cloneGroup(original.getParent()!!))
        }
    }
}
