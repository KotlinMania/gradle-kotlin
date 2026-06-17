/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language.nativeplatform.internal.incremental

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.internal.serialize.Serializer
import java.io.File

class CompilationStateSerializer : Serializer<CompilationState?> {
    private val fileSerializer: Serializer<File?>
    private val hashSerializer: Serializer<HashCode?> = HashCodeSerializer()

    init {
        fileSerializer = BaseSerializerFactory().getSerializerFor<File?>(File::class.java)
    }

    @Throws(Exception::class)
    override fun read(decoder: Decoder): CompilationState? {
        // Deduplicates the include file states, as these are often shared between source files
        val ids: MutableMap<Int?, IncludeFileEdge?> = HashMap<Int?, IncludeFileEdge?>()
        val sourceFileCount = decoder.readSmallInt()
        val builder = ImmutableMap.builder<File?, SourceFileState?>()
        for (i in 0..<sourceFileCount) {
            val sourceFile = fileSerializer.read(decoder)
            val sourceHashCode = hashSerializer.read(decoder)
            val isUnresolved = decoder.readBoolean()
            val includeFileCount = decoder.readSmallInt()
            val includeFileStateBuilder = ImmutableSet.builder<IncludeFileEdge?>()
            for (j in 0..<includeFileCount) {
                val id = decoder.readSmallInt()
                var includeFileState = ids.get(id)
                if (includeFileState == null) {
                    val includePath = decoder.readString()
                    var includedBy: HashCode? = null
                    if (decoder.readBoolean()) {
                        includedBy = hashSerializer.read(decoder)
                    }
                    val resolvedTo = hashSerializer.read(decoder)
                    includeFileState = IncludeFileEdge(includePath, includedBy, resolvedTo)
                    ids.put(id, includeFileState)
                }
                includeFileStateBuilder.add(includeFileState)
            }
            builder.put(sourceFile, SourceFileState(sourceHashCode, isUnresolved, includeFileStateBuilder.build()))
        }
        return CompilationState(builder.build())
    }

    @Throws(Exception::class)
    override fun write(encoder: Encoder, value: CompilationState) {
        // Deduplicates the include file states, as these are often shared between source files
        val ids: MutableMap<IncludeFileEdge?, Int?> = HashMap<IncludeFileEdge?, Int?>()
        encoder.writeSmallInt(value.getFileStates().size)
        for (entry in value.getFileStates().entries) {
            val sourceFileState = entry.value
            fileSerializer.write(encoder, entry.key)
            hashSerializer.write(encoder, sourceFileState.getHash())
            encoder.writeBoolean(sourceFileState.isHasUnresolved())
            encoder.writeSmallInt(sourceFileState.getEdges().size)
            for (includeFileState in sourceFileState.getEdges()) {
                var id = ids.get(includeFileState)
                if (id == null) {
                    id = ids.size
                    ids.put(includeFileState, id)
                    encoder.writeSmallInt(id)
                    encoder.writeString(includeFileState.getIncludePath())
                    if (includeFileState.getIncludedBy() == null) {
                        encoder.writeBoolean(false)
                    } else {
                        encoder.writeBoolean(true)
                        hashSerializer.write(encoder, includeFileState.getIncludedBy())
                    }
                    hashSerializer.write(encoder, includeFileState.getResolvedTo())
                } else {
                    encoder.writeSmallInt(id)
                }
            }
        }
    }
}
