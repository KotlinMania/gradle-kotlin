/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.verification.serializer

import com.google.common.collect.Interner
import com.google.common.collect.Interners
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.verification.exceptions.DependencyVerificationException
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind
import org.gradle.api.internal.artifacts.verification.model.IgnoredKey
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifier
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier
import org.gradle.internal.xml.XmlFactories
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParser

object DependencyVerificationsXmlReader {
    @JvmStatic
    fun readFromXml(`in`: InputStream?, builder: DependencyVerifierBuilder) {
        try {
            `in`.use { `is` ->
                val saxParser = createSecureParser()
                val xmlReader = saxParser.getXMLReader()
                val handler = VerifiersHandler(builder)
                xmlReader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
                xmlReader.setContentHandler(handler)
                xmlReader.parse(InputSource(`is`))
            }
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        } catch (e: Exception) {
            throw DependencyVerificationException("Unable to read dependency verification metadata", e)
        }
    }

    @JvmStatic
    fun readFromXml(`in`: InputStream?): DependencyVerifier {
        val builder = DependencyVerifierBuilder()
        readFromXml(`in`, builder)
        return builder.build()
    }

    @Throws(ParserConfigurationException::class, SAXException::class)
    private fun createSecureParser(): SAXParser {
        val spf = XmlFactories.newSAXParserFactory()
        spf.setFeature("http://xml.org/sax/features/namespaces", false)
        spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        return spf.newSAXParser()
    }

    private class VerifiersHandler(private val builder: DependencyVerifierBuilder) : DefaultHandler2() {
        private val stringInterner: Interner<String> = Interners.newStrongInterner<String?>()
        private var inMetadata = false
        private var inComponents = false
        private var inConfiguration = false
        private var inVerifyMetadata = false
        private var inVerifySignatures = false
        private var inTrustedArtifacts = false
        private var inKeyServers = false
        private var inIgnoredKeys = false
        private var inTrustedKeys = false
        private var inTrustedKey = false
        private var currentTrustedKey: String? = null
        private var inKeyRingFormat = false
        private var currentComponent: ModuleComponentIdentifier? = null
        private var currentArtifact: ModuleComponentArtifactIdentifier? = null
        private var currentChecksum: ChecksumKind? = null

        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
            when (qName) {
                DependencyVerificationXmlTags.CONFIG -> inConfiguration = true
                DependencyVerificationXmlTags.VERIFICATION_METADATA -> inMetadata = true
                DependencyVerificationXmlTags.COMPONENTS -> {
                    assertInMetadata()
                    inComponents = true
                }

                DependencyVerificationXmlTags.COMPONENT -> {
                    assertInComponents()
                    currentComponent = createComponentId(attributes)
                }

                DependencyVerificationXmlTags.ARTIFACT -> {
                    assertValidComponent()
                    currentArtifact = createArtifactId(attributes)
                }

                DependencyVerificationXmlTags.VERIFY_METADATA -> {
                    assertInConfiguration(DependencyVerificationXmlTags.VERIFY_METADATA)
                    inVerifyMetadata = true
                }

                DependencyVerificationXmlTags.VERIFY_SIGNATURES -> {
                    assertInConfiguration(DependencyVerificationXmlTags.VERIFY_SIGNATURES)
                    inVerifySignatures = true
                }

                DependencyVerificationXmlTags.TRUSTED_ARTIFACTS -> {
                    assertInConfiguration(DependencyVerificationXmlTags.TRUSTED_ARTIFACTS)
                    inTrustedArtifacts = true
                }

                DependencyVerificationXmlTags.TRUSTED_KEY -> {
                    assertContext(inTrustedKeys, DependencyVerificationXmlTags.TRUSTED_KEY, DependencyVerificationXmlTags.TRUSTED_KEYS)
                    addTrustedKey(attributes)
                    inTrustedKey = true
                }

                DependencyVerificationXmlTags.TRUSTED_KEYS -> {
                    assertInConfiguration(DependencyVerificationXmlTags.TRUSTED_KEYS)
                    inTrustedKeys = true
                }

                DependencyVerificationXmlTags.TRUST -> {
                    assertInTrustedArtifacts()
                    addTrustedArtifact(attributes)
                }

                DependencyVerificationXmlTags.TRUSTING -> {
                    assertContext(inTrustedKey, DependencyVerificationXmlTags.TRUSTING, DependencyVerificationXmlTags.TRUSTED_KEY)
                    maybeAddTrustedKey(attributes)
                }

                DependencyVerificationXmlTags.KEY_SERVERS -> {
                    assertInConfiguration(DependencyVerificationXmlTags.KEY_SERVERS)
                    inKeyServers = true
                    val enabled = getNullableAttribute(attributes, DependencyVerificationXmlTags.ENABLED)
                    if (enabled != null) {
                        builder.isUseKeyServers = enabled.toBoolean()
                    }
                }

                DependencyVerificationXmlTags.KEY_SERVER -> {
                    assertContext(inKeyServers, DependencyVerificationXmlTags.KEY_SERVER, DependencyVerificationXmlTags.KEY_SERVERS)
                    val server = getAttribute(attributes, DependencyVerificationXmlTags.URI)
                    try {
                        builder.addKeyServer(URI(server))
                    } catch (e: URISyntaxException) {
                        throw DependencyVerificationException("Unsupported URI for key server: " + server)
                    }
                }

                DependencyVerificationXmlTags.IGNORED_KEYS -> {
                    if (currentArtifact == null) {
                        assertInConfiguration(DependencyVerificationXmlTags.IGNORED_KEYS)
                    }
                    inIgnoredKeys = true
                }

                DependencyVerificationXmlTags.IGNORED_KEY -> {
                    assertContext(inIgnoredKeys, DependencyVerificationXmlTags.IGNORED_KEY, DependencyVerificationXmlTags.IGNORED_KEYS)
                    if (currentArtifact != null) {
                        addArtifactIgnoredKey(attributes)
                    } else {
                        addIgnoredKey(attributes)
                    }
                }

                DependencyVerificationXmlTags.KEYRING_FORMAT -> {
                    assertInConfiguration(DependencyVerificationXmlTags.KEYRING_FORMAT)
                    inKeyRingFormat = true
                }

                else -> if (currentChecksum != null && DependencyVerificationXmlTags.ALSO_TRUST == qName) {
                    builder.addChecksum(currentArtifact!!, currentChecksum, getAttribute(attributes, DependencyVerificationXmlTags.VALUE), null, null)
                } else if (currentArtifact != null) {
                    if (DependencyVerificationXmlTags.PGP == qName) {
                        builder.addTrustedKey(currentArtifact!!, getAttribute(attributes, DependencyVerificationXmlTags.VALUE))
                    } else {
                        currentChecksum = ChecksumKind.valueOf(qName)
                        builder.addChecksum(
                            currentArtifact!!,
                            currentChecksum,
                            getAttribute(attributes, DependencyVerificationXmlTags.VALUE),
                            getNullableAttribute(attributes, DependencyVerificationXmlTags.ORIGIN),
                            getNullableAttribute(attributes, DependencyVerificationXmlTags.REASON)
                        )
                    }
                }
            }
        }

