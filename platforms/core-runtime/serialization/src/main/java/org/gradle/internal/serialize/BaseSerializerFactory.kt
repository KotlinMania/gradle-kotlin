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
package org.gradle.internal.serialize

import com.google.common.base.Objects
import com.google.common.collect.ImmutableMap
import org.gradle.internal.Cast
import org.gradle.internal.hash.HashCode
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.file.Path
import java.nio.file.Paths

class BaseSerializerFactory {
    fun <T> getSerializerFor(type: Class<T?>): Serializer<T?> {
        if (type == String::class.java) {
            return Cast.uncheckedNonnullCast<Serializer<T?>>(STRING_SERIALIZER)
        }
        if (type == Long::class.java) {
            return Cast.uncheckedNonnullCast<Serializer<T?>>(LONG_SERIALIZER)
        }
        if (type == File::class.java) {
            return Cast.uncheckedNonnullCast<Serializer<T?>>(FILE_SERIALIZER)
        }
        if (type == ByteArray::class.java) {
            return Cast.uncheckedNonnullCast<Serializer<T?>>(BYTE_ARRAY_SERIALIZER)
        }
        if (type.isEnum()) {
            return Cast.uncheckedNonnullCast<Serializer<T?>>(EnumSerializer<Enum<*>?>(Cast.uncheckedNonnullCast<Class<Enum<*>?>?>(type)))
        }
        if (type == Boolean::class.java) {
            return Cast.uncheckedNonnullCast<Serializer<T?>>(BOOLEAN_SERIALIZER)
        }
        if (Throwable::class.java.isAssignableFrom(type)) {
            return Cast.uncheckedNonnullCast<Serializer<T?>>(THROWABLE_SERIALIZER)
        }
        if (HashCode::class.java.isAssignableFrom(type)) {
            return Cast.uncheckedNonnullCast<Serializer<T?>>(HASHCODE_SERIALIZER)
        }
        if (Path::class.java.isAssignableFrom(type)) {
            return Cast.uncheckedNonnullCast<Serializer<T?>>(PATH_SERIALIZER)
        }
        return DefaultSerializer<T?>(type.getClassLoader())
    }

