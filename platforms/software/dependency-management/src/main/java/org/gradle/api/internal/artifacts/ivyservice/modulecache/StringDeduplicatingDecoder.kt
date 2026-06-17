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
package org.gradle.api.internal.artifacts.ivyservice.modulecache

import com.google.common.collect.Interner
import org.gradle.internal.serialize.Decoder
import java.io.Closeable
import java.io.EOFException
import java.io.IOException

internal class StringDeduplicatingDecoder(private val delegate: Decoder, private val stringInterner: Interner<String?>) : Decoder, Closeable {
    val inputStream: InputStream
        get() = delegate.inputStream

    @Throws(EOFException::class, IOException::class)
    override fun readLong(): Long {
        return delegate.readLong()
    }

    @Throws(EOFException::class, IOException::class)
    override fun readSmallLong(): Long {
        return delegate.readSmallLong()
    }

    @Throws(EOFException::class, IOException::class)
    override fun readInt(): Int {
        return delegate.readInt()
    }

    @Throws(EOFException::class, IOException::class)
    override fun readSmallInt(): Int {
        return delegate.readSmallInt()
    }

    @Throws(IOException::class)
    override fun readNullableSmallInt(): Int? {
        return delegate.readNullableSmallInt()
    }

    @Throws(EOFException::class, IOException::class)
    override fun readShort(): Short {
        return delegate.readShort()
    }

    @Throws(EOFException::class, IOException::class)
    override fun readFloat(): Float {
        return delegate.readFloat()
    }

    @Throws(EOFException::class, IOException::class)
    override fun readDouble(): Double {
        return delegate.readDouble()
    }

    @Throws(EOFException::class, IOException::class)
    override fun readBoolean(): Boolean {
        return delegate.readBoolean()
    }

    @Throws(EOFException::class, IOException::class)
    override fun readString(): String? {
        return stringInterner.intern(delegate.readString())
    }

    @Throws(EOFException::class, IOException::class)
    override fun readNullableString(): String? {
        var str = delegate.readNullableString()
        if (str != null) {
            str = stringInterner.intern(str)
        }
        return str
    }

    @Throws(EOFException::class, IOException::class)
    override fun readByte(): Byte {
        return delegate.readByte()
    }

    @Throws(EOFException::class, IOException::class)
    override fun readBytes(buffer: ByteArray?) {
        delegate.readBytes(buffer)
    }

    @Throws(EOFException::class, IOException::class)
    override fun readBytes(buffer: ByteArray?, offset: Int, count: Int) {
        delegate.readBytes(buffer, offset, count)
    }

    @Throws(EOFException::class, IOException::class)
    override fun readBinary(): ByteArray? {
        return delegate.readBinary()
    }

    @Throws(EOFException::class, IOException::class)
    override fun skipBytes(count: Long) {
        delegate.skipBytes(count)
    }

    @Throws(EOFException::class, Exception::class)
    override fun <T> decodeChunked(decodeAction: Decoder.DecodeAction<Decoder?, T?>?): T? {
        throw UnsupportedOperationException()
    }

    @Throws(EOFException::class, IOException::class)
    override fun skipChunked() {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun close() {
        (delegate as Closeable).close()
    }
}
