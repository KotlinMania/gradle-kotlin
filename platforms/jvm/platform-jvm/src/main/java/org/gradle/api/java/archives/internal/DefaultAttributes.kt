/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.java.archives.internal

import org.gradle.api.java.archives.ManifestException
import java.util.jar.Attributes

class DefaultAttributes : org.gradle.api.java.archives.Attributes {
    protected var attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()

    override fun size(): Int {
        return attributes.size
    }

    override fun isEmpty(): Boolean {
        return attributes.isEmpty()
    }

    override fun containsKey(key: Any?): Boolean {
        return attributes.containsKey(key)
    }

    override fun containsValue(value: Any?): Boolean {
        return attributes.containsValue(value)
    }

    override fun get(key: Any?): Any? {
        return attributes.get(key)
    }

    override fun put(key: String, value: Any): Any? {
        if (key == null) {
            throw ManifestException("The key of a manifest attribute must not be null.")
        }
        if (value == null) {
            throw ManifestException(String.format("The value of a manifest attribute must not be null (Key=%s).", key))
        }
        try {
            Attributes.Name(key)
        } catch (e: IllegalArgumentException) {
            throw ManifestException(String.format("The Key=%s violates the Manifest spec!", key))
        }
        return attributes.put(key, value)
    }

    override fun remove(key: Any?): Any? {
        return attributes.remove(key)
    }

    override fun putAll(m: MutableMap<out String?, out Any?>) {
        for (entry in m.entries) {
            put(entry.key!!, entry.value!!)
        }
    }

    override fun clear() {
        attributes.clear()
    }

    override fun keySet(): MutableSet<String?> {
        return attributes.keys
    }

    override fun values(): MutableCollection<Any?> {
        return attributes.values
    }

    override fun entrySet(): MutableSet<MutableMap.MutableEntry<String?, Any?>> {
        return attributes.entries
    }

    override fun equals(o: Any?): Boolean {
        return attributes == o
    }

    override fun hashCode(): Int {
        return attributes.hashCode()
    }
}
