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
package org.gradle.api.internal.tasks.compile

import com.google.common.annotations.VisibleForTesting
import com.sun.tools.javac.api.ClientCodeWrapper
import com.sun.tools.javac.api.DiagnosticFormatter
import com.sun.tools.javac.util.Context
import com.sun.tools.javac.util.JCDiagnostic
import com.sun.tools.javac.util.JavacMessages
import com.sun.tools.javac.util.Log
import org.gradle.api.Action
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.problems.Problem
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.internal.GradleCoreProblemGroup.compilation
import org.gradle.api.problems.internal.ProblemReporterInternal
import java.util.Collections
import java.util.Locale
import java.util.Optional
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.tools.Diagnostic
import javax.tools.DiagnosticListener
import javax.tools.JavaFileObject

/**
 * A [DiagnosticListener] that consumes [Diagnostic] messages, and reports them as Gradle [Problems].
 */
// If this annotation is not present, all diagnostic messages would be wrapped in a ClientCodeWrapper.
// We don't need this wrapping feature, hence the trusted annotation.
@ClientCodeWrapper.Trusted
class DiagnosticToProblemListener(private val problemReporter: ProblemReporterInternal, private val context: Context) : DiagnosticListener<JavaFileObject?> {
    private val problemsReported: MutableList<Problem?> = ArrayList<Problem?>()

    private var errorCount = 0
    private var warningCount = 0

    override fun report(diagnostic: Diagnostic<out JavaFileObject?>) {
        when (diagnostic.getKind()) {
            Diagnostic.Kind.ERROR -> errorCount++
            Diagnostic.Kind.WARNING, Diagnostic.Kind.MANDATORY_WARNING -> warningCount++
            else -> {}
        }

        val reportedProblem = problemReporter.create(id(diagnostic), Action { spec: ProblemSpec? -> buildProblem(diagnostic, spec!!) })
        problemsReported.add(reportedProblem)
    }

    /**
     * This method is responsible for printing the number of errors and warnings after writing the diagnostics out to the console.
     * This count is normally printed by the compiler itself, but when a [DiagnosticListener] is registered, the compiled will stop reporting the number of errors and warnings.
     *
     * An example output with the last two lines being the count:
     * <pre>
     * /.../src/main/java/Foo.java:10: error: ';' expected
     * String s = "Hello, World!"
     * ^
     * /.../Bar.java:10: warning: [cast] redundant cast to String
     * String s = (String)"Hello World";
     * ^
     * 1 error
     * 1 warning
    </pre> *
     *
     * @see com.sun.tools.javac.main.JavaCompiler.printCount
     */
    fun diagnosticCounts(): String {
        val logger = Log.instance(Context())
        val error: Optional<String?> = diagnosticCount(logger, "error", errorCount)
        val warning: Optional<String?> = diagnosticCount(logger, "warn", warningCount)

        return Stream.of<Optional<String?>?>(error, warning)
            .filter { obj: Optional<String?>? -> obj!!.isPresent() }
            .map<String?> { obj: Optional<String?>? -> obj!!.get() }
            .collect(Collectors.joining(System.lineSeparator()))
    }

    @VisibleForTesting
    fun buildProblem(diagnostic: Diagnostic<out JavaFileObject?>, spec: ProblemSpec) {
        maybeAddSolution(diagnostic, spec)
        addLocations(diagnostic, spec)

        val label = toFormattedLabel(diagnostic)
        addContextualLabel(label, spec)

        val details = toFormattedDetails(diagnostic)
        // We cannot be sure that the compiler makes us a message, hence the defensiveness
        if (details != null) {
            addDetails(details, spec)
            // NOTE: This is required to keep backward compatibility
            // By default, when a compiler is called without a diagnostic listener
            // the compiler will print the diagnostic message to the error stream
            System.err.println(details)
        }
    }

    /**
     * Using a [DiagnosticFormatter], turns a diagnostic into a human-readable multi-line message.
     *
     *
     * This method uses an internal Java compiler API to get a formatter.
     *
     *
     * In some circumstances, getting the formatter can fail, after which we would use a
     * fail-safe way of formatting the message.
     * The drawback is that the formatters are not equal. Normally, we would get a `RichDiagnosticFormatter`
     * instance that can simplify types, generics, and is the formatter normally used by `javac`.
     *
     *
     * The failsafe (normally `BasicDiagnosticFormatter`, however, uses a much simpler algorithm to format the message,
     * and will make a different, more terse message.
     */
    private fun toFormattedDetails(diagnostic: Diagnostic<out JavaFileObject?>): String? {
        try {
            val formatter = Log.instance(context).getDiagnosticFormatter()
            // Note: this method uses a different formatter than #toFormattedLabel
            return formatter.format(diagnostic as JCDiagnostic?, JavacMessages.instance(context).getCurrentLocale())
        } catch (ex: Exception) {
            LOGGER!!.info(FORMATTER_FALLBACK_MESSAGE)
            return diagnostic.toString()
        }
    }

    /**
     * Using a [DiagnosticFormatter], turns a diagnostic into a human-readable label.
     *
     *
     * This method uses an internal Java compiler API to get a formatter.
     *
     *
     * In some circumstances, getting the formatter can fail, after which we would use a
     * fail-safe way of formatting the message.
     * The drawback is that the formatters are not equal. Normally, we would get a `RichDiagnosticFormatter`
     * instance that can simplify types, generics, and is the formatter normally used by `javac`.
     *
     *
     * The failsafe (normally `BasicDiagnosticFormatter`, however, uses a much simpler algorithm to format the message,
     * and will make a different, more terse message.
     */
    private fun toFormattedLabel(diagnostic: Diagnostic<out JavaFileObject?>): String {
        try {
            val formatter = Log.instance(context).getDiagnosticFormatter()
            // Note: this method uses a different formatter than #toFormattedDetails
            return formatter.formatMessage(diagnostic as JCDiagnostic?, JavacMessages.instance(context).getCurrentLocale())
        } catch (ex: Exception) {
            LOGGER!!.info(FORMATTER_FALLBACK_MESSAGE)
            return diagnostic.getMessage(Locale.getDefault())
        }
    }

