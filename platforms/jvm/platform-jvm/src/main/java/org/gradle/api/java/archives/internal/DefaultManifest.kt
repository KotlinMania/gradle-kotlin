/*
 * Copyright 2007 the original author or authors.
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

import groovy.lang.Closure
import org.apache.commons.io.FileUtils
import org.gradle.api.Action
import org.gradle.api.java.archives.Attributes
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.ManifestMergeSpec
import org.gradle.api.provider.Provider
import org.gradle.internal.Actions
import org.gradle.internal.IoActions
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.file.PathToFileResolver
import org.gradle.util.internal.ClosureBackedAction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.jar.Manifest

class DefaultManifest : ManifestInternal {
    val mergeSpecs: MutableList<ManifestMergeSpec> = ArrayList<ManifestMergeSpec>()
    private val attributes = DefaultAttributes()
    private val sections: MutableMap<String?, Attributes?> = LinkedHashMap<String?, Attributes?>()
    private val fileResolver: PathToFileResolver

    // We have both of these because the DefaultManifest(PathToFileResolver) constructor is used by the
    // Shadow and BND plugins. We need the String constructor because DefaultManifestMergeSpec can create a manifest
    // and has its own contentCharset that is settable via the public API.
    //
    // Because we can't change the constructor to accept a ProviderFactory, we can't construct a ManifestMergeSpec
    // with a ProviderFactory, which means it can't supply a provider when it loops back and creates a new
    // Manifest, so we have to have constructors that accept either a Provider or a String.
    //
    // If we can get Shadow and BND off of using these constructors, then we can remove all of this evil.
    // We could:
    //   1. Provide a way for plugins to generate a manifest via a Gradle-provided factory
    //   2. Make ProviderFactory an argument to the constructor
    //   3. Make the contentCharset a Property in DefaultManifestMergeSpec
    //   4. Get rid of the non-Provider constructors
    private val contentCharsetProvider: Provider<String?>?
    private val contentCharset: String

    constructor(fileResolver: PathToFileResolver) {
        this.fileResolver = fileResolver
        this.contentCharsetProvider = null
        this.contentCharset = ManifestInternal.Companion.DEFAULT_CONTENT_CHARSET
        init()
    }

    constructor(fileResolver: PathToFileResolver, contentCharset: String) {
        this.fileResolver = fileResolver
        this.contentCharsetProvider = null
        this.contentCharset = contentCharset
        init()
    }

    constructor(fileResolver: PathToFileResolver, contentCharsetProvider: Provider<String?>?) {
        this.fileResolver = fileResolver
        this.contentCharsetProvider = contentCharsetProvider
        this.contentCharset = ManifestInternal.Companion.DEFAULT_CONTENT_CHARSET
        init()
    }

    fun init(): DefaultManifest {
        getAttributes().put("Manifest-Version", "1.0")
        return this
    }

    override fun getContentCharsetProvider(): Provider<String?>? {
        return contentCharsetProvider
    }

    @Suppress("deprecation")
    override fun getContentCharset(): String? {
        return this.effectiveContentCharset
    }

    private val effectiveContentCharset: String?
        get() = if (contentCharsetProvider == null) contentCharset else contentCharsetProvider.getOrElse(contentCharset)

    fun mainAttributes(attributes: MutableMap<String?, *>): DefaultManifest {
        return attributes(attributes)
    }

    override fun attributes(attributes: MutableMap<String?, *>): DefaultManifest {
        getAttributes().putAll(attributes)
        return this
    }

    override fun attributes(attributes: MutableMap<String?, *>, sectionName: String?): DefaultManifest {
        if (!sections.containsKey(sectionName)) {
            sections.put(sectionName, DefaultAttributes())
        }
        sections.get(sectionName)!!.putAll(attributes)
        return this
    }

    override fun getAttributes(): Attributes {
        return attributes
    }

    override fun getSections(): MutableMap<String?, Attributes?> {
        return sections
    }

    fun clear(): DefaultManifest {
        attributes.clear()
        sections.clear()
        mergeSpecs.clear()
        init()
        return this
    }

    override fun from(vararg mergePaths: Any?): DefaultManifest {
        return from(mergePaths, Actions.doNothing<ManifestMergeSpec?>())
    }

    override fun from(mergePaths: Any?, closure: Closure<*>?): DefaultManifest {
        return from(mergePaths, ClosureBackedAction.of<ManifestMergeSpec?>(closure))
    }

    override fun from(mergePath: Any?, action: Action<ManifestMergeSpec?>): DefaultManifest {
        val mergeSpec = DefaultManifestMergeSpec()
        mergeSpec.from(mergePath)
        mergeSpecs.add(mergeSpec)
        action.execute(mergeSpec)
        return this
    }

    override fun getEffectiveManifest(): DefaultManifest {
        return getEffectiveManifestInternal(this)
    }

    protected fun getEffectiveManifestInternal(baseManifest: DefaultManifest): DefaultManifest {
        var resultManifest = baseManifest
        for (manifestMergeSpec in this.mergeSpecs) {
            resultManifest = (manifestMergeSpec as DefaultManifestMergeSpec).merge(resultManifest, fileResolver)
        }
        return resultManifest
    }

    override fun writeTo(outputStream: OutputStream?): Manifest {
        ManifestInternal.ManifestWriter.writeTo(this, outputStream, this.effectiveContentCharset)
        return this
    }

    override fun writeTo(path: Any?): Manifest {
        val manifestFile = fileResolver.resolve(path)
        try {
            val parentFile = manifestFile.getParentFile()
            if (parentFile != null) {
                FileUtils.forceMkdir(parentFile)
            }
            IoActions.withResource<FileOutputStream?>(FileOutputStream(manifestFile), object : Action<FileOutputStream?> {
                override fun execute(fileOutputStream: FileOutputStream?) {
                    ManifestInternal.ManifestWriter.writeTo(this@DefaultManifest, fileOutputStream, this.effectiveContentCharset)
                }
            })
            return this
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    fun read(manifestPath: Any?): DefaultManifest {
        val manifestFile = fileResolver.resolve(manifestPath)
        try {
            var manifestBytes = FileUtils.readFileToByteArray(manifestFile)
            manifestBytes = prepareManifestBytesForInteroperability(manifestBytes)
            // Eventually convert manifest content to UTF-8 before handing it to java.util.jar.Manifest
            if (ManifestInternal.Companion.DEFAULT_CONTENT_CHARSET != this.effectiveContentCharset) {
                manifestBytes = String(manifestBytes, charset(this.effectiveContentCharset!!)).toByteArray(charset(ManifestInternal.Companion.DEFAULT_CONTENT_CHARSET))
            }
            // Effectively read the manifest
            val javaManifest = Manifest(ByteArrayInputStream(manifestBytes))
            addJavaManifestToAttributes(javaManifest)
            addJavaManifestToSections(javaManifest)
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
        return this
    }

    /**
     * Prepare Manifest bytes for interoperability. Ant Manifest class doesn't support split multi-bytes characters, Java Manifest class does. Ant Manifest class supports manifest sections starting
     * without prior blank lines, Java Manifest class doesn't. Ant Manifest class supports manifest without last line blank, Java Manifest class doesn't. Therefore we need to insert blank lines before
     * entries named 'Name' and before EOF if needed. This without decoding characters as this would break split multi-bytes characters, hence working on the bytes level.
     */
    private fun prepareManifestBytesForInteroperability(original: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        var useCarriageReturns = false
        val carriageReturn = '\r'.code.toByte()
        val newLine = '\n'.code.toByte()
        for (idx in original.indices) {
            val current = original[idx]
            if (current == carriageReturn) {
                useCarriageReturns = true
            }
            if (idx == original.size - 1) {
                // Always append a new line at EOF
                output.write(current.toInt())
                if (useCarriageReturns) {
                    output.write(carriageReturn.toInt())
                }
                output.write(newLine.toInt())
            } else if (current == newLine && idx + 5 < original.size) {
                // Eventually add blank line before section
                output.write(current.toInt())
                if ((original[idx + 1] == 'N'.code.toByte() || original[idx + 1] == 'n'.code.toByte())
                    && (original[idx + 2] == 'A'.code.toByte() || original[idx + 2] == 'a'.code.toByte())
                    && (original[idx + 3] == 'M'.code.toByte() || original[idx + 3] == 'm'.code.toByte())
                    && (original[idx + 4] == 'E'.code.toByte() || original[idx + 4] == 'e'.code.toByte())
                    && (original[idx + 5] == ':'.code.toByte())
                ) {
                    if (useCarriageReturns) {
                        output.write(carriageReturn.toInt())
                    }
                    output.write(newLine.toInt())
                }
            } else {
                output.write(current.toInt())
            }
        }
        return output.toByteArray()
    }

    private fun addJavaManifestToAttributes(javaManifest: java.util.jar.Manifest) {
        attributes.put("Manifest-Version", "1.0")
        for (attributeKey in javaManifest.getMainAttributes().keys) {
            val attributeName: String? = attributeKey.toString()
            val attributeValue = javaManifest.getMainAttributes().getValue(attributeName)
            attributes.put(attributeName, attributeValue)
        }
    }

    private fun addJavaManifestToSections(javaManifest: java.util.jar.Manifest) {
        for (sectionEntry in javaManifest.getEntries().entries) {
            val sectionName = sectionEntry.key
            val sectionAttributes = DefaultAttributes()
            for (attributeKey in sectionEntry.value.keys) {
                val attributeName: String? = attributeKey.toString()
                val attributeValue = sectionEntry.value.getValue(attributeName)
                sectionAttributes.put(attributeName, attributeValue)
            }
            sections.put(sectionName, sectionAttributes)
        }
    }
}
