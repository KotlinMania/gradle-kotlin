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
package org.gradle.plugins.ide.idea.model

import com.google.common.base.Objects

/**
 * Represents a path in a format as used often in ipr and iml files.
 */
open class Path @JvmOverloads constructor(
    /**
     * The url of the path. Must not be null.
     */
    val url: String?,
    /**
     * Canonical url.
     */
    val canonicalUrl: String = url!!,
    /**
     * The relative path of the path. Must not be null.
     */
    val relPath: String? = null
) {
    override fun toString(): String {
        return "Path{" + "url='" + url + "\'" + "}"
    }


    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is Path) {
            return false
        }
        val path = o
        return Objects.equal(canonicalUrl, path.canonicalUrl)
    }

    override fun hashCode(): Int {
        return canonicalUrl.hashCode()
    }
}
