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

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.api.GradleException
import org.gradle.api.internal.exceptions.MarkedVerificationException
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate
import org.gradle.api.plugins.internal.ant.AntWorkAction
import org.gradle.api.specs.Spec
import org.gradle.internal.Cast
import org.gradle.internal.Factory
import org.gradle.internal.SystemProperties
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.util.internal.GUtil
import org.gradle.util.internal.VersionNumber
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.function.Consumer

abstract class PmdInvoker : AntWorkAction<PmdActionParameters>() {
    override fun getActionName(): String {
        return "pmd"
    }

    public override fun execute(ant: AntBuilderDelegate) {
        val parameters = getParameters()
        val pmdClasspath = parameters.getPmdClasspath().filter(FileExistFilter())

        // PMD uses java.class.path to determine it's implementation classpath for incremental analysis
        // Since we run PMD inside the Gradle daemon, this pulls in all of Gradle's runtime.
        // To hide this from PMD, we override the java.class.path to just the PMD classpath from Gradle's POV.
        if (parameters.getIncrementalAnalysis().get()) {
            // TODO: Can we get rid of this now that we're running in a worker?
            SystemProperties.getInstance().withSystemProperty<Void?>("java.class.path", GUtil.asPath(pmdClasspath), org.gradle.internal.Factory {
                runPmd(ant, parameters)
                null
            } as Factory<Void?>)
        } else {
            runPmd(ant, parameters)
        }
    }

