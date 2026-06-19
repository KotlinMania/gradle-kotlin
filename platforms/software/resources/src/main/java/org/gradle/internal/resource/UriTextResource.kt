/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.internal.resource

import com.google.common.io.Files
import org.apache.commons.io.IOUtils
import org.gradle.api.resources.MissingResourceException
import org.gradle.api.resources.ResourceException
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.FileUtils
import org.gradle.internal.SystemProperties
import org.gradle.internal.file.RelativeFilePathResolver
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.util.GradleVersion
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.net.JarURLConnection
import java.net.URI
import java.nio.charset.Charset

/**
 * A [TextResource] implementation backed by a URI. Defaults content encoding to UTF-8.
 */
open class UriTextResource : TextResource {
    private val description: String
    private val sourceFile: File?
    private val sourceUri: URI
    private val resolver: RelativeFilePathResolver

    internal constructor(description: String, sourceFile: File, resolver: RelativeFilePathResolver) {
        this.description = description
        this.sourceFile = FileUtils.normalize(sourceFile)
        this.sourceUri = sourceFile.toURI()
        this.resolver = resolver
    }

    constructor(description: String, sourceUri: URI, resolver: RelativeFilePathResolver) {
        this.description = description
        this.sourceFile = if (sourceUri.getScheme() == "file") FileUtils.normalize(File(sourceUri.getPath())) else null
        this.sourceUri = sourceUri
        this.resolver = resolver
    }

    override fun getDisplayName(): String {
        return getLongDisplayName().getDisplayName()
    }

    override fun getLongDisplayName(): DisplayName {
        if (sourceFile != null) {
            return Describables.quoted(description, sourceFile.getAbsolutePath())
        } else {
            return Describables.quoted(description, sourceUri)
        }
    }

    override fun getShortDisplayName(): DisplayName {
        if (sourceFile != null) {
            return Describables.quoted(description, resolver.resolveForDisplay(sourceFile))
        } else {
            return Describables.quoted(description, sourceUri)
        }
    }

    override fun isContentCached(): Boolean {
        return false
    }

    override fun getHasEmptyContent(): Boolean {
        val file = getFile()
        if (file != null) {
            assertNoDirectory()
            if (!file.exists()) {
                throw MissingResourceException(sourceUri, String.format("Could not read %s as it does not exist.", getDisplayName()))
            }
            return file.length() == 0L
        }
        val reader = getAsReader()
        try {
            try {
                return reader.read() == -1
            } finally {
                reader.close()
            }
        } catch (e: Exception) {
            throw ResourceExceptions.failure(sourceUri, String.format("Could not read %s.", getDisplayName()), e)
        }
    }

    override fun getText(): String? {
        val file = getFile()
        if (file != null) {
            assertNoDirectory()
            try {
                return Files.asCharSource(file, getCharset()!!).read()
            } catch (e: FileNotFoundException) {
                throw MissingResourceException(sourceUri, String.format("Could not read %s as it does not exist.", getDisplayName()))
            } catch (e: Exception) {
                throw ResourceExceptions.failure(sourceUri, String.format("Could not read %s.", getDisplayName()), e)
            }
        }
        val reader = getAsReader()
        try {
            try {
                return IOUtils.toString(reader)
            } finally {
                reader.close()
            }
        } catch (e: Exception) {
            throw ResourceExceptions.failure(sourceUri, String.format("Could not read %s.", getDisplayName()), e)
        }
    }

    @Throws(ResourceException::class)
    override fun getContentHash(): HashCode? {
        val hasher = Hashing.newPrimitiveHasher()
        hasher.putHash(SIGNATURE)
        hasher.putString(getText()!!)
        return hasher.hash()
    }

    override fun getAsReader(): Reader {
        assertNoDirectory()
        try {
            return openReader()
        } catch (e: FileNotFoundException) {
            throw MissingResourceException(sourceUri, String.format("Could not read %s as it does not exist.", getDisplayName()))
        } catch (e: Exception) {
            throw ResourceExceptions.failure(sourceUri, String.format("Could not read %s.", getDisplayName()), e)
        }
    }

    private fun assertNoDirectory() {
        if (sourceFile != null && sourceFile.isDirectory()) {
            throw ResourceIsAFolderException(sourceUri, String.format("Could not read %s as it is a directory.", getDisplayName()))
        }
    }

    override fun getExists(): Boolean {
        val file = getFile()
        if (file != null) {
            return file.exists()
        }
        try {
            val reader = openReader()
            try {
                return true
            } finally {
                reader.close()
            }
        } catch (e: FileNotFoundException) {
            return false
        } catch (e: Exception) {
            throw ResourceExceptions.failure(sourceUri, String.format("Could not determine if %s exists.", getDisplayName()), e)
        }
    }