    private class EnumSerializer<T : Enum<*>?>(private val type: Class<T?>) : AbstractSerializer<T?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): T? {
            return type.getEnumConstants()[decoder.readSmallInt()]
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: T?) {
            encoder.writeSmallInt(value!!.ordinal.toByte().toInt())
        }

        override fun equals(obj: Any?): Boolean {
            if (!super.equals(obj)) {
                return false
            }

            val rhs = obj as EnumSerializer<*>
            return Objects.equal(type, rhs.type)
        }

        override fun hashCode(): Int {
            return Objects.hashCode(super.hashCode(), type)
        }
    }

    private class LongSerializer : AbstractSerializer<Long?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): Long {
            return decoder.readLong()
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: Long) {
            encoder.writeLong(value)
        }
    }

    private class StringSerializer : AbstractSerializer<String?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): String? {
            return decoder.readString()
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: String?) {
            encoder.writeString(value)
        }
    }

    private class FileSerializer : AbstractSerializer<File?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): File {
            return File(decoder.readString())
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: File) {
            encoder.writeString(value.getPath())
        }
    }

    private class PathSerializer : AbstractSerializer<Path?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): Path {
            return Paths.get(decoder.readString())
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: Path) {
            encoder.writeString(value.toString())
        }
    }

    private class ByteArraySerializer : AbstractSerializer<ByteArray?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): ByteArray? {
            return decoder.readBinary()
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: ByteArray?) {
            encoder.writeBinary(value)
        }
    }

    private class StringMapSerializer : AbstractSerializer<MutableMap<String?, String?>?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): MutableMap<String?, String?> {
            val pairs = decoder.readSmallInt()
            val builder = ImmutableMap.builder<String?, String?>()
            for (i in 0..<pairs) {
                builder.put(decoder.readString(), decoder.readString())
            }
            return builder.build()
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: MutableMap<String?, String?>) {
            encoder.writeSmallInt(value.size)
            for (entry in value.entries) {
                encoder.writeString(entry.key)
                encoder.writeString(entry.value)
            }
        }
    }

    private class BooleanSerializer : AbstractSerializer<Boolean?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): Boolean {
            return decoder.readBoolean()
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: Boolean) {
            encoder.writeBoolean(value)
        }
    }

    private class ByteSerializer : AbstractSerializer<Byte?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): Byte {
            return decoder.readByte()
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: Byte) {
            encoder.writeByte(value)
        }
    }

    private class ShortSerializer : AbstractSerializer<Short?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): Short {
            return decoder.readShort()
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: Short) {
            encoder.writeShort(value)
        }
    }

    private class CharSerializer : AbstractSerializer<Char?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): Char {
            return decoder.readInt().toChar()
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: Char) {
            encoder.writeInt(value.code)
        }
    }

    private class IntegerSerializer : AbstractSerializer<Int?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): Int {
            return decoder.readInt()
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: Int) {
            encoder.writeInt(value)
        }
    }

    private class FloatSerializer : AbstractSerializer<Float?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): Float {
            return decoder.readFloat()
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: Float) {
            encoder.writeFloat(value)
        }
    }

    private class DoubleSerializer : AbstractSerializer<Double?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): Double {
            return decoder.readDouble()
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: Double) {
            encoder.writeDouble(value)
        }
    }

    private class BigIntegerSerializer : AbstractSerializer<BigInteger?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): BigInteger {
            return BigInteger(decoder.readBinary())
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: BigInteger) {
            encoder.writeBinary(value.toByteArray())
        }
    }

    private class BigDecimalSerializer : AbstractSerializer<BigDecimal?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): BigDecimal {
            val unscaledVal: BigInteger = BIG_INTEGER_SERIALIZER.read(decoder)
            val scale = decoder.readSmallInt()
            return BigDecimal(unscaledVal, scale)
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: BigDecimal) {
            BIG_INTEGER_SERIALIZER.write(encoder, value.unscaledValue())
            encoder.writeSmallInt(value.scale())
        }
    }

    private class ThrowableSerializer : AbstractSerializer<Throwable?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): Throwable? {
            return Message.receive(decoder.inputStream, javaClass.getClassLoader()) as Throwable?
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: Throwable?) {
            Message.send(value, encoder.outputStream)
        }
    }

    companion object {
        @JvmField
        val STRING_SERIALIZER: Serializer<String?> = StringSerializer()
        val BOOLEAN_SERIALIZER: Serializer<Boolean?> = BooleanSerializer()
        val BYTE_SERIALIZER: Serializer<Byte?> = ByteSerializer()
        val CHAR_SERIALIZER: Serializer<Char?> = CharSerializer()
        val SHORT_SERIALIZER: Serializer<Short?> = ShortSerializer()
        val INTEGER_SERIALIZER: Serializer<Int?> = IntegerSerializer()
        @JvmField
        val LONG_SERIALIZER: Serializer<Long?> = LongSerializer()
        val FLOAT_SERIALIZER: Serializer<Float?> = FloatSerializer()
        val DOUBLE_SERIALIZER: Serializer<Double?> = DoubleSerializer()
        @JvmField
        val FILE_SERIALIZER: Serializer<File?> = FileSerializer()
        @JvmField
        val PATH_SERIALIZER: Serializer<Path?> = PathSerializer()
        val BYTE_ARRAY_SERIALIZER: Serializer<ByteArray?> = ByteArraySerializer()
        @JvmField
        val NO_NULL_STRING_MAP_SERIALIZER: Serializer<MutableMap<String?, String?>?> = StringMapSerializer()
        val THROWABLE_SERIALIZER: Serializer<Throwable?> = ThrowableSerializer()
        val HASHCODE_SERIALIZER: Serializer<HashCode?> = HashCodeSerializer()
        val BIG_INTEGER_SERIALIZER: Serializer<BigInteger> = BigIntegerSerializer()
        val BIG_DECIMAL_SERIALIZER: Serializer<BigDecimal?> = BigDecimalSerializer()
    }
}
