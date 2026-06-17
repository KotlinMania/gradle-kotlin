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
package org.gradle.api.internal.artifacts.ivyservice.modulecache

import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder

internal class ModuleMetadataCacheEntrySerializer : AbstractSerializer<ModuleMetadataCacheEntry?>() {
    @Throws(Exception::class)
    override fun write(encoder: Encoder, value: ModuleMetadataCacheEntry) {
        encoder.writeByte(value.type)
        when (value.type) {
            ModuleMetadataCacheEntry.Companion.TYPE_MISSING -> encoder.writeLong(value.createTimestamp)
            ModuleMetadataCacheEntry.Companion.TYPE_PRESENT -> {
                encoder.writeBoolean(value.isChanging)
                encoder.writeLong(value.createTimestamp)
            }

            else -> throw IllegalArgumentException("Don't know how to serialize meta-data entry: " + value)
        }
    }

    @Throws(Exception::class)
    override fun read(decoder: Decoder): ModuleMetadataCacheEntry? {
        val type = decoder.readByte()
        when (type) {
            ModuleMetadataCacheEntry.Companion.TYPE_MISSING -> {
                val createTimestamp = decoder.readLong()
                return MissingModuleCacheEntry(createTimestamp)
            }

            ModuleMetadataCacheEntry.Companion.TYPE_PRESENT -> {
                val isChanging = decoder.readBoolean()
                createTimestamp = decoder.readLong()
                return ModuleMetadataCacheEntry(ModuleMetadataCacheEntry.Companion.TYPE_PRESENT, isChanging, createTimestamp)
            }

            else -> throw IllegalArgumentException("Don't know how to deserialize meta-data entry of type " + type)
        }
    }

    public override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        return super.equals(o)
    }

    public override fun hashCode(): Int {
        return super.hashCode()
    }
}
