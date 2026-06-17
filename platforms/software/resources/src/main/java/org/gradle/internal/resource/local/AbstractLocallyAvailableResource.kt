/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.resource.local

import org.gradle.internal.Factory
import org.gradle.internal.hash.HashCode

abstract class AbstractLocallyAvailableResource : LocallyAvailableResource {
    private var factory: Factory<HashCode?>? = null

    // Calculated on demand
    private var sha1: HashCode? = null
    private var contentLength: Long? = null
    private var lastModified: Long? = null

    protected constructor(factory: Factory<HashCode?>) {
        this.factory = factory
    }

    protected constructor(sha1: HashCode?) {
        this.sha1 = sha1
    }

    override fun toString(): String {
        return getDisplayName()
    }

    override fun getDisplayName(): String {
        return getFile().getPath()
    }

    override fun getSha1(): HashCode? {
        if (sha1 == null) {
            sha1 = factory!!.create()
        }
        return sha1
    }

    override fun getContentLength(): Long {
        if (contentLength == null) {
            contentLength = getFile().length()
        }
        return contentLength!!
    }

    override fun getLastModified(): Long {
        if (lastModified == null) {
            lastModified = getFile().lastModified()
        }
        return lastModified!!
    }
}