        fun addArtifactIgnoredKey(attributes: Attributes) {
            builder.addIgnoredKey(currentArtifact!!, toIgnoredKey(attributes))
        }

        fun toIgnoredKey(attributes: Attributes): IgnoredKey {
            return IgnoredKey(getAttribute(attributes, DependencyVerificationXmlTags.ID), getNullableAttribute(attributes, DependencyVerificationXmlTags.REASON))
        }

        fun addIgnoredKey(attributes: Attributes) {
            builder.addIgnoredKey(toIgnoredKey(attributes))
        }

        fun assertInTrustedArtifacts() {
            assertContext(inTrustedArtifacts, DependencyVerificationXmlTags.TRUST, DependencyVerificationXmlTags.TRUSTED_ARTIFACTS)
        }

        fun addTrustedArtifact(attributes: Attributes) {
            var regex = false
            val regexAttr = getNullableAttribute(attributes, DependencyVerificationXmlTags.REGEX)
            if (regexAttr != null) {
                regex = regexAttr.toBoolean()
            }
            builder.addTrustedArtifact(
                getNullableAttribute(attributes, DependencyVerificationXmlTags.GROUP),
                getNullableAttribute(attributes, DependencyVerificationXmlTags.NAME),
                getNullableAttribute(attributes, DependencyVerificationXmlTags.VERSION),
                getNullableAttribute(attributes, DependencyVerificationXmlTags.FILE),
                regex,
                getNullableAttribute(attributes, DependencyVerificationXmlTags.REASON)
            )
        }

        fun addTrustedKey(attributes: Attributes) {
            currentTrustedKey = getAttribute(attributes, DependencyVerificationXmlTags.ID)
            maybeAddTrustedKey(attributes)
        }

