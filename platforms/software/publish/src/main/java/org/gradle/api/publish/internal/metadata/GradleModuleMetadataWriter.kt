/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.publish.internal.metadata

import com.google.gson.stream.JsonWriter
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import java.io.IOException
import java.io.Writer

/**
 *
 * The Gradle module metadata file generator is responsible for generating a JSON file
 * describing module metadata. In particular, this file format is capable of handling different
 * variants with different dependency sets.
 *
 *
 * Whenever you change this class, make sure you also:
 *
 *
 *  * Update the corresponding [module metadata parser][GradleModuleMetadataParser]
 *  * Update the module metadata specification (platforms/documentation/docs/src/docs/design/gradle-module-metadata-specification.md)
 *  * Update [the module metadata serializer][org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetadataSerializer]
 *  * Add a sample for the module metadata serializer test, to make sure that serialized metadata is idempotent
 *
 */
class GradleModuleMetadataWriter(
    private val buildInvocationScopeId: BuildInvocationScopeId,
    private val checksumService: ChecksumService?
) {
    @Throws(IOException::class)
    fun writeTo(writer: Writer, metadata: ModuleMetadataSpec) {
        // Write the output

        val jsonWriter = JsonWriter(writer)
        jsonWriter.setHtmlSafe(false)
        jsonWriter.setIndent("  ")

        ModuleMetadataJsonWriter(
            jsonWriter,
            metadata,
            if (metadata.mustIncludeBuildId) buildInvocationScopeId.getId().asString() else null,
            checksumService
        ).write()

        jsonWriter.flush()
        writer.append('\n')
    }
}
