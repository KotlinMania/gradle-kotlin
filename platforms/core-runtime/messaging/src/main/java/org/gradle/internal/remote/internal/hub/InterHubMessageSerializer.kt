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
package org.gradle.internal.remote.internal.hub

import org.gradle.internal.remote.internal.hub.protocol.ChannelIdentifier
import org.gradle.internal.remote.internal.hub.protocol.ChannelMessage
import org.gradle.internal.remote.internal.hub.protocol.EndOfStream
import org.gradle.internal.remote.internal.hub.protocol.InterHubMessage
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.ObjectReader
import org.gradle.internal.serialize.ObjectWriter
import org.gradle.internal.serialize.StatefulSerializer
import java.io.IOException

class InterHubMessageSerializer(private val payloadSerializer: StatefulSerializer<Any?>) : StatefulSerializer<InterHubMessage?> {
    override fun newReader(decoder: Decoder): ObjectReader<InterHubMessage?> {
        return MessageReader(decoder, payloadSerializer.newReader(decoder))
    }

    override fun newWriter(encoder: Encoder): ObjectWriter<InterHubMessage?> {
        return MessageWriter(encoder, payloadSerializer.newWriter(encoder))
    }

    private class MessageReader(private val decoder: Decoder, private val payloadReader: ObjectReader<*>) : ObjectReader<InterHubMessage?> {
        private val channels: MutableMap<Int?, ChannelIdentifier?> = HashMap<Int?, ChannelIdentifier?>()

        @Throws(Exception::class)
        override fun read(): InterHubMessage {
            when (decoder.readByte()) {
                CHANNEL_MESSAGE -> {
                    val channelId = readChannelId()
                    val payload: Any? = payloadReader.read()
                    return ChannelMessage(channelId, payload)
                }

                END_STREAM_MESSAGE -> return EndOfStream()
                else -> throw IllegalArgumentException()
            }
        }

        @Throws(IOException::class)
        fun readChannelId(): ChannelIdentifier {
            val channelNum = decoder.readSmallInt()
            var channelId = channels.get(channelNum)
            if (channelId == null) {
                val channel = decoder.readString()
                channelId = ChannelIdentifier(channel)
                channels.put(channelNum, channelId)
            }
            return channelId
        }
    }

    private class MessageWriter(private val encoder: Encoder, private val payloadWriter: ObjectWriter<Any?>) : ObjectWriter<InterHubMessage?> {
        private val channels: MutableMap<ChannelIdentifier?, Int?> = HashMap<ChannelIdentifier?, Int?>()

        @Throws(Exception::class)
        override fun write(message: InterHubMessage?) {
            if (message is ChannelMessage) {
                val channelMessage = message
                encoder.writeByte(CHANNEL_MESSAGE)
                writeChannelId(channelMessage)
                payloadWriter.write(channelMessage.payload)
            } else if (message is EndOfStream) {
                encoder.writeByte(END_STREAM_MESSAGE)
            } else {
                throw IllegalArgumentException()
            }
        }

        @Throws(IOException::class)
        fun writeChannelId(channelMessage: ChannelMessage) {
            var channelNum = channels.get(channelMessage.getChannel())
            if (channelNum == null) {
                channelNum = channels.size
                channels.put(channelMessage.getChannel(), channelNum)
                encoder.writeSmallInt(channelNum)
                encoder.writeString(channelMessage.getChannel()!!.name)
            } else {
                encoder.writeSmallInt(channelNum)
            }
        }
    }

    companion object {
        private const val CHANNEL_MESSAGE: Byte = 1
        private const val END_STREAM_MESSAGE: Byte = 2
    }
}
