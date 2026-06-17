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
package org.gradle.jvm.toolchain.internal

import org.gradle.api.internal.file.FileResolver
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.xml.XmlFactories
import org.gradle.util.internal.MavenUtil
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Collectors
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpressionException
import javax.xml.xpath.XPathFactory

class MavenToolchainsInstallationSupplier @Inject constructor(private val providerFactory: ProviderFactory, private val fileResolver: FileResolver) : InstallationSupplier {
    private val toolchainLocation: Provider<String?>
    private val xPathFactory: XPathFactory
    private val documentBuilderFactory: DocumentBuilderFactory

    init {
        this.toolchainLocation = providerFactory.gradleProperty(PROPERTY_NAME).orElse(defaultMavenToolchainsDefinitionsLocation())
        this.xPathFactory = XmlFactories.newXPathFactory()
        this.documentBuilderFactory = XmlFactories.newDocumentBuilderFactory()
    }

    override fun getSourceName(): String {
        return "Maven Toolchains"
    }

    override fun get(): MutableSet<InstallationLocation?> {
        val toolchainFile = fileResolver.resolve(toolchainLocation.get())
        if (toolchainFile.exists()) {
            try {
                FileInputStream(toolchainFile).use { toolchain ->
                    val documentBuilder = documentBuilderFactory.newDocumentBuilder()
                    documentBuilder.setErrorHandler(PropagatingErrorHandler())
                    val xpath = xPathFactory.newXPath()
                    val expression = xpath.compile(PARSE_EXPRESSION)

                    val nodes = expression.evaluate(documentBuilder.parse(toolchain), XPathConstants.NODESET) as NodeList
                    val locations: MutableSet<String?> = HashSet<String?>()
                    for (i in 0..<nodes.getLength()) {
                        val item = nodes.item(i)
                        if (item != null && item.getNodeType() == Node.TEXT_NODE) {
                            val nodeValue = item.getNodeValue().trim { it <= ' ' }
                            val matcher: Matcher = ENV_VARIABLE_PATTERN.matcher(nodeValue)
                            val resolvedValue = StringBuffer()
                            while (matcher.find()) {
                                val envVariableName = matcher.group(1)
                                val envVariableValue = providerFactory.environmentVariable(envVariableName).getOrNull()
                                if (envVariableValue == null) {
                                    matcher.appendReplacement(resolvedValue, "\\\${env." + envVariableName + "}")
                                    continue
                                }
                                matcher.appendReplacement(resolvedValue, Matcher.quoteReplacement(envVariableValue))
                            }
                            // If no match or there is remaining text after the environment property, append it.
                            matcher.appendTail(resolvedValue)
                            locations.add(resolvedValue.toString())
                        }
                    }
                    return locations.stream()
                        .map<InstallationLocation?> { jdkHome: String? -> InstallationLocation.Companion.autoDetected(File(jdkHome), getSourceName()) }
                        .collect(Collectors.toSet())
                }
            } catch (e: IOException) {
                if (LOGGER!!.isDebugEnabled()) {
                    LOGGER.debug("Java Toolchain auto-detection failed to parse Maven Toolchains located at {}", toolchainFile, e)
                } else {
                    LOGGER.info("Java Toolchain auto-detection failed to parse Maven Toolchains located at {}. {}", toolchainFile, e.message)
                }
            } catch (e: ParserConfigurationException) {
                if (LOGGER!!.isDebugEnabled()) {
                    LOGGER.debug("Java Toolchain auto-detection failed to parse Maven Toolchains located at {}", toolchainFile, e)
                } else {
                    LOGGER.info("Java Toolchain auto-detection failed to parse Maven Toolchains located at {}. {}", toolchainFile, e.message)
                }
            } catch (e: SAXException) {
                if (LOGGER!!.isDebugEnabled()) {
                    LOGGER.debug("Java Toolchain auto-detection failed to parse Maven Toolchains located at {}", toolchainFile, e)
                } else {
                    LOGGER.info("Java Toolchain auto-detection failed to parse Maven Toolchains located at {}. {}", toolchainFile, e.message)
                }
            } catch (e: XPathExpressionException) {
                if (LOGGER!!.isDebugEnabled()) {
                    LOGGER.debug("Java Toolchain auto-detection failed to parse Maven Toolchains located at {}", toolchainFile, e)
                } else {
                    LOGGER.info("Java Toolchain auto-detection failed to parse Maven Toolchains located at {}. {}", toolchainFile, e.message)
                }
            }
        }
        return mutableSetOf<InstallationLocation?>()
    }

    private fun defaultMavenToolchainsDefinitionsLocation(): String {
        return File(MavenUtil.getUserMavenDir(), "toolchains.xml").getAbsolutePath()
    }

    private class PropagatingErrorHandler : ErrorHandler {
        @Throws(SAXException::class)
        override fun warning(e: SAXParseException?) {
            // Non-fatal error. No need to log.
        }

        @Throws(SAXException::class)
        override fun error(e: SAXParseException?) {
            // Non-fatal error. No need to log.
        }

        @Throws(SAXException::class)
        override fun fatalError(e: SAXParseException) {
            // Propagate error -- consistent with default behavior.
            throw e
        }
    }

    companion object {
        private const val PROPERTY_NAME = "org.gradle.java.installations.maven-toolchains-file"
        private const val PARSE_EXPRESSION = "/toolchains/toolchain[type='jdk']/configuration/jdkHome//text()"
        private val LOGGER = getLogger(MavenToolchainsInstallationSupplier::class.java)
        private val ENV_VARIABLE_PATTERN: Pattern = Pattern.compile("\\$\\{env\\.([^}]+)}")
    }
}
