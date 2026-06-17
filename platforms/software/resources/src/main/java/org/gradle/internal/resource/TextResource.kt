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

/**
 * A `Resource` that has text content.
 */
interface TextResource : Resource {
    /**
     * Returns the location of this resource.
     */
    val location: ResourceLocation?

    /**
     * A long display name for this resource. The display name must use absolute paths and assume no context.
     */
    val longDisplayName: DisplayName?

    /**
     * A short display name for this resource. The display name may use relative paths.
     */
    val shortDisplayName: DisplayName?

    /**
     * Returns a file that contains the same content as this resource, encoded using the charset specified by [.getCharset].
     * Not all resources are available as a file.
     * Note that this method may return null when [ResourceLocation.getFile] returns non-null, when the contents are different.
     *
     * @return A file containing this resource. Returns null if this resource is not available as a file.
     */
    val file: File?

    /**
     * Returns the charset use to encode the file containing the resource's content, as returned by [.getFile].
     *
     * @return The charset. Returns null when this resource is not available as a file.
     */
    val charset: Charset?

    /**
     * Returns true when the content of this resource is cached in-heap or uses a hard-coded value. Returns false when the content requires IO on each query.
     *
     *
     * When this method returns false, the caller should avoid querying the content more than once.
     */
    val isContentCached: Boolean

    @get:Throws(ResourceException::class)
    val exists: Boolean

    @get:Throws(ResourceException::class)
    val hasEmptyContent: Boolean

    @get:Throws(ResourceException::class)
    val asReader: Reader?

    @get:Throws(ResourceException::class)
    val text: String?

    @get:Throws(ResourceException::class)
    val contentHash: HashCode?
}
