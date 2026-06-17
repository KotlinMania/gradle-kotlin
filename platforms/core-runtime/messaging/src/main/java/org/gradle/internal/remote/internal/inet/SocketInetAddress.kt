/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.remote.internal.inet

import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import java.io.IOException
import java.net.InetAddress

class SocketInetAddress(val address: InetAddress, private val port: Int) : InetEndpoint {
    override fun getDisplayName(): String {
        return address.toString() + ":" + port
    }

    override fun toString(): String {
        return getDisplayName()
    }

    override fun equals(o: Any): Boolean {
        if (o === this) {
            return true
        }
        if (o == null || o.javaClass != javaClass) {
            return false
        }
        val other = o as SocketInetAddress
        return other.address == address && other.port == port
    }

    override fun hashCode(): Int {
        return address.hashCode() xor port
    }

    override fun getCandidates(): MutableList<InetAddress> {
        return mutableListOf<InetAddress>(address)
    }

    override fun getPort(): Int {
        return port
    }

    private class Serializer : org.gradle.internal.serialize.Serializer<SocketInetAddress> {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): SocketInetAddress {
            return SocketInetAddress(readAddress(decoder), decoder.readInt())
        }

        @Throws(IOException::class)
        fun readAddress(decoder: Decoder): InetAddress {
            return InetAddress.getByAddress(decoder.readBinary())
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, address: SocketInetAddress) {
            writeAddress(encoder, address)
            encoder.writeInt(address.port)
        }

        @Throws(IOException::class)
        fun writeAddress(encoder: Encoder, address: SocketInetAddress) {
            encoder.writeBinary(address.address.getAddress())
        }
    }

    companion object {
        @JvmField
        val SERIALIZER: org.gradle.internal.serialize.Serializer<SocketInetAddress> = Serializer()
    }
}
