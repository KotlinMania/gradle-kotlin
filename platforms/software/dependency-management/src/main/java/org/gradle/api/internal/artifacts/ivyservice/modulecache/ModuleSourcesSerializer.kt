/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.modulecache

import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.component.model.ModuleSource
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.component.model.MutableModuleSources
import org.gradle.internal.component.model.PersistentModuleSource
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.IOException
import java.util.function.Consumer

@ServiceScope(Scope.BuildTree::class)
class ModuleSourcesSerializer(private val moduleSourceCodecs: MutableMap<Int?, PersistentModuleSource.Codec<out PersistentModuleSource?>?>) : Serializer<ModuleSources?> {
    @Throws(IOException::class)
    override fun write(encoder: Encoder, value: ModuleSources) {
        value.withSources(Consumer { source: ModuleSource? ->
            try {
                if (source is PersistentModuleSource) {
                    val persistentModuleSource = source
                    val codecId = assertValidId(persistentModuleSource.codecId)
                    encoder.writeSmallInt(codecId)
                    val codec = uncheckedCast<PersistentModuleSource.Codec<PersistentModuleSource?>?>(moduleSourceCodecs.get(codecId))
                    codec!!.encode(persistentModuleSource, encoder)
                }
            } catch (e: IOException) {
                throw throwAsUncheckedException(e)
            }
        })
        encoder.writeSmallInt(0) // end of sources
    }

    private fun assertValidId(codecId: Int): Int {
        assert(codecId >= 0) { "Module source must have a strictly positive source id" }
        return codecId
    }

    @Throws(IOException::class)
    override fun read(decoder: Decoder): ModuleSources {
        val sources = MutableModuleSources()
        var codecId: Int
        while ((decoder.readSmallInt().also { codecId = it }) > 0) {
            sources.add(moduleSourceCodecs.get(codecId)!!.decode(decoder)!!)
        }
        return sources
    }
}
