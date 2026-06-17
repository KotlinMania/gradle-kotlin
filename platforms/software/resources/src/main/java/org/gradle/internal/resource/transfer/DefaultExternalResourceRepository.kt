/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.internal.resource.transfer

import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceRepository


class DefaultExternalResourceRepository(
    private val name: String?,
    private val accessor: ExternalResourceAccessor?,
    private val uploader: ExternalResourceUploader?,
    private val lister: ExternalResourceLister?
) : ExternalResourceRepository {
    override fun withProgressLogging(): ExternalResourceRepository {
        return this
    }

    override fun resource(resource: ExternalResourceName?, revalidate: Boolean): ExternalResource {
        return AccessorBackedExternalResource(resource, accessor, uploader, lister, revalidate)
    }

    override fun resource(resource: ExternalResourceName?): ExternalResource {
        return resource(resource, false)
    }

    override fun toString(): String {
        return name!!
    }
}
