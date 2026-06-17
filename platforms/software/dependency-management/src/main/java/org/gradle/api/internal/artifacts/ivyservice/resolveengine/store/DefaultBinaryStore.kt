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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.store

import org.gradle.cache.internal.BinaryStore
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.file.RandomAccessFileInputStream
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.kryo.StringDeduplicatingKryoBackedDecoder
import org.gradle.internal.serialize.kryo.StringDeduplicatingKryoBackedEncoder
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.RandomAccessFile

internal class DefaultBinaryStore(var file: File) : BinaryStore, Closeable {
    private var encoder: StringDeduplicatingKryoBackedEncoder? = null
    private var offset: Long = -1

    override fun write(write: BinaryStore.WriteAction) {
        if (encoder == null) {
            try {
                encoder = StringDeduplicatingKryoBackedEncoder(FileOutputStream(file))
            } catch (e: FileNotFoundException) {
                throw throwAsUncheckedException(e)
            }
        }
        if (offset == -1L) {
            offset = encoder!!.getWritePosition()
            check(offset != Int.MAX_VALUE.toLong()) {
                ("Unable to write to binary store. "
                        + "The bytes offset has reached a point where using it is unsafe. Please report this error.")
            }
        }
        try {
            write.write(encoder)
        } catch (e: Exception) {
            throw RuntimeException("Problems writing to " + diagnose(), e)
        }
    }

    private fun diagnose(): String {
        return toString() + " (exist: " + file.exists() + ")"
    }

    override fun toString(): String {
        return "Binary store in " + file
    }

    override fun done(): BinaryStore.BinaryData {
        try {
            if (encoder != null) {
                encoder!!.done()
                encoder!!.flush()
            }
            return SimpleBinaryData(file, offset)
        } finally {
            offset = -1
        }
    }

    override fun close() {
        try {
            if (encoder != null) {
                encoder!!.close()
            }
        } finally {
            if (file != null) {
                file.delete()
            }
            encoder = null
            file = null
        }
    }

    val size: Long
        get() = file.length()

    val isInUse: Boolean
        get() = offset != -1L

    private class SimpleBinaryData(private val inputFile: File, private val offset: Long) : BinaryStore.BinaryData {
        private var decoder: Decoder? = null
        private var resources: CompositeStoppable? = null

        override fun <T> read(readAction: BinaryStore.ReadAction<T?>): T? {
            try {
                if (decoder == null) {
                    val randomAccess = RandomAccessFile(inputFile, "r")
                    randomAccess.seek(offset)
                    decoder = StringDeduplicatingKryoBackedDecoder(RandomAccessFileInputStream(randomAccess))
                    resources = CompositeStoppable().add(randomAccess, decoder!!)
                }
                return readAction.read(decoder)
            } catch (e: Exception) {
                throw RuntimeException("Problems reading data from " + toString(), e)
            }
        }

        override fun close() {
            try {
                if (resources != null) {
                    resources!!.stop()
                }
            } catch (e: Exception) {
                throw RuntimeException("Problems cleaning resources of " + toString(), e)
            } finally {
                decoder = null
                resources = null
            }
        }

        override fun toString(): String {
            return "Binary store in " + inputFile + " offset " + offset + " exists? " + inputFile.exists()
        }
    }
}
