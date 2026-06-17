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

import org.gradle.api.resources.MissingResourceException
import org.gradle.api.resources.ResourceException
import org.gradle.internal.DisplayName
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import java.io.File
import java.io.Reader
import java.io.StringReader
import java.nio.charset.Charset

class CachingTextResource(private val resource: TextResource) : TextResource {
    private var content: String? = null
    private var contentHash: HashCode? = null

    override fun getDisplayName(): String? {
        return resource.getDisplayName()
    }

    override fun getLongDisplayName(): DisplayName? {
        return resource.getLongDisplayName()
    }

    override fun getShortDisplayName(): DisplayName? {
        return resource.getShortDisplayName()
    }

    override fun getLocation(): ResourceLocation? {
        return resource.getLocation()
    }

    override fun getFile(): File? {
        return resource.getFile()
    }

    override fun getCharset(): Charset? {
        return resource.getCharset()
    }

    override fun isContentCached(): Boolean {
        return true
    }

    override fun getExists(): Boolean {
        try {
            maybeFetch()
        } catch (e: MissingResourceException) {
            return false
        }
        return true
    }

    override fun getHasEmptyContent(): Boolean {
        maybeFetch()
        return content!!.length == 0
    }

    override fun getText(): String? {
        maybeFetch()
        return content
    }

    @Throws(ResourceException::class)
    override fun getContentHash(): HashCode? {
        maybeFetch()
        return contentHash
    }

    override fun getAsReader(): Reader {
        maybeFetch()
        return StringReader(content)
    }

    private fun maybeFetch() {
        if (content == null) {
            content = resource.getText()
            val hasher = Hashing.newPrimitiveHasher()
            hasher.putHash(SIGNATURE)
            hasher.putString(content!!)
            contentHash = hasher.hash()
        }
    }

    companion object {
        private val SIGNATURE = Hashing.signature(CachingTextResource::class.java)
    }
}
