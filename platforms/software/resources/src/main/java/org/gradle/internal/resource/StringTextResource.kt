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

import org.gradle.api.resources.ResourceException
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import java.io.File
import java.io.Reader
import java.io.StringReader
import java.net.URI
import java.nio.charset.Charset

class StringTextResource(private val displayName: String, private val contents: CharSequence) : TextResource {
    private var contentHash: HashCode? = null

    override fun getDisplayName(): String {
        return displayName
    }

    override fun getLongDisplayName(): DisplayName {
        return Describables.of(displayName)
    }

    override fun getShortDisplayName(): DisplayName {
        return getLongDisplayName()
    }

    override fun isContentCached(): Boolean {
        return true
    }

    override fun getHasEmptyContent(): Boolean {
        return contents.length == 0
    }

    override fun getAsReader(): Reader {
        return StringReader(getText())
    }

    override fun getText(): String {
        return contents.toString()
    }

    @Throws(ResourceException::class)
    override fun getContentHash(): HashCode {
        if (contentHash == null) {
            val hasher = Hashing.newPrimitiveHasher()
            hasher.putHash(SIGNATURE)
            hasher.putString(getText())
            contentHash = hasher.hash()
        }
        return contentHash!!
    }

    override fun getFile(): File? {
        return null
    }

    override fun getCharset(): Charset? {
        return null
    }

    override fun getLocation(): ResourceLocation {
        return StringResourceLocation(displayName)
    }

    override fun getExists(): Boolean {
        return true
    }

    private class StringResourceLocation(private val displayName: String?) : ResourceLocation {
        override fun getDisplayName(): String? {
            return displayName
        }

        override fun getFile(): File? {
            return null
        }

        override fun getURI(): URI? {
            return null
        }
    }

    companion object {
        private val SIGNATURE = Hashing.signature(StringTextResource::class.java)
    }
}
