/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.base.Objects
import org.gradle.api.Describable
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.jspecify.annotations.NullMarked
import java.net.URI
import java.net.URISyntaxException

/**
 * An immutable resource name. Resources are arranged in a hierarchy. Names may be relative, or absolute with some opaque root resource.
 */
@NullMarked
open class ExternalResourceName private constructor(
    private val encodedRoot: String?,
    /**
     * Returns the path for this resource. The '/' character is used to separate the elements of the path.
     */
    val path: String, private val encodedQuery: String = ""
) : Describable {
    constructor(uri: URI) : this(encodeRoot(uri), extractPath(uri), extractQuery(uri))

    constructor(path: String) : this(null, path, "")

    constructor(parent: URI, path: String) : this(encodeRoot(parent), combine(parent, path), "")

    override fun getDisplayName(): String {
        return this.displayable
    }

    open val shortDisplayName: String
        get() {
            val lastSlash = path.lastIndexOf('/')
            return if (lastSlash == -1) this.displayable else path.substring(lastSlash + 1)
        }

    override fun toString(): String {
        return getDisplayName()
    }

    val uri: URI
        /**
         * Returns a URI that represents this resource.
         */
        get() {
            try {
                if (encodedRoot == null) {
                    return URI(encode(path, false) + encodedQuery)
                }
                return URI(encodedRoot + encode(path, true) + encodedQuery)
            } catch (e: URISyntaxException) {
                throw throwAsUncheckedException(e)
            }
        }

    val displayable: String
        /**
         * Returns the 'displayable' name, which is the opaque root + the encoded path of the name.
         */
        get() {
            if (encodedRoot == null) {
                return encode(path, false)
            }
            return encodedRoot + encode(path, true)
        }

    val root: ExternalResourceName
        /**
         * Returns the root name for this name.
         */
        get() = ExternalResourceName(encodedRoot, if (path.startsWith("/")) "/" else "")

    /**
     * Resolves the given path relative to this name. The path can be a relative path or an absolute path. The '/' character is used to separate the elements of the path.
     */
    fun resolve(path: String): ExternalResourceName {
        val parts: MutableList<String> = ArrayList<String>()
        val leadingSlash: Boolean
        val trailingSlash = path.endsWith("/")
        if (path.startsWith("/")) {
            leadingSlash = true
        } else {
            leadingSlash = this.path.startsWith("/")
            append(this.path, parts)
        }
        append(path, parts)
        val newPath = join(leadingSlash, trailingSlash, parts)
        return ExternalResourceName(encodedRoot, newPath)
    }

    /**
     * Appends the given text to the end of this path.
     */
    fun append(path: String): ExternalResourceName {
        return ExternalResourceName(encodedRoot, this.path + path)
    }

    override fun equals(obj: Any): Boolean {
        if (obj === this) {
            return true
        }
        if (obj == null || obj.javaClass != javaClass) {
            return false
        }
        val other = obj as ExternalResourceName
        return Objects.equal(encodedRoot, other.encodedRoot) && path == other.path
    }

    override fun hashCode(): Int {
        return (if (encodedRoot == null) 0 else encodedRoot.hashCode()) xor path.hashCode()
    }

    private fun join(leadingSlash: Boolean, trailingSlash: Boolean, parts: MutableList<String>): String {
        if (parts.isEmpty() && leadingSlash) {
            return "/"
        }
        val builder = StringBuilder()
        for (part in parts) {
            if (builder.length > 0 || leadingSlash) {
                builder.append("/")
            }
            builder.append(part)
        }
        if (trailingSlash) {
            builder.append("/")
        }
        return builder.toString()
    }

    private fun append(path: String, parts: MutableList<String>) {
        var pos = 0
        while (pos < path.length) {
            val end = path.indexOf('/', pos)
            val part: String?
            if (end < 0) {
                part = path.substring(pos)
                pos = path.length
            } else {
                part = path.substring(pos, end)
                pos = end + 1
            }
            if (part.length == 0 || part == ".") {
                continue
            }
            if (part == "..") {
                parts.removeAt(parts.size - 1)
                continue
            }
            parts.add(part)
        }
    }

    companion object {
        private fun combine(parent: URI, path: String): String {
            val parentPath: String = extractPath(parent)
            val childPath = if (path.startsWith("/")) path.substring(1) else path
            if (childPath.length == 0) {
                return parentPath
            } else if (parentPath.endsWith("/")) {
                return parentPath + childPath
            } else {
                return parentPath + "/" + childPath
            }
        }

        private fun isFileOnHost(uri: URI): Boolean {
            return "file" == uri.getScheme() && uri.getPath().startsWith("//")
        }

        private fun extractPath(parent: URI): String {
            if (isFileOnHost(parent)) {
                return URI.create(parent.getPath()).getPath()
            }
            return parent.getPath()
        }

        private fun extractQuery(uri: URI): String {
            val rawQuery = uri.getRawQuery()
            if (rawQuery == null) {
                return ""
            }
            return "?" + rawQuery
        }

        private fun encodeRoot(uri: URI): String {
            //based on reversing the operations performed by URI.toString()
            requireNotNull(uri.getPath()) { String.format("Cannot create resource name from non-hierarchical URI '%s'.", uri) }

            val builder = StringBuilder(uri.toString())

            val fragment = uri.getRawFragment()
            if (fragment != null) {
                val index = builder.lastIndexOf("#" + fragment)
                if (index < 0) {
                    throw RuntimeException(String.format("Can't locate fragment in URI: %s", uri))
                }
                builder.delete(index, builder.length)
            }

            if (uri.isOpaque()) {
                return builder.toString()
            }

            val query = uri.getRawQuery()
            if (query != null) {
                val index = builder.lastIndexOf("?" + query)
                if (index < 0) {
                    throw RuntimeException(String.format("Can't locate query in URI: %s", uri))
                }
                builder.delete(index, builder.length)
            }

            var path = uri.getRawPath()
            if (path != null && isFileOnHost(uri)) {  //if file URI
                path = URI.create(path).getRawPath() //remove hostname from path
            }
            if (path != null) {
                val index = builder.lastIndexOf(path)
                if (index < 0) {
                    throw RuntimeException(String.format("Can't locate path in URI: %s", uri))
                }
                builder.delete(index, builder.length)
            }

            return builder.toString()
        }

        private fun encode(path: String, isPathSeg: Boolean): String {
            val builder = StringBuilder()
            for (i in 0..<path.length) {
                val ch = path.get(i)
                if (isLowerCaseChar(ch) ||
                    isUpperCaseChar(ch) ||
                    isDigit(ch)
                ) {
                    builder.append(ch)
                } else if (ch == '/' || ch == '@' || (isPathSeg && ch == ':') || ch == '.' || ch == '-' || ch == '_' || ch == '~' || ch == '!' || ch == '$' || ch == '&' || ch == '\'' || ch == '(' || ch == ')' || ch == '*' || ch == '+' || ch == ',' || ch == ';' || ch == '=') {
                    builder.append(ch)
                } else {
                    if (ch.code <= 0x7F) {
                        escapeByte(ch.code, builder)
                    } else if (ch.code <= 0x7FF) {
                        escapeByte(0xC0 or ((ch.code shr 6) and 0x1F), builder)
                        escapeByte(0x80 or (ch.code and 0x3F), builder)
                    } else {
                        escapeByte(0xE0 or ((ch.code shr 12) and 0x1F), builder)
                        escapeByte(0x80 or ((ch.code shr 6) and 0x3F), builder)
                        escapeByte(0x80 or (ch.code and 0x3F), builder)
                    }
                }
            }
            return builder.toString()
        }

        private fun isDigit(ch: Char): Boolean {
            return ch >= '0' && ch <= '9'
        }

        private fun isUpperCaseChar(ch: Char): Boolean {
            return ch >= 'A' && ch <= 'Z'
        }

        private fun isLowerCaseChar(ch: Char): Boolean {
            return ch >= 'a' && ch <= 'z'
        }

        private fun escapeByte(ch: Int, builder: StringBuilder) {
            builder.append('%')
            builder.append(Character.forDigit(ch shr 4 and 0xFF, 16).uppercaseChar())
            builder.append(Character.forDigit(ch and 0xF, 16).uppercaseChar())
        }
    }
}
