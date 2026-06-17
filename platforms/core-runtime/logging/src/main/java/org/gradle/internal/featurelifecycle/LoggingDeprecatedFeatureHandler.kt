/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.internal.featurelifecycle

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.api.problems.Problem
import org.gradle.api.problems.Problems
import org.gradle.api.problems.internal.DeprecationDataSpec
import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.api.problems.internal.ProblemSpecInternal
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.internal.SystemProperties
import org.gradle.internal.deprecation.DeprecatedFeatureUsage
import org.gradle.internal.deprecation.DeprecationMessageBuilder.Companion.createDefaultDeprecationId
import org.gradle.internal.logging.LoggingConfigurationBuildOptions
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory
import org.gradle.problems.ProblemDiagnostics
import org.gradle.problems.buildtree.ProblemStream
import org.gradle.util.internal.DefaultGradleVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.min

class LoggingDeprecatedFeatureHandler : FeatureHandler<DeprecatedFeatureUsage> {
    private val loggedMessages: MutableSet<String> = CopyOnWriteArraySet<String>()
    private val loggedUsages: MutableSet<String> = CopyOnWriteArraySet<String>()
    private var deprecationsFound = false
    private var problemStream = NoOpProblemDiagnosticsFactory.EMPTY_STREAM

    private var warningMode = WarningMode.Summary
    private var progressEventEmitter: BuildOperationProgressEventEmitter? = null
    private var problemsService: Problems? = null
    private var error: GradleException? = null

    fun init(warningMode: WarningMode, progressEventEmitter: BuildOperationProgressEventEmitter, problemsService: Problems, problemStream: ProblemStream) {
        this.warningMode = warningMode
        this.problemStream = problemStream
        this.progressEventEmitter = progressEventEmitter
        this.problemsService = problemsService
    }

    override fun featureUsed(usage: DeprecatedFeatureUsage) {
        deprecationsFound = true
        val diagnostics = problemStream.forCurrentCaller(StackTraceSanitizer(usage.getCalledFrom()))
        if (warningMode.shouldDisplayMessages()) {
            maybeLogUsage(usage, diagnostics)
        }
        if (warningMode == WarningMode.Fail) {
            if (error == null) {
                error = GradleException(WARNING_SUMMARY + " " + DefaultGradleVersion.current().getNextMajorVersion().getMajorVersion())
            }
        }
        if (problemsService != null) {
            reportDeprecation(usage, diagnostics)
        }
        fireDeprecatedUsageBuildOperationProgress(usage, diagnostics)
    }

    private fun reportDeprecation(usage: DeprecatedFeatureUsage, diagnostics: ProblemDiagnostics) {
        val reporter = (problemsService as ProblemsInternal).internalReporter
        val problem: Problem = reporter.internalCreate(object : Action<ProblemSpecInternal> {
            override fun execute(builder: ProblemSpecInternal) {
                val problemSpec =
                    builder // usage.getKind() could be part of the problem ID, however it provides hints on the problem provenance which should be modeled differently, maybe as location data.
                        .id(getDefaultDeprecationIdDisplayName(usage), usage.problemIdDisplayName, GradleCoreProblemGroup.deprecation())
                        .contextualLabel(usage.getSummary())
                        .details(usage.removalDetails)
                        .documentedAt(usage.documentationUrl)
                        .diagnostics(diagnostics)
                        .additionalDataInternal<DeprecationDataSpec>(DeprecationDataSpec::class.java, object : Action<DeprecationDataSpec> {
                            override fun execute(data: DeprecationDataSpec) {
                                data.type(usage.type.toDeprecationDataType())
                            }
                        })
                if (usage.type == DeprecatedFeatureUsage.Type.USER_CODE_DIRECT) {
                    builder.stackLocation()
                }
                addSolution(usage.advice, problemSpec)
                addSolution(usage.contextualAdvice, problemSpec)
            }
        })
        reporter.report(problem)
    }

    private fun maybeLogUsage(usage: DeprecatedFeatureUsage, diagnostics: ProblemDiagnostics) {
        val featureMessage = usage.formattedMessage()
        val location = diagnostics.location
        if (!loggedUsages.add(featureMessage) && location == null && diagnostics.stack.isEmpty()) {
            // This usage does not contain any useful diagnostics and the usage has already been logged, so skip it
            return
        }
        val message = StringBuilder()
        if (location != null) {
            message.append(location.getFormatted())
                .append(SystemProperties.getInstance().getLineSeparator())
        }
        message.append(featureMessage)
        if (location != null && !loggedUsages.add(message.toString()) && diagnostics.stack.isEmpty()) {
            // This usage has no stack trace and has already been logged with the same location, so skip it
            return
        }
        displayDeprecationIfSameMessageNotDisplayedBefore(message, diagnostics.stack)
    }

