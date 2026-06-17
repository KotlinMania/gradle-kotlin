/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.plugins.quality.internal

import com.google.common.collect.ImmutableMap
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.exceptions.MarkedVerificationException
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.project.ant.AntLoggingAdapter
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate
import org.gradle.api.plugins.internal.ant.AntWorkAction
import org.gradle.internal.logging.ConsoleRenderer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.util.function.Consumer
import java.util.stream.Collectors

abstract class CodeNarcInvoker : AntWorkAction<CodeNarcActionParameters>() {
    override fun getActionName(): String {
        return "codenarc"
    }

    public override fun execute(ant: AntBuilderDelegate) {
        val parameters = getParameters()
        val compilationClasspath: FileCollection = parameters.getCompilationClasspath()
        val configFile = parameters.getConfig().get()
        val maxPriority1Violations = parameters.getMaxPriority1Violations().get()
        val maxPriority2Violations = parameters.getMaxPriority2Violations().get()
        val maxPriority3Violations = parameters.getMaxPriority3Violations().get()
        val reports: MutableList<CodeNarcActionParameters.EnabledReport?> = parameters.getEnabledReports().get()
        val ignoreFailures = parameters.getIgnoreFailures().get()
        val source: FileCollection = parameters.getSource()

        setLifecycleLogLevel(ant, null)
        ant.taskdef("codenarc", "org.codenarc.ant.CodeNarcTask")
        try {
            ant.createNode(
                "codenarc",
                ImmutableMap.of<String?, Any?>(
                    "ruleSetFiles", "file:" + configFile,
                    "maxPriority1Violations", maxPriority1Violations,
                    "maxPriority2Violations", maxPriority2Violations,
                    "maxPriority3Violations", maxPriority3Violations
                ),
                Runnable {
                    reports.forEach(Consumer { r: CodeNarcActionParameters.EnabledReport? ->
                        // See https://codenarc.org/codenarc-text-report-writer.html
                        if (r!!.getName().get() == "console") {
                            // The output from Ant is written at INFO level
                            setLifecycleLogLevel(ant, "INFO")

                            // Prefer to use the IDE based formatter because this produces a useful/clickable link to the violation on the console
                            ant.createNode(
                                "report",
                                ImmutableMap.of<String?, Any?>("type", "ide"),
                                Runnable { ant.createNode("option", ImmutableMap.of<String?, Any?>("name", "writeToStandardOut", "value", true)) }
                            )
                        } else if (r.getName().get() == "html") {
                            ant.createNode(
                                "report",
                                ImmutableMap.of<String?, Any?>("type", "sortable"),
                                Runnable { ant.createNode("option", ImmutableMap.of<String?, Any?>("name", "outputFile", "value", r.getOutputLocation().getAsFile().get())) }
                            )
                        } else {
                            ant.createNode(
                                "report",
                                ImmutableMap.of<String?, Any?>("type", r.getName().get()),
                                Runnable { ant.createNode("option", ImmutableMap.of<String?, Any?>("name", "outputFile", "value", r.getOutputLocation().getAsFile().get())) }
                            )
                        }
                    })
                    ant.addDirectoryTrees("fileset", (source as FileCollectionInternal).getAsDirectoryTrees())
                    if (!compilationClasspath.isEmpty()) {
                        ant.addFiles("classpath", compilationClasspath)
                    }
                })
        } catch (e: Exception) {
            if (e.message!!.matches("Exceeded maximum number of priority \\d* violations.*".toRegex())) {
                var message = "CodeNarc rule violations were found."

                // Find all reports that produced a file
                val reportsWithFiles = reports.stream()
                    .filter { it: CodeNarcActionParameters.EnabledReport? -> it!!.getName().get() != "console" }
                    .collect(Collectors.toList())
                // a report file was generated
                if (!reportsWithFiles.isEmpty()) {
                    var humanReadableReport = reportsWithFiles.stream().filter { it: CodeNarcActionParameters.EnabledReport? -> it!!.getName().get() == "html" }
                        .findFirst()
                        .orElse(null)
                    if (humanReadableReport == null) {
                        humanReadableReport = reportsWithFiles.stream().filter { it: CodeNarcActionParameters.EnabledReport? -> it!!.getName().get() == "text" }
                            .findFirst()
                            .orElse(null)
                    }
                    if (humanReadableReport == null) {
                        humanReadableReport = reportsWithFiles.stream().filter { it: CodeNarcActionParameters.EnabledReport? -> it!!.getName().get() == "xml" }
                            .findFirst()
                            .orElse(null)
                    }
                    // Prefer HTML > text > XML and don't include a link if we don't recognize the report format
                    if (humanReadableReport != null) {
                        val reportUrl = ConsoleRenderer().asClickableFileUrl(humanReadableReport.getOutputLocation().getAsFile().get())
                        message += " See the report at: " + reportUrl
                    }
                }

                if (ignoreFailures) {
                    LOGGER.warn(message)
                    return
                }
                throw MarkedVerificationException(message, e)
            }
            if (e.message!!.contains("codenarc doesn't support the nested \"classpath\" element.")) {
                val message = "The compilationClasspath property of CodeNarc task can only be non-empty when using CodeNarc 0.27.0 or newer."
                throw GradleException(message, e)
            }
            throw e
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(CodeNarcInvoker::class.java)

        fun setLifecycleLogLevel(ant: AntBuilderDelegate, lifecycleLogLevel: String?) {
            try {
                val buildListeners = ant.getProject().invokeMethod("getBuildListeners") as MutableList<Any>?
                for (it in buildListeners!!) {
                    // We cannot use instanceof or getClass().equals(AntLoggingAdapter.class) since they're in different class loaders
                    if (it.javaClass.getName() == AntLoggingAdapter::class.java.getName()) {
                        it.javaClass.getMethod("setLifecycleLogLevel", String::class.java).invoke(it, lifecycleLogLevel)
                    }
                }
            } catch (e: IllegalAccessException) {
                throw RuntimeException(e)
            } catch (e: InvocationTargetException) {
                throw RuntimeException(e)
            } catch (e: NoSuchMethodException) {
                throw RuntimeException(e)
            }
        }
    }
}