    @Throws(IOException::class)
    protected open fun openReader(): Reader {
        val file = getFile()
        if (file != null) {
            return InputStreamReader(FileInputStream(file), getCharset())
        }
        val urlConnection = sourceUri.toURL().openConnection()
        urlConnection.setRequestProperty("User-Agent", userAgentString)

        // Without this, the URLConnection will keep the backing Jar file open indefinitely
        // This will have a performance impact for Jar-backed `UriTextResource` instances
        if (urlConnection is JarURLConnection) {
            urlConnection.setUseCaches(false)
        }
        urlConnection.connect()
        val contentType = urlConnection.getContentType()
        val charset: Charset = extractCharacterEncoding(contentType, DEFAULT_ENCODING)
        return InputStreamReader(urlConnection.getInputStream(), charset)
    }

    override fun getFile(): File? {
        return sourceFile
    }

    override fun getCharset(): Charset? {
        if (getFile() != null) {
            return DEFAULT_ENCODING
        }
        return null
    }

    override fun getLocation(): ResourceLocation {
        return UriResourceLocation()
    }

    private inner class UriResourceLocation : ResourceLocation {
        override fun getDisplayName(): String? {
            return this@UriTextResource.getDisplayName()
        }

        override fun getFile(): File? {
            return sourceFile
        }

        override fun getURI(): URI {
            return sourceUri
        }
    }

    companion object {
        private val SIGNATURE = Hashing.signature(UriTextResource::class.java)
        val DEFAULT_ENCODING: Charset = Charset.forName("utf-8")
        val userAgentString: String

        init {
            val osName = System.getProperty("os.name")
            val osVersion = System.getProperty("os.version")
            val osArch = System.getProperty("os.arch")
            val javaVendor = System.getProperty("java.vendor")
            val javaVersion = SystemProperties.getInstance().getJavaVersion()
            val javaVendorVersion = System.getProperty("java.vm.version")
            userAgentString = String.format(
                "Gradle/%s (%s;%s;%s) (%s;%s;%s)",
                GradleVersion.current().getVersion(),
                osName,
                osVersion,
                osArch,
                javaVendor,
                javaVersion,
                javaVendorVersion
            )
        }

        fun from(description: String, sourceFile: File, resolver: RelativeFilePathResolver): UriTextResource {
            return if (sourceFile.exists()) UriTextResource(description, sourceFile, resolver) else EmptyFileTextResource(description, sourceFile, resolver)
        }

        fun extractCharacterEncoding(contentType: String?, defaultEncoding: Charset): Charset {
            if (contentType == null) {
                return defaultEncoding
            }
            var pos: Int = findFirstParameter(0, contentType)
            if (pos == -1) {
                return defaultEncoding
            }
            val paramName = StringBuilder()
            val paramValue = StringBuilder()
            pos = findNextParameter(pos, contentType, paramName, paramValue)
            while (pos != -1) {
                if (paramName.toString() == "charset" && paramValue.length > 0) {
                    return Charset.forName(paramValue.toString())
                }
                pos = findNextParameter(pos, contentType, paramName, paramValue)
            }
            return defaultEncoding
        }

        private fun findFirstParameter(pos: Int, contentType: String): Int {
            val index = contentType.indexOf(';', pos)
            if (index < 0) {
                return -1
            }
            return index + 1
        }

        private fun findNextParameter(pos: Int, contentType: String, paramName: StringBuilder, paramValue: StringBuilder): Int {
            if (pos >= contentType.length) {
                return -1
            }
            paramName.setLength(0)
            paramValue.setLength(0)
            var separator = contentType.indexOf("=", pos)
            if (separator < 0) {
                separator = contentType.length
            }
            paramName.append(contentType.substring(pos, separator).trim { it <= ' ' })
            if (separator >= contentType.length - 1) {
                return contentType.length
            }

            var startValue = separator + 1
            var endValue: Int
            if (contentType.get(startValue) == '"') {
                startValue++
                var i = startValue
                while (i < contentType.length) {
                    val ch = contentType.get(i)
                    if (ch == '\\' && i < contentType.length - 1 && contentType.get(i + 1) == '"') {
                        paramValue.append('"')
                        i += 2
                    } else if (ch == '"') {
                        break
                    } else {
                        paramValue.append(ch)
                        i++
                    }
                }
                endValue = i + 1
            } else {
                endValue = contentType.indexOf(';', startValue)
                if (endValue < 0) {
                    endValue = contentType.length
                }
                paramValue.append(contentType.substring(startValue, endValue))
            }
            if (endValue < contentType.length && contentType.get(endValue) == ';') {
                endValue++
            }
            return endValue
        }
    }
}
