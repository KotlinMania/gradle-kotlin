/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.resources.ResourceException
import org.gradle.internal.file.RelativeFilePathResolver
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import java.io.File
import java.io.Reader
import java.io.StringReader
import java.nio.charset.Charset

/**
 * A [UriTextResource] that is empty and maps to an actual (non-null) file location
 * (which does not actually exist in the file system).
 */
class EmptyFileTextResource internal constructor(description: String, sourceFile: File, resolver: RelativeFilePathResolver) : UriTextResource(description, sourceFile, resolver) {
    override fun isContentCached(): Boolean {
        return true
    }

    override fun getHasEmptyContent(): Boolean {
        return true
    }

    override fun getFile(): File? {
        // Returns null as there is no file that contains this resource's contents,
        // however {@link ResourceLocation#getFile()} would still return the given `sourceFile`
        return null
    }

    override fun getCharset(): Charset? {
        return null
    }

    override fun getAsReader(): Reader {
        return StringReader("")
    }

    @Throws(ResourceException::class)
    override fun getContentHash(): HashCode {
        return SIGNATURE
    }

    override fun getText(): String {
        return ""
    }

    override fun getExists(): Boolean {
        return true
    }

    companion object {
        private val SIGNATURE = Hashing.signature(EmptyFileTextResource::class.java)
    }
}