        fun maybeAddTrustedKey(attributes: Attributes) {
            var regex = false
            val regexAttr = getNullableAttribute(attributes, DependencyVerificationXmlTags.REGEX)
            if (regexAttr != null) {
                regex = regexAttr.toBoolean()
            }
            val group = getNullableAttribute(attributes, DependencyVerificationXmlTags.GROUP)
            val name = getNullableAttribute(attributes, DependencyVerificationXmlTags.NAME)
            val version = getNullableAttribute(attributes, DependencyVerificationXmlTags.VERSION)
            val file = getNullableAttribute(attributes, DependencyVerificationXmlTags.FILE)
            if (group != null || name != null || version != null || file != null) {
                builder.addTrustedKey(
                    currentTrustedKey!!,
                    group,
                    name,
                    version,
                    file,
                    regex
                )
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inVerifyMetadata) {
                builder.isVerifyMetadata = readBoolean(ch, start, length)
            } else if (inVerifySignatures) {
                builder.isVerifySignatures = readBoolean(ch, start, length)
            } else if (inKeyRingFormat) {
                builder.setKeyringFormat(String(ch, start, length))
            }
        }

        fun readBoolean(ch: CharArray, start: Int, length: Int): Boolean {
            return String(ch, start, length).toBoolean()
        }

        fun assertInConfiguration(tag: String?) {
            assertContext(inConfiguration, tag, DependencyVerificationXmlTags.CONFIG)
        }

        fun assertInComponents() {
            assertContext(inComponents, DependencyVerificationXmlTags.COMPONENT, DependencyVerificationXmlTags.COMPONENTS)
        }

        fun assertInMetadata() {
            assertContext(inMetadata, DependencyVerificationXmlTags.COMPONENTS, DependencyVerificationXmlTags.VERIFICATION_METADATA)
        }

        fun assertValidComponent() {
            assertContext(currentComponent != null, DependencyVerificationXmlTags.ARTIFACT, DependencyVerificationXmlTags.COMPONENT)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            when (qName) {
                DependencyVerificationXmlTags.CONFIG -> inConfiguration = false
                DependencyVerificationXmlTags.VERIFY_METADATA -> inVerifyMetadata = false
                DependencyVerificationXmlTags.VERIFY_SIGNATURES -> inVerifySignatures = false
                DependencyVerificationXmlTags.VERIFICATION_METADATA -> inMetadata = false
                DependencyVerificationXmlTags.COMPONENTS -> inComponents = false
                DependencyVerificationXmlTags.COMPONENT -> currentComponent = null
                DependencyVerificationXmlTags.TRUSTED_ARTIFACTS -> inTrustedArtifacts = false
                DependencyVerificationXmlTags.TRUSTED_KEYS -> inTrustedKeys = false
                DependencyVerificationXmlTags.TRUSTED_KEY -> {
                    inTrustedKey = false
                    currentTrustedKey = null
                }

                DependencyVerificationXmlTags.KEY_SERVERS -> inKeyServers = false
                DependencyVerificationXmlTags.ARTIFACT -> {
                    currentArtifact = null
                    currentChecksum = null
                }

                DependencyVerificationXmlTags.IGNORED_KEYS -> inIgnoredKeys = false
                DependencyVerificationXmlTags.KEYRING_FORMAT -> inKeyRingFormat = false
            }
        }

        fun createArtifactId(attributes: Attributes): ModuleComponentFileArtifactIdentifier {
            return ModuleComponentFileArtifactIdentifier(
                currentComponent!!,
                getAttribute(attributes, DependencyVerificationXmlTags.NAME)
            )
        }

        fun createComponentId(attributes: Attributes): ModuleComponentIdentifier {
            return newId(
                createModuleId(attributes),
                getAttribute(attributes, DependencyVerificationXmlTags.VERSION)
            )
        }

        fun createModuleId(attributes: Attributes): ModuleIdentifier {
            return DefaultModuleIdentifier.newId(getAttribute(attributes, DependencyVerificationXmlTags.GROUP), getAttribute(attributes, DependencyVerificationXmlTags.NAME))
        }

        fun getAttribute(attributes: Attributes, name: String?): String {
            val value = attributes.getValue(name)
            assertContext(value != null, "Missing attribute: " + name)
            return stringInterner.intern(value)
        }

        fun getNullableAttribute(attributes: Attributes, name: String?): String? {
            val value = attributes.getValue(name)
            if (value == null) {
                return null
            }
            return stringInterner.intern(value)
        }

        @Throws(SAXException::class)
        override fun comment(ch: CharArray, start: Int, length: Int) {
            if (!inMetadata) {
                builder.addTopLevelComment(String(ch, start, length))
            }
        }

        companion object {
            private fun assertContext(test: Boolean, innerTag: String?, outerTag: String?) {
                assertContext(test, "<" + innerTag + "> must be found under the <" + outerTag + "> tag")
            }

            private fun assertContext(test: Boolean, message: String?) {
                if (!test) {
                    throw DependencyVerificationException("Invalid dependency verification metadata file: " + message)
                }
            }
        }
    }
}
