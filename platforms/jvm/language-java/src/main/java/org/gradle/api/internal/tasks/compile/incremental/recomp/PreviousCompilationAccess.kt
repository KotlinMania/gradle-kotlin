/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental.recomp

import org.gradle.api.internal.cache.StringInterner
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class PreviousCompilationAccess(private val interner: StringInterner?) {
    fun readPreviousCompilationData(source: File): PreviousCompilationData? {
        try {
            KryoBackedDecoder(FileInputStream(source)).use { encoder ->
                return PreviousCompilationData.Serializer(interner).read(encoder)
            }
        } catch (e: Exception) {
            throw IllegalStateException("Could not read previous compilation result.", e)
        }
    }

    fun writePreviousCompilationData(data: PreviousCompilationData, target: File) {
        try {
            KryoBackedEncoder(FileOutputStream(target)).use { encoder ->
                PreviousCompilationData.Serializer(interner).write(encoder, data)
            }
        } catch (e: Exception) {
            throw IllegalStateException("Could not store compilation result", e)
        }
    }
}
