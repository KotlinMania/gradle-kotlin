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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.internal.component.model.PersistentModuleSource
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import java.io.IOException

class ModuleDescriptorHashCodec : PersistentModuleSource.Codec<ModuleDescriptorHashModuleSource?> {
    @Throws(IOException::class)
    override fun encode(moduleSource: ModuleDescriptorHashModuleSource, encoder: Encoder) {
        encoder.writeBinary(moduleSource.getDescriptorHash().toByteArray())
        encoder.writeBoolean(moduleSource.isChangingModule())
    }

    @Throws(IOException::class)
    override fun decode(decoder: Decoder): ModuleDescriptorHashModuleSource {
        return ModuleDescriptorHashModuleSource(
            HashCode.fromBytes(decoder.readBinary()!!),
            decoder.readBoolean()
        )
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        return o != null && javaClass == o.javaClass
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }
}