    private class FileExistFilter : Spec<File?> {
        override fun isSatisfiedBy(element: File): Boolean {
            return element.exists()
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(PmdInvoker::class.java)

        private val BASIC: MutableList<String?> = ImmutableList.of<String?>("basic")
        private val JAVA_BASIC: MutableList<String?> = ImmutableList.of<String?>("java-basic")
        private val ERROR_PRONE: MutableList<String?> = ImmutableList.of<String?>("category/java/errorprone.xml")

        private fun runPmd(ant: AntBuilderDelegate, parameters: PmdActionParameters) {
            val version: VersionNumber = determinePmdVersion(Thread.currentThread().getContextClassLoader())

            val antPmdArgs: MutableMap<String?, Any?> = HashMap<String?, Any?>(
                ImmutableMap.builder<String?, Any?>()
                    .put("failOnRuleViolation", false)
                    .put("failuresPropertyName", "pmdFailureCount")
                    .put("minimumPriority", parameters.getRulesMinimumPriority().get())
                    .build()
            )

            var ruleSets: MutableList<String?> = parameters.getRuleSets().get()
            var htmlFormat = "html"
            if (version.compareTo(VersionNumber.parse("5.0.0")) < 0) {
                // <5.x
                // NOTE: PMD 5.0.2 apparently introduces an element called "language" that serves the same purpose
                // http://sourceforge.net/p/pmd/bugs/1004/
                // http://java-pmd.30631.n5.nabble.com/pmd-pmd-db05bc-pmd-AntTask-support-for-language-td5710041.html
                antPmdArgs.put("targetjdk", parameters.getTargetJdk().get().getName())

                htmlFormat = "betterhtml"

                // fallback to basic on pre 5.0 for backwards compatible
                if (ruleSets == JAVA_BASIC || ruleSets == ERROR_PRONE) {
                    ruleSets = BASIC
                }
                if (parameters.getIncrementalAnalysis().get()) {
                    assertUnsupportedIncrementalAnalysis(version)
                }
            } else if (version.compareTo(VersionNumber.parse("6.0.0")) < 0) {
                // 5.x
                if (ruleSets == ERROR_PRONE) {
                    ruleSets = JAVA_BASIC
                }
                if (parameters.getIncrementalAnalysis().get()) {
                    assertUnsupportedIncrementalAnalysis(version)
                }
                antPmdArgs.put("threads", parameters.getThreads().get())
            } else {
                // 6.+
                if (parameters.getIncrementalAnalysis().get()) {
                    antPmdArgs.put("cacheLocation", parameters.getIncrementalCacheFile().get().getAsFile())
                } else {
                    if (version.compareTo(VersionNumber.parse("6.2.0")) >= 0) {
                        antPmdArgs.put("noCache", true)
                    }
                }
                antPmdArgs.put("threads", parameters.getThreads().get())
            }

            for (report in parameters.getEnabledReports().get()) {
                if (report.getName().get() == "sarif") {
                    assertSarifReportSupportedByPmdVersion(version)
                } else if (report.getName().get() == "codeClimate") {
                    assertCodeClimateReportSupportedByPmdVersion(version)
                }
            }

            val maxFailures = parameters.getMaxFailures().get()
            if (maxFailures < 0) {
                throw GradleException(String.format("Invalid maxFailures %s. Valid range is >= 0.", maxFailures))
            }

            val finalHtmlFormat = htmlFormat
            val finalRuleSets = ruleSets
            val reports: MutableList<PmdActionParameters.EnabledReport?> = parameters.getEnabledReports().get()
            ant.taskdef("pmd", "net.sourceforge.pmd.ant.PMDTask")
            ant.createNode("pmd", antPmdArgs, Runnable {
                ant.addDirectoryTrees("fileset", (parameters.getSource() as FileCollectionInternal).getAsDirectoryTrees())
                finalRuleSets.forEach(Consumer { rule: String? -> ant.createNode("ruleset", rule) })
                parameters.getRuleSetConfigFiles().forEach(Consumer { ruleSetConfig: File? -> ant.createNode("ruleset", ruleSetConfig!!.getAbsolutePath()) })

                val auxClasspath = parameters.getAuxClasspath().filter(FileExistFilter())
                if (!auxClasspath.isEmpty()) {
                    ant.addFiles("auxclasspath", auxClasspath)
                }

                reports.forEach(Consumer { report: PmdActionParameters.EnabledReport? ->
                    val file = report!!.getOutputLocation().getAsFile().get()
                    Preconditions.checkArgument(file.getParentFile().exists(), "Parent directory of report file '" + file + "' does not exist.")
                    val type: String?
                    val name = report.getName().get()
                    when (name) {
                        "html" -> type = finalHtmlFormat
                        "codeClimate" -> type = "codeclimate"
                        else -> type = name
                    }
                    ant.createNode("formatter", ImmutableMap.of<String?, Any?>("type", type, "toFile", file))
                })
                if (parameters.getConsoleOutput().get()) {
                    var consoleOutputType = "text"
                    if (parameters.getStdOutIsAttachedToTerminal().get()) {
                        consoleOutputType = "textcolor"
                    }
                    ant.setSaveStreams(false)
                    ant.createNode("formatter", ImmutableMap.of<String?, Any?>("type", consoleOutputType, "toConsole", true))
                }
            })
            val failureCount = ant.getProjectProperties().get("pmdFailureCount") as String?
            if (failureCount != null) {
                var message = String.format("%s PMD rule violations were found.", failureCount)
                val report = if (reports.isEmpty()) null else reports.get(0)
                if (report != null) {
                    val reportUrl = ConsoleRenderer().asClickableFileUrl(report.getOutputLocation().getAsFile().get())
                    message += " See the report at: " + reportUrl
                }
                if (parameters.getIgnoreFailures().get() || failureCount.toInt() <= maxFailures) {
                    LOGGER.warn(message)
                } else {
                    throw MarkedVerificationException(message)
                }
            }
        }

        private fun determinePmdVersion(antLoader: ClassLoader): VersionNumber {
            var pmdVersion: Class<*>
            try {
                pmdVersion = antLoader.loadClass("net.sourceforge.pmd.PMDVersion")
            } catch (e: ClassNotFoundException) {
                try {
                    pmdVersion = antLoader.loadClass("net.sourceforge.pmd.PMD")
                } catch (ex: ClassNotFoundException) {
                    throw RuntimeException(ex)
                }
            }
            try {
                val versionField = pmdVersion.getDeclaredField("VERSION")
                return VersionNumber.parse(Cast.castNullable<String?, Any?>(String::class.java, versionField.get(null)))
            } catch (e: NoSuchFieldException) {
                throw RuntimeException(e)
            } catch (e: IllegalAccessException) {
                throw RuntimeException(e)
            }
        }

        private fun assertUnsupportedIncrementalAnalysis(version: VersionNumber?) {
            throw GradleException("Incremental analysis only supports PMD 6.0.0 and newer. Please upgrade from PMD " + version + " or disable incremental analysis.")
        }

        private fun assertSarifReportSupportedByPmdVersion(version: VersionNumber) {
            if (version.compareTo(VersionNumber.parse("6.31.0")) < 0) {
                // https://github.com/pmd/pmd/releases/tag/pmd_releases%2F6.31.0
                throw GradleException("The Sarif output format is only supported by PMD 6.31.0 and newer. Please upgrade from PMD " + version + " or disable the 'sarif' report.")
            }
        }

        private fun assertCodeClimateReportSupportedByPmdVersion(version: VersionNumber) {
            if (version.compareTo(VersionNumber.parse("5.3.7")) < 0) {
                // https://github.com/pmd/pmd/releases/tag/pmd_releases%2F5.3.7
                throw GradleException("The CodeClimate output format is only supported by PMD 5.3.7 and newer. Please upgrade from PMD " + version + " or disable the 'codeClimate' report.")
            }
        }
    }
}
