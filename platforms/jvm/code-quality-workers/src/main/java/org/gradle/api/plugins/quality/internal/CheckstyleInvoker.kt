/*
 * Copyright 2021 the original author or authors.
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
import com.google.common.collect.ImmutableMap
import groovy.namespace.QName
import groovy.util.Node
import groovy.xml.XmlParser
import org.apache.commons.io.IOUtils
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.JavaVersion
import org.gradle.api.JavaVersion.Companion.current
import org.gradle.api.JavaVersion.Companion.forClassVersion
import org.gradle.api.internal.exceptions.MarkedVerificationException
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate
import org.gradle.api.plugins.internal.ant.AntWorkAction
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.util.GradleVersion
import org.gradle.util.internal.GFileUtils
import org.gradle.util.internal.VersionNumber
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xml.sax.SAXException
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.function.Function
import java.util.stream.Collectors
import javax.xml.parsers.ParserConfigurationException

abstract class CheckstyleInvoker : AntWorkAction<CheckstyleActionParameters>() {
    override fun getActionName(): String {
        return "checkstyle"
    }

    public override fun execute(ant: AntBuilderDelegate) {
        val parameters = getParameters()
        val source = parameters.getSource().getAsFileTree()
        val showViolations = parameters.getShowViolations().get()
        val maxErrors = parameters.getMaxErrors().get()
        val maxWarnings = parameters.getMaxWarnings().get()
        val configProperties: MutableMap<String?, Any?> = parameters.getConfigProperties().get()
        val ignoreFailures = parameters.getIgnoreFailures().get()
        val config = parameters.getConfig().get()
        val configDir = parameters.getConfigDirectory().getAsFile().get()
        val isXmlRequired = parameters.getIsXmlRequired().get()
        val isHtmlRequired = parameters.getIsHtmlRequired().get()
        val isSarifRequired = parameters.getIsSarifRequired().get()
        val xmlOutputLocation: File? = getXmlOutputLocation(parameters, isXmlRequired, isHtmlRequired)
        val stylesheetString = parameters.getStylesheetString()
        val htmlOutputLocation = parameters.getHtmlOutputLocation().getAsFile().getOrNull()
        val sarifOutputLocation = parameters.getSarifOutputLocation().getAsFile().getOrNull()

        // TODO: Make this a generic feature of all Ant workers
        val checkstyleVersion: VersionNumber = determineCheckstyleVersion(Thread.currentThread().getContextClassLoader())
        val checkstyleJavaVersion: JavaVersion = determineCheckstyleJavaVersion(Thread.currentThread().getContextClassLoader())
        if (!current()!!.isCompatibleWith(checkstyleJavaVersion)) {
            // 10.0 requires Java 11
            // 11.0 requires Java 17
            // 13.0 requires Java 21
            throw UnsupportedWorkerJvmException("Checkstyle", checkstyleVersion)
        }

        // User provided their own config_loc
        val userProvidedConfigLoc = configProperties.get(CONFIG_LOC_PROPERTY)
        if (userProvidedConfigLoc != null) {
            throw InvalidUserDataException("Cannot add config_loc to checkstyle.configProperties. Please configure the configDirectory on the checkstyle task instead.")
        }

        if (isSarifRequired && !isSarifSupported(checkstyleVersion)) {
            assertUnsupportedReportFormatSARIF(checkstyleVersion)
        }

        try {
            ant.taskdef("checkstyle", "com.puppycrawl.tools.checkstyle.CheckStyleTask")
        } catch (ignore: RuntimeException) {
            ant.taskdef("checkstyle", "com.puppycrawl.tools.checkstyle.ant.CheckstyleAntTask")
        }

        try {
            ant.createNode(
                "checkstyle",
                ImmutableMap.of<String?, Any?>(
                    "config", config.getAsFile(),
                    "failOnViolation", false,
                    "maxErrors", maxErrors,
                    "maxWarnings", maxWarnings,
                    "failureProperty", FAILURE_PROPERTY_NAME
                ), Runnable {
                    ant.addDirectoryTrees("fileset", (source as FileCollectionInternal).getAsDirectoryTrees())
                    if (showViolations) {
                        ant.createNode("formatter", ImmutableMap.of<String?, Any?>("type", "plain", "useFile", false))
                    }

                    if (isXmlRequired || isHtmlRequired) {
                        ant.createNode(
                            "formatter", ImmutableMap.of<String?, Any?>(
                                "type", "xml",
                                "toFile", Preconditions.checkNotNull<File?>(xmlOutputLocation, "Xml report output location is required when xml or html report is requested.")
                            )
                        )
                    }

                    if (isSarifRequired) {
                        ant.createNode(
                            "formatter", ImmutableMap.of<String?, Any?>(
                                "type", "sarif",
                                "toFile", Preconditions.checkNotNull<File?>(sarifOutputLocation, "SARIF report output location is required when SARIF report is requested.")
                            )
                        )
                    }

                    configProperties.forEach { (key: String?, value: Any?) -> ant.createNode("property", ImmutableMap.of<String?, Any?>("key", key, "value", value.toString())) }
                    ant.createNode("property", ImmutableMap.of<String?, Any?>("key", CONFIG_LOC_PROPERTY, "value", configDir.toString()))
                })
        } catch (e: Exception) {
            throw CheckstyleInvocationException("An unexpected error occurred configuring and executing Checkstyle.", e)
        }

        if (isHtmlRequired) {
            val stylesheet = (if (stylesheetString.isPresent())
                stylesheetString.get()
            else
                org.gradle.api.plugins.quality.internal.CheckstyleInvoker.Companion.readText(
                    org.gradle.api.plugins.quality.internal.CheckstyleInvoker::class.java.getClassLoader().getResourceAsStream("checkstyle-noframes-sorted.xsl")
                ))!!
            ant.createNode("xslt", ImmutableMap.of<String?, Any?>("in", Preconditions.checkNotNull<File?>(xmlOutputLocation), "out", Preconditions.checkNotNull<File?>(htmlOutputLocation)), Runnable {
                ant.createNode("param", ImmutableMap.of<String?, Any?>("name", "gradleVersion", "expression", GradleVersion.current().toString()))
                ant.createNode("style", mutableMapOf<String?, Any?>(), Runnable { ant.createNode("string", ImmutableMap.of<String?, Any?>("value", stylesheet)) }
                )
            })
        }

        if (isHtmlReportEnabledOnly(isXmlRequired, isHtmlRequired)) {
            GFileUtils.deleteQuietly(xmlOutputLocation)
        }

        val reportXml: Node? = parseCheckstyleXml(isXmlRequired, xmlOutputLocation)
        val message: String = getMessage(isXmlRequired, xmlOutputLocation, isHtmlRequired, htmlOutputLocation, isSarifRequired, sarifOutputLocation, reportXml)
        val hasAFailure = ant.getProjectProperties().get(FAILURE_PROPERTY_NAME) != null
        if (hasAFailure && !ignoreFailures) {
            throw MarkedVerificationException(message)
        } else {
            if (violationsExist(reportXml)) {
                LOGGER.warn(message)
            }
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(CheckstyleInvoker::class.java)

        private const val FAILURE_PROPERTY_NAME = "org.gradle.checkstyle.violations"
        private const val CONFIG_LOC_PROPERTY = "config_loc"

        private fun getXmlOutputLocation(parameters: CheckstyleActionParameters, isXmlRequired: Boolean, isHtmlRequired: Boolean): File? {
            val xmlOutputLocation = parameters.getXmlOutputLocation().getAsFile().getOrNull()
            if (isHtmlReportEnabledOnly(isXmlRequired, isHtmlRequired)) {
                Preconditions.checkNotNull<File?>(xmlOutputLocation, "Xml report output location is required when html report is requested.")
                return File(parameters.getTemporaryDir().getAsFile().get(), xmlOutputLocation!!.getName())
            }
            return xmlOutputLocation
        }

        private fun determineCheckstyleJavaVersion(antLoader: ClassLoader): JavaVersion {
            var checkstyleTask = antLoader.getResourceAsStream("com/puppycrawl/tools/checkstyle/CheckStyleTask.class")
            if (checkstyleTask == null) {
                checkstyleTask = antLoader.getResourceAsStream("com/puppycrawl/tools/checkstyle/ant/CheckstyleAntTask.class")
                requireNotNull(checkstyleTask) { "Could not find checkstyle task class" }
            }
            try {
                DataInputStream(checkstyleTask).use { `in` ->
                    if (`in`.readInt() == -0x35014542) {
                        val unused = `in`.readUnsignedShort()
                        val major = `in`.readUnsignedShort()
                        return forClassVersion(major)!!
                    }
                }
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
            return current()!!
        }

        private fun determineCheckstyleVersion(antLoader: ClassLoader): VersionNumber {
            val `in` = antLoader.getResourceAsStream("META-INF/maven/com.puppycrawl.tools/checkstyle/pom.properties")
            if (`in` == null) {
                var checkstyleTaskClass: Class<*>
                try {
                    checkstyleTaskClass = antLoader.loadClass("com.puppycrawl.tools.checkstyle.CheckStyleTask")
                } catch (e2: ClassNotFoundException) {
                    try {
                        checkstyleTaskClass = antLoader.loadClass("com.puppycrawl.tools.checkstyle.ant.CheckstyleAntTask")
                    } catch (e3: ClassNotFoundException) {
                        throw RuntimeException(e3)
                    }
                }
                return VersionNumber.parse(checkstyleTaskClass.getPackage().getImplementationVersion())
            } else {
                try {
                    val checkstyleProperties = Properties()
                    checkstyleProperties.load(`in`)
                    return VersionNumber.parse(checkstyleProperties.getProperty("version"))
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
        }

        private fun isSarifSupported(versionNumber: VersionNumber): Boolean {
            return versionNumber.compareTo(VersionNumber.parse("10.3.3")) >= 0
        }

        private fun assertUnsupportedReportFormatSARIF(version: VersionNumber?) {
            throw GradleException("SARIF report format is supported on Checkstyle versions 10.3.3 and newer. Please upgrade from Checkstyle " + version + " or disable the SARIF format.")
        }

        private fun parseCheckstyleXml(isXmlRequired: Boolean, xmlOutputLocation: File?): Node? {
            try {
                return if (isXmlRequired) XmlParser().parse(xmlOutputLocation) else null
            } catch (e: IOException) {
                throw RuntimeException(e)
            } catch (e: SAXException) {
                throw RuntimeException(e)
            } catch (e: ParserConfigurationException) {
                throw RuntimeException(e)
            }
        }

        private fun getMessage(
            isXmlRequired: Boolean,
            xmlOutputLocation: File?,
            isHtmlRequired: Boolean,
            htmlOutputLocation: File?,
            isSarifRequired: Boolean,
            sarifOutputLocation: File?,
            reportXml: Node?
        ): String {
            return String.format(
                "Checkstyle rule violations were found.%s%s",
                getReportUrlMessage(isXmlRequired, xmlOutputLocation, isHtmlRequired, htmlOutputLocation, isSarifRequired, sarifOutputLocation),
                getViolationMessage(reportXml)
            )
        }

        private fun getReportUrlMessage(
            isXmlRequired: Boolean,
            xmlOutputLocation: File?,
            isHtmlRequired: Boolean,
            htmlOutputLocation: File?,
            isSarifRequired: Boolean,
            sarifOutputLocation: File?
        ): String {
            val outputLocation: File?
            if (isHtmlRequired) {
                outputLocation = htmlOutputLocation
            } else if (isXmlRequired) {
                outputLocation = xmlOutputLocation
            } else if (isSarifRequired) {
                outputLocation = sarifOutputLocation
            } else {
                outputLocation = null
            }
            return if (outputLocation != null) String.format(" See the report at: %s", ConsoleRenderer().asClickableFileUrl(outputLocation)) else "\n"
        }

        private fun getViolationMessage(reportXml: Node?): String {
            if (violationsExist(reportXml)) {
                val errorFileCount: Int = Companion.getErrorFileCount(reportXml!!)
                val violations: MutableList<String?> = Companion.getViolations(reportXml)
                return String.format(
                    "\n" +
                            "Checkstyle files with violations: %s\n" +
                            "Checkstyle violations by severity: %s\n",
                    errorFileCount,
                    violations
                )
            }
            return "\n"
        }

        private fun readText(stream: InputStream): String? {
            try {
                return IOUtils.toString(stream, StandardCharsets.UTF_8)
            } catch (e: IOException) {
                throw throwAsUncheckedException(e)
            }
        }

        private fun getViolations(reportXml: Node): MutableList<String?> {
            val errorNodes: MutableList<Node?> = reportXml.getAt(QName.valueOf("file")).getAt("error")
            return errorNodes.stream()
                .map<String?> { node: Node? -> node!!.attribute("severity") as String? }
                .collect(Collectors.groupingBy(Function.identity<String?>(), Collectors.counting()))
                .entries.stream()
                .map<String?> { entry: MutableMap.MutableEntry<String?, Long?>? -> entry!!.key + ":" + entry.value }
                .collect(Collectors.toList())
        }

        private fun violationsExist(reportXml: Node?): Boolean {
            return reportXml != null && getErrorFileCount(reportXml) > 0
        }

        private fun getErrorFileCount(reportXml: Node): Int {
            var count = 0
            for (node in reportXml.getAt(QName.valueOf("file"))) {
                val errors = (node as Node).getAt(QName.valueOf("error"))
                if (!errors.isEmpty()) {
                    count++
                }
            }
            return count
        }

        private fun isHtmlReportEnabledOnly(isXmlRequired: Boolean, isHtmlRequired: Boolean): Boolean {
            return !isXmlRequired && isHtmlRequired
        }
    }
}
