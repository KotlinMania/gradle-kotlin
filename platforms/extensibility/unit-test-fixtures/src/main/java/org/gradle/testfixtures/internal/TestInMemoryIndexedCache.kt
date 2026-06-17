/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.testfixtures.internal

import org.gradle.cache.IndexedCache
import org.gradle.cache.internal.ProducerGuard
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.serialize.InputStreamBackedDecoder
import org.gradle.internal.serialize.OutputStreamBackedEncoder
import org.gradle.internal.serialize.Serializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import java.util.function.Supplier

/**
 * A simple in-memory cache, used by the testing fixtures.
 */
class TestInMemoryIndexedCache<K, V>(private val valueSerializer: Serializer<V?>) : IndexedCache<K?, V?> {
    private val entries: MutableMap<Any, ByteArray> = ConcurrentHashMap<Any, ByteArray>()
    private val producerGuard: ProducerGuard<K?> = ProducerGuard.serial<K?>()

    override fun getIfPresent(key: K?): V? {
        val serialised = entries.get(key)
        if (serialised == null) {
            return null
        }
        try {
            val instr = ByteArrayInputStream(serialised)
            val decoder = InputStreamBackedDecoder(instr)
            return valueSerializer.read(decoder)
        } catch (e: Exception) {
            throw throwAsUncheckedException(e)
        }
    }

    override fun get(key: K?, producer: Function<in K?, out V>): V? {
        return producerGuard.guardByKey<V?>(key, Supplier {
            if (!entries.containsKey(key)) {
                put(key, producer.apply(key))
            }
            this@TestInMemoryIndexedCache.getIfPresent(key)
        })
    }

    override fun put(key: K?, value: V?) {
        val outstr = ByteArrayOutputStream()
        val encoder = OutputStreamBackedEncoder(outstr)
        try {
            valueSerializer.write(encoder, value)
            encoder.flush()
        } catch (e: Exception) {
            throw throwAsUncheckedException(e)
        }

        entries.put(key!!, outstr.toByteArray())
    }

    override fun remove(key: K?) {
        entries.remove(key)
    }

    fun keySet(): MutableSet<K?> {
        return entries.keys as MutableSet<K?>
    }
}
