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
package org.gradle.api.java.archives.internal

import org.gradle.api.java.archives.Manifest
import org.gradle.api.provider.Provider
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.jar.Attributes
import java.util.jar.Manifest

/**
 * Represents a Manifest that knows the character set of its content and can write to an OutputStream using that character set.
 *
 * @since 2.14
 */
interface ManifestInternal : Manifest {
    /**
     * Returns the character set used to decode the manifest content, if known.
     *
     * @since 9.5.0
     */
    val contentCharsetProvider: Provider<String?>?

    @get:Deprecated("")
    val contentCharset: String?
        /**
         * Returns the character set used to decode the manifest content, if known.
         *
         * This method is deprecated and will be removed in Gradle 10.0.0. Use [.getContentCharsetProvider] instead.
         */
        get() = this.contentCharsetProvider.get()

    /**
     * Writes the manifest into a stream.
     *
     * The manifest will be encoded using the character set implicit in the manifest itself.
     *
     * @param outputStream The stream to write the manifest to
     * @return this
     */
    fun writeTo(outputStream: OutputStream?): Manifest?

    object ManifestWriter {
        private fun generateJavaManifest(gradleManifest: Manifest): java.util.jar.Manifest {
            val javaManifest = Manifest()
            addMainAttributesToJavaManifest(gradleManifest, javaManifest)
            addSectionAttributesToJavaManifest(gradleManifest, javaManifest)
            return javaManifest
        }

        private fun addMainAttributesToJavaManifest(gradleManifest: Manifest, javaManifest: java.util.jar.Manifest) {
            fillAttributes(gradleManifest.getAttributes(), javaManifest.getMainAttributes())
        }

        private fun addSectionAttributesToJavaManifest(gradleManifest: Manifest, javaManifest: java.util.jar.Manifest) {
            for (entry in gradleManifest.getSections().entries) {
                val sectionName = entry.key
                val targetAttributes = Attributes()
                fillAttributes(entry.value, targetAttributes)
                javaManifest.getEntries().put(sectionName, targetAttributes)
            }
        }

        private fun fillAttributes(attributes: org.gradle.api.java.archives.Attributes, targetAttributes: Attributes) {
            for (entry in attributes.entries) {
                val mainAttributeName = entry.key
                val mainAttributeValue = resolveValueToString(entry.value)
                if (mainAttributeValue != null) {
                    targetAttributes.putValue(mainAttributeName, mainAttributeValue)
                }
            }
        }

        private fun resolveValueToString(value: Any): String? {
            var underlyingValue = value
            if (value is Provider<*>) {
                val provider: Provider<*> = uncheckedCast<Provider<*>?>(value)!!
                if (!provider.isPresent()) {
                    return null
                }
                underlyingValue = provider.get()
            }
            return underlyingValue.toString()
        }

        fun writeTo(manifest: Manifest, outputStream: OutputStream, contentCharset: String) {
            try {
                val javaManifest = generateJavaManifest(manifest.getEffectiveManifest())
                val buffer = ByteArrayOutputStream()
                javaManifest.write(buffer)
                val manifestBytes: ByteArray?
                if (DEFAULT_CONTENT_CHARSET == contentCharset) {
                    manifestBytes = buffer.toByteArray()
                } else {
                    // Convert the UTF-8 manifest bytes to the requested content charset
                    manifestBytes = buffer.toString(DEFAULT_CONTENT_CHARSET).toByteArray(charset(contentCharset))
                }
                outputStream.write(manifestBytes)
            } catch (e: IOException) {
                throw throwAsUncheckedException(e)
            }
        }
    }

    companion object {
        const val DEFAULT_CONTENT_CHARSET: String = "UTF-8"
    }
}