    val reportedProblems: MutableList<Problem?>
        get() = Collections.unmodifiableList<Problem?>(problemsReported)

    companion object {
        const val FORMATTER_FALLBACK_MESSAGE: String = "Failed to format diagnostic message, falling back to default message formatting"
        private val LOGGER = getLogger(DiagnosticToProblemListener::class.java)

        private fun id(diagnostic: Diagnostic<out JavaFileObject?>): ProblemId {
            val code = diagnostic.getCode()
            val message = diagnostic.getMessage(Locale.getDefault())
            return ProblemId.create(
                if (code == null) "unknown" else code,
                if (message == null) "unknown" else message,
                compilation().java()!!
            )
        }

        /**
         * Formats and prints the number of diagnostics of a given kind.
         *
         *
         * E.g.:
         * <pre>
         * 1 error
         * 2 warnings
        </pre> *
         *
         * @param logger the logger used to localize the message
         * @param kind the kind of diagnostic (error, or warn)
         * @param number the total number of diagnostics of the given kind
         * @return the human-readable count of diagnostics of the given kind, or `#Optional.empty()` if there are no diagnostics of the given kind
         */
        private fun diagnosticCount(logger: Log, kind: String?, number: Int): Optional<String?> {
            // Compiler only handles 'error' and 'warn' kinds
            require("error" == kind || "warn" == kind) { "kind must be either 'error' or 'warn'" }
            // If there are no diagnostics of this kind, we don't need to print anything
            if (number == 0) {
                return Optional.empty<String?>()
            }

            // See the distributions' respective `compiler.java` files to see the keys used for localization.
            // We are using the following keys:
            //  - count.error and count.error.plural
            //  - count.warn and count.warn.plural
            val keyBuilder = StringBuilder("count.")
            keyBuilder.append(kind)
            if (number > 1) {
                keyBuilder.append(".plural")
            }

            return Optional.of<String?>(logger.localize(keyBuilder.toString(), number))
        }

        /**
         * Adds a contextual label to the problem spec.
         *
         *
         * This method will sanitize the label by splitting it into lines, and only using the first line.
         *
         * @param label the label to add
         * @param spec the problem spec to add the label to
         */
        private fun addContextualLabel(label: String, spec: ProblemSpec) {
            val lines: Array<String?> = label.split(System.lineSeparator().toRegex(), limit = 2).toTypedArray()
            spec.contextualLabel(lines[0])
        }

        private fun addDetails(formattedMessage: String?, spec: ProblemSpec) {
            spec.details(formattedMessage)
        }

        private fun maybeAddSolution(diagnostic: Diagnostic<out JavaFileObject?>, spec: ProblemSpec) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                spec.solution(CompilationFailedException.Companion.RESOLUTION_MESSAGE)
            }
        }

        private fun addLocations(diagnostic: Diagnostic<out JavaFileObject?>, spec: ProblemSpec) {
            val resourceName: String? = if (diagnostic.getSource() != null) getPath(diagnostic.getSource()) else null
            val line: Int = clampLocation(diagnostic.getLineNumber())
            val column: Int = clampLocation(diagnostic.getColumnNumber())
            val position: Int = clampLocation(diagnostic.getPosition())
            val end: Int = clampLocation(diagnostic.getEndPosition())

            // We only set the location if we have a resource to point to
            if (resourceName != null) {
                // If we know the line ...
                if (Diagnostic.NOPOS != line.toLong()) {
                    // ... and the column ...
                    if (Diagnostic.NOPOS != column.toLong()) {
                        // ... and we know how long the error is (i.e. end - start)
                        // (documentation says that getEndPosition() will be NOPOS if and only if the getPosition() is NOPOS)
                        if (Diagnostic.NOPOS != position.toLong()) {
                            // ... we can report the line, column, and extent ...
                            spec.lineInFileLocation(resourceName, line, column, end - position)
                        } else {
                            // ... otherwise we can still report the line and column
                            spec.lineInFileLocation(resourceName, line, column)
                        }
                    } else {
                        // ... otherwise we can still report the line
                        spec.lineInFileLocation(resourceName, line)
                    }
                } else  // If we know the offsets ...
                // (offset doesn't require line and column to be set, hence the separate check)
                // (documentation says that getEndPosition() will be NOPOS iff getPosition() is NOPOS)
                    if (Diagnostic.NOPOS != position.toLong() && end > position) {
                        // ... we can report the start and extent
                        spec.offsetInFileLocation(resourceName, position, end - position)
                    } else {
                        spec.fileLocation(resourceName)
                    }
            }
        }

        /**
         * Clamp the value to an int, or return [Diagnostic.NOPOS] if the value is too large.
         *
         *
         * This is used to ensure that we don't report invalid locations.
         *
         * @param value the value to clamp
         * @return either the clamped value, or [Diagnostic.NOPOS]
         */
        private fun clampLocation(value: Long): Int {
            if (value > Int.MAX_VALUE) {
                return Math.toIntExact(Diagnostic.NOPOS)
            } else {
                return value.toInt()
            }
        }

        private fun getPath(fileObject: JavaFileObject): String? {
            return fileObject.getName()
        }
    }
}