    private fun displayDeprecationIfSameMessageNotDisplayedBefore(message: StringBuilder, callStack: MutableList<StackTraceElement>) {
        // Let's cut the first 10 lines of stack traces as the "key" to identify a deprecation message uniquely.
        // Even when two deprecation messages are emitted from the same location,
        // the stack traces at very bottom might be different due to thread pool scheduling.
        appendLogTraceIfNecessary(message, callStack, 0, 10)
        if (loggedMessages.add(message.toString())) {
            appendLogTraceIfNecessary(message, callStack, 10, callStack.size)
            LOGGER.warn(message.toString())
        }
    }

    private fun fireDeprecatedUsageBuildOperationProgress(usage: DeprecatedFeatureUsage, diagnostics: ProblemDiagnostics) {
        if (progressEventEmitter != null) {
            progressEventEmitter!!.emitNowIfCurrent(DefaultDeprecatedUsageProgressDetails(usage, diagnostics))
        }
    }

    fun reset() {
        problemStream = NoOpProblemDiagnosticsFactory.EMPTY_STREAM
        progressEventEmitter = null
        loggedMessages.clear()
        loggedUsages.clear()
        deprecationsFound = false
        error = null
    }

    fun reportSuppressedDeprecations() {
        if (warningMode == WarningMode.Summary && deprecationsFound) {
            LOGGER.warn(
                "\n{} {}.\n\nYou can use '--{} {}' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.\n\n{}",
                WARNING_SUMMARY, DefaultGradleVersion.current().getNextMajorVersion().getMajorVersion(),
                LoggingConfigurationBuildOptions.WarningsOption.LONG_OPTION, WarningMode.All.name.lowercase(),
                DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor("on this", "command_line_interface", "sec:command_line_warnings")
            )
        }
    }

    val deprecationFailure: GradleException
        get() = error

    companion object {
        const val ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME: String = "org.gradle.deprecation.trace"
        const val WARNING_SUMMARY: String = "Deprecated Gradle features were used in this build, making it incompatible with Gradle"

        private val DOCUMENTATION_REGISTRY = DocumentationRegistry()
        private val LOGGER: Logger = LoggerFactory.getLogger(LoggingDeprecatedFeatureHandler::class.java)
        private const val ELEMENT_PREFIX = "\tat "
        private const val RUN_WITH_STACKTRACE_INFO = "\t(Run with --stacktrace to get the full stack trace of this deprecation warning.)"

        /**
         * Whether or not deprecated features should print a full stack trace.
         *
         * This property can be overridden by setting the ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
         * system property.
         *
         * @param traceLoggingEnabled if trace logging should be enabled.
         */
        var isTraceLoggingEnabled: Boolean = false
            get() {
                val value = System.getProperty(ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME)
                if (value == null) {
                    return field
                }
                return value.toBoolean()
            }

        private fun getDefaultDeprecationIdDisplayName(usage: DeprecatedFeatureUsage): String {
            if (usage.getProblemId() != null) {
                return usage.getProblemId()!!
            }
            return createDefaultDeprecationId(usage.problemIdDisplayName)
        }

        private fun addSolution(advice: String?, problemSpec: ProblemSpecInternal) {
            if (advice != null) {
                problemSpec.solution(advice)
            }
        }

        private fun appendLogTraceIfNecessary(message: StringBuilder, stack: MutableList<StackTraceElement>, startIndexInclusive: Int, endIndexExclusive: Int) {
            val lineSeparator = SystemProperties.getInstance().getLineSeparator()

            val endIndex = min(stack.size, endIndexExclusive)
            if (isTraceLoggingEnabled) {
                // append full stack trace
                for (i in startIndexInclusive..<endIndex) {
                    val frame = stack.get(i)
                    appendStackTraceElement(frame, message, lineSeparator)
                }
            } else {
                for (i in startIndexInclusive..<endIndex) {
                    val element = stack.get(i)
                    if (isGradleScriptElement(element)) {
                        // only print first Gradle script stack trace element
                        appendStackTraceElement(element, message, lineSeparator)
                        appendRunWithStacktraceInfo(message, lineSeparator)
                        return
                    }
                }
            }
        }

        private fun appendStackTraceElement(frame: StackTraceElement, message: StringBuilder, lineSeparator: String) {
            message.append(lineSeparator)
                .append(ELEMENT_PREFIX)
                .append(frame)
        }

        private fun appendRunWithStacktraceInfo(message: StringBuilder, lineSeparator: String) {
            message.append(lineSeparator)
                .append(RUN_WITH_STACKTRACE_INFO)
        }

        private fun isGradleScriptElement(element: StackTraceElement): Boolean {
            var fileName = element.getFileName()
            if (fileName == null) {
                return false
            }
            fileName = fileName.lowercase()
            return fileName.endsWith(".gradle") // ordinary Groovy Gradle script
                    || fileName.endsWith(".gradle.kts") // Kotlin Gradle script
        }
    }
}
