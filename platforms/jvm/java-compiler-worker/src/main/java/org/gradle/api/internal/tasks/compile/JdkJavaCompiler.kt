/*
 * Copyright 2011 the original author or authors.
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

import com.sun.tools.javac.util.Context
import org.gradle.api.JavaVersion.Companion.current
import org.gradle.api.internal.tasks.compile.reflect.GradleStandardJavaFileManager
import org.gradle.api.problems.Problem
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.internal.GradleCoreProblemGroup.compilation
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.api.tasks.WorkResult
import org.gradle.internal.Factory
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.language.base.internal.compile.Compiler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Serializable
import java.nio.charset.Charset
import java.util.Objects
import java.util.Optional
import java.util.function.Function
import java.util.stream.Collectors
import javax.inject.Inject
import javax.tools.JavaCompiler

class JdkJavaCompiler @Inject constructor(
    private val compilerFactory: Factory<ContextAwareJavaCompiler?>,
    problemsService: ProblemsInternal
) : Compiler<JavaCompileSpec?>, Serializable {
    private val context: Context
    private val problemsService: ProblemsInternal?
    private val diagnosticToProblemListener: DiagnosticToProblemListener

    init {
        this.context = Context()
        this.problemsService = problemsService
        this.diagnosticToProblemListener = DiagnosticToProblemListener(problemsService.internalReporter, context)
    }

    override fun execute(spec: JavaCompileSpec): WorkResult {
        LOGGER.info("Compiling with JDK Java compiler API.")

        val result = ApiCompilerResult()
        val task: JavaCompiler.CompilationTask
        try {
            task = createCompileTask(spec, result)
        } catch (ex: RuntimeException) {
            val id = ProblemId.create("initialization-failed", "Java compilation initialization error", compilation().java()!!)
            throw problemsService!!.internalReporter!!.throwing(ex, id, { builder ->
                Companion.buildProblemFrom(ex, builder!!)
            })
        }
        val success = task.call()
        val diagnosticCounts = diagnosticToProblemListener.diagnosticCounts()
        if ("" != diagnosticCounts) {
            System.err.println(diagnosticCounts)
        }
        if (!success) {
            val exception =
                CompilationFailedException(
                    result, diagnosticToProblemListener.getReportedProblems().stream().map<ProblemInternal?> { obj: Problem? -> ProblemInternal::class.java.cast(obj) }.collect(
                        Collectors.toList()
                    ), diagnosticCounts
                )
            throw problemsService!!.internalReporter!!.throwing(exception, diagnosticToProblemListener.getReportedProblems())
        } else {
            problemsService!!.internalReporter!!.report(diagnosticToProblemListener.getReportedProblems())
        }
        return result
    }

    private fun createCompileTask(spec: JavaCompileSpec, result: ApiCompilerResult): JavaCompiler.CompilationTask {
        val options = JavaCompilerArgumentsBuilder(spec).build()
        val compiler = compilerFactory.create()
        Objects.requireNonNull<ContextAwareJavaCompiler?>(compiler, "Compiler factory returned null compiler")

        val compileOptions = spec.getCompileOptions()
        val charset = Optional.ofNullable<String?>(compileOptions.getEncoding())
            .map<Charset?>(Function { charsetName: String? -> Charset.forName(charsetName) })
            .orElse(null)
        val standardFileManager = compiler!!.getStandardFileManager(diagnosticToProblemListener, null, charset)

        val compilationUnits = standardFileManager.getJavaFileObjectsFromFiles(spec.getSourceFiles())
        val hasEmptySourcepaths = current()!!.isJava9Compatible && emptySourcepathIn(options)
        val fileManager = GradleStandardJavaFileManager.wrap(standardFileManager, DefaultClassPath.of(spec.getAnnotationProcessorPath()), hasEmptySourcepaths)

        var task: JavaCompiler.CompilationTask = compiler.getTask(null, fileManager, diagnosticToProblemListener, options, spec.getClassesToProcess(), compilationUnits, context)
        if (compiler is IncrementalCompilationAwareJavaCompiler) {
            task = compiler.makeIncremental(
                task,
                result.getSourceClassesMapping(),
                result.getConstantsAnalysisResult(),
                CompilationSourceDirs(spec),
                CompilationClassBackupService(spec, result)
            )
        }
        val annotationProcessors = spec.getEffectiveAnnotationProcessors()
        task = AnnotationProcessingCompileTask(task, annotationProcessors, spec.getAnnotationProcessorPath(), result.getAnnotationProcessingResult())
        task = ResourceCleaningCompilationTask(task, fileManager)
        return task
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(JdkJavaCompiler::class.java)

        private fun emptySourcepathIn(options: MutableList<String?>): Boolean {
            val optionsIter: MutableIterator<String> = options.iterator()
            while (optionsIter.hasNext()) {
                val current = optionsIter.next()
                if (current == "-sourcepath" || current == "--source-path") {
                    return optionsIter.next().isEmpty()
                }
            }
            return false
        }

        private fun buildProblemFrom(ex: RuntimeException, spec: ProblemSpec) {
            spec.contextualLabel(ex.getLocalizedMessage())
            spec.withException(ex)
        }

        @JvmStatic
        fun canBeUsed(): Boolean {
            try {
                // Our goal is to check if the class is instantiable
                // Class loading alone doesn't generate an exception
                Context()
            } catch (e: IllegalAccessError) {
                LOGGER.debug("Expected failure when checking class presence: {}", e.message)
                return false
            } catch (throwable: Throwable) {
                // We don't expect any other exception
                // Regardless, to make this as robust as possible, we handle it
                LOGGER.debug("Unexpected failure when checking class presence: {}", throwable.message)
                return false
            }

            return true
        }
    }
}
