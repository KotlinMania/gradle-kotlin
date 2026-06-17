/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental.deps

import com.google.common.collect.ImmutableSet
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.ints.IntSets
import org.gradle.api.internal.cache.StringInterner
import org.gradle.internal.serialize.AbstractCollectionSerializer.read
import org.gradle.internal.serialize.AbstractCollectionSerializer.write
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.HashCodeSerializer.read
import org.gradle.internal.serialize.HashCodeSerializer.write
import org.gradle.internal.serialize.HierarchicalNameSerializer.read
import org.gradle.internal.serialize.HierarchicalNameSerializer.write
import org.gradle.internal.serialize.InterningStringSerializer
import org.gradle.internal.serialize.MapSerializer.read
import org.gradle.internal.serialize.MapSerializer.write
import org.gradle.internal.serialize.Serializer.read
import org.gradle.internal.serialize.Serializer.write
import org.gradle.internal.serialize.SetSerializer

/**
 * An immutable set of details extracted from a class file.
 */
class ClassAnalysis(val className: String?, privateClassDependencies: MutableSet<String?>, accessibleClassDependencies: MutableSet<String?>, val dependencyToAllReason: String?, constants: IntSet) {
    val privateClassDependencies: MutableSet<String?>
    val accessibleClassDependencies: MutableSet<String?>
    val constants: IntSet

    init {
        this.privateClassDependencies = ImmutableSet.copyOf<String?>(privateClassDependencies)
        this.accessibleClassDependencies = ImmutableSet.copyOf<String?>(accessibleClassDependencies)
        this.constants = if (constants.isEmpty()) IntSets.EMPTY_SET else constants
    }

    class Serializer(private val interner: StringInterner) : AbstractSerializer<ClassAnalysis?>() {
        private val stringSetSerializer: SetSerializer<String?>

        init {
            stringSetSerializer = SetSerializer<String?>(InterningStringSerializer(interner), false)
        }

        @Throws(Exception::class)
        override fun read(decoder: Decoder): ClassAnalysis? {
            val className = interner.intern(decoder.readString()!!)
            val dependencyToAllReason = decoder.readNullableString()
            val privateClasses = stringSetSerializer.read(decoder)
            val accessibleClasses = stringSetSerializer.read(decoder)
            val constants: IntSet? = IntSetSerializer.Companion.INSTANCE.read(decoder)
            return ClassAnalysis(className, privateClasses!!, accessibleClasses!!, dependencyToAllReason, constants!!)
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: ClassAnalysis) {
            encoder.writeString(value.className)
            encoder.writeNullableString(value.dependencyToAllReason)
            stringSetSerializer.write(encoder, value.privateClassDependencies)
            stringSetSerializer.write(encoder, value.accessibleClassDependencies)
            IntSetSerializer.Companion.INSTANCE.write(encoder, value.constants)
        }
    }
}
