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
package org.gradle.api.internal.artifacts.ivyservice

import org.apache.commons.lang3.builder.HashCodeBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.Serializable
import java.util.Base64

/**
 * Represents an identifier containing a tuple of namespace and name for use when
 * consuming/producing namespaced elements in descriptors.
 */
class NamespaceId(
    /**
     * Sets the namespace for this identifier.
     *
     * @param namespace the namespace
     */
    @JvmField var namespace: String,
    /**
     * Sets the name for this identifier.
     *
     * @param name the name
     */
    @JvmField var name: String
) : Serializable {
    /**
     * Gets the namespace for this identifier.
     *
     * @return the namespace for this identifier
     */
    /**
     * Gets the name for this identifier.
     *
     * @return the name for this identifier
     */

    fun encode(): String {
        try {
            ByteArrayOutputStream().use { baos ->
                DataOutputStream(baos).use { dos ->
                    dos.writeUTF(namespace)
                    dos.writeUTF(name)
                    return Base64.getEncoder().encodeToString(baos.toByteArray())
                }
            }
        } catch (e: IOException) {
            throw RuntimeException("Failed encoding namespace ID '" + name + "'")
        }
    }

    override fun toString(): String {
        return name
    }

    override fun equals(o: Any): Boolean {
        if (o === this) {
            return true
        }

        if (o == null || o.javaClass != javaClass) {
            return false
        }

        val other = o as NamespaceId
        return other.name == name && other.namespace == namespace
    }

    override fun hashCode(): Int {
        return HashCodeBuilder(17, 31)
            .append(namespace)
            .append(name)
            .toHashCode()
    }

    companion object {
        @JvmStatic
        fun decode(encoding: String): NamespaceId {
            val data = Base64.getDecoder().decode(encoding)
            try {
                ByteArrayInputStream(data).use { bais ->
                    DataInputStream(bais).use { dis ->
                        val namespace = dis.readUTF()
                        val name = dis.readUTF()
                        return NamespaceId(namespace, name)
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException("Failed decoding namespace ID")
            }
        }
    }
}
