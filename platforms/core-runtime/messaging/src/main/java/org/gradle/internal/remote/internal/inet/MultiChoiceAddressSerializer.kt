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
import org.gradle.internal.serialize.Serializer
import java.io.IOException
import java.net.InetAddress
import java.util.UUID

class MultiChoiceAddressSerializer : Serializer<MultiChoiceAddress> {
    @Throws(IOException::class)
    override fun read(decoder: Decoder): MultiChoiceAddress {
        val canonicalAddress = UUID(decoder.readLong(), decoder.readLong())
        val port = decoder.readInt()
        val addressCount = decoder.readSmallInt()
        val addresses: MutableList<InetAddress> = ArrayList<InetAddress>(addressCount)
        for (i in 0..<addressCount) {
            val address = InetAddress.getByAddress(decoder.readBinary())
            addresses.add(address!!)
        }
        return MultiChoiceAddress(canonicalAddress, port, addresses)
    }

    @Throws(IOException::class)
    override fun write(encoder: Encoder, address: MultiChoiceAddress) {
        val canonicalAddress = address.getCanonicalAddress()
        encoder.writeLong(canonicalAddress.getMostSignificantBits())
        encoder.writeLong(canonicalAddress.getLeastSignificantBits())
        encoder.writeInt(address.getPort())
        encoder.writeSmallInt(address.getCandidates().size)
        for (inetAddress in address.getCandidates()) {
            encoder.writeBinary(inetAddress.getAddress())
        }
    }
}
