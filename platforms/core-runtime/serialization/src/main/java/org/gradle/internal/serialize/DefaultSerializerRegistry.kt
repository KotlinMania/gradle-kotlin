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
import org.gradle.internal.Cast
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import javax.annotation.concurrent.ThreadSafe
import kotlin.Any
import kotlin.Array
import kotlin.Comparator
import kotlin.Exception
import kotlin.IllegalArgumentException
import kotlin.Int
import kotlin.String
import kotlin.Throwable
import kotlin.Throws
import kotlin.arrayOfNulls
import kotlin.require
import kotlin.requireNotNull

/**
 * Default implementation of [SerializerRegistry].
 *
 * This class must be thread-safe because multiple tasks may be registering serializable classes concurrently, while other tasks are calling [.build].
 */
@ThreadSafe
open class DefaultSerializerRegistry @JvmOverloads constructor(supportClassHierarchy: Boolean = true) : SerializerRegistry {
    private val serializerMap: MutableMap<Class<*>, SerializerFactory<*>> = ConcurrentSkipListMap<Class<*>, SerializerFactory<*>>(CLASS_COMPARATOR)

    // We are using a ConcurrentHashMap here because:
    //   - We don't want to use a Set with CLASS_COMPARATOR, since that would treat two classes with the same name originating from different classloaders as identical, allowing only one in the Set.
    //   - ConcurrentHashMap.newKeySet() isn't available on Java 6, yet, and that is where this code needs to run.
    //   - CopyOnWriteArraySet has slower insert performance, since it is not hash based.
    private val javaSerialization: MutableMap<Class<*>, Boolean> = ConcurrentHashMap<Class<*>, Boolean>()
    private val classMatcher: SerializerClassMatcherStrategy

    init {
        this.classMatcher = if (supportClassHierarchy) SerializerClassMatcherStrategy.Companion.HIERARCHY else SerializerClassMatcherStrategy.Companion.STRICT
    }

    override fun <T : Any> register(implementationType: Class<T>, serializer: Serializer<T>) {
        registerWithFactory<T>(implementationType, InstanceBasedSerializerFactory<T>(serializer))
    }

    protected fun <T : Any> registerWithFactory(implementationType: Class<T>, serializerProvider: SerializerFactory<T>) {
        serializerMap.put(implementationType, serializerProvider)
    }

    override fun <T : Any> useJavaSerialization(implementationType: Class<T>) {
        javaSerialization.put(implementationType, true)
    }

    override fun canSerialize(baseType: Class<*>): kotlin.Boolean {
        for (candidate in serializerMap.keys) {
            if (classMatcher.matches(baseType, candidate)) {
                return true
            }
        }
        for (candidate in javaSerialization.keys) {
            if (classMatcher.matches(baseType, candidate)) {
                return true
            }
        }
        return false
    }

    override fun <T : Any> build(baseType: Class<T>): Serializer<T> {
        val matches: MutableMap<Class<*>, SerializerFactory<*>> = LinkedHashMap<Class<*>, SerializerFactory<*>>()
        for (entry in serializerMap.entries) {
            if (baseType.isAssignableFrom(entry.key)) {
                matches.put(entry.key, entry.value)
            }
        }
        val matchingJavaSerialization: MutableSet<Class<*>> = LinkedHashSet<Class<*>>()
        for (candidate in javaSerialization.keys) {
            if (baseType.isAssignableFrom(candidate)) {
                matchingJavaSerialization.add(candidate)
            }
        }
        require(!(matches.isEmpty() && matchingJavaSerialization.isEmpty())) { String.format("Don't know how to serialize objects of type %s.", baseType.getName()) }
        if (matches.size == 1 && matchingJavaSerialization.isEmpty()) {
            return Cast.uncheckedNonnullCast<Serializer<T>>(matches.values.iterator().next().serializerInstance())
        }
        return TaggedTypeSerializer<T>(matches, matchingJavaSerialization)
    }

    private class TypeInfo(val tag: Int, val useForSubtypes: kotlin.Boolean, val serializer: Serializer<*>)

    private class TaggedTypeSerializer<T : Any>(serializerMap: MutableMap<Class<*>, SerializerFactory<*>>, javaSerialization: MutableSet<Class<*>>) : AbstractSerializer<T>() {
        private val serializersByType: MutableMap<Class<*>, TypeInfo> = HashMap<Class<*>, TypeInfo>()
        private val typeHierarchies: MutableMap<Class<*>, TypeInfo> = HashMap<Class<*>, TypeInfo>()
        private val serializersByTag: Array<TypeInfo?>

        init {
            serializersByTag = arrayOfNulls<TypeInfo>(2 + serializerMap.size)
            serializersByTag[JAVA_TYPE] = JAVA_SERIALIZATION
            var nextTag = 2
            for (entry in serializerMap.entries) {
                add(nextTag, entry.key, entry.value.serializerInstance())
                nextTag++
            }
            for (type in javaSerialization) {
                serializersByType.put(type, JAVA_SERIALIZATION)
                typeHierarchies.put(type, JAVA_SERIALIZATION)
            }
        }

        fun add(tag: Int, type: Class<*>, serializer: Serializer<*>) {
            val typeInfo = TypeInfo(tag, type == Throwable::class.java, serializer)
            serializersByType.put(type, typeInfo)
            serializersByTag[typeInfo.tag] = typeInfo
            if (typeInfo.useForSubtypes) {
                typeHierarchies.put(type, typeInfo)
            }
        }

        @Throws(Exception::class)
        override fun read(decoder: Decoder): T {
            val tag = decoder.readSmallInt()
            val typeInfo: TypeInfo = (if (tag >= serializersByTag.size) null else serializersByTag[tag])!!
            requireNotNull(typeInfo) { String.format("Unexpected type tag %d found.", tag) }
            return Cast.uncheckedNonnullCast<T>(typeInfo.serializer.read(decoder))
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: T) {
            val typeInfo = map(value.javaClass)
            encoder.writeSmallInt(typeInfo.tag)
            Cast.uncheckedNonnullCast<Serializer<T>>(typeInfo.serializer).write(encoder, value)
        }

        override fun equals(obj: Any?): kotlin.Boolean {
            if (!super.equals(obj)) {
                return false
            }

            val rhs = obj as TaggedTypeSerializer<*>
            return Objects.equal(serializersByType, rhs.serializersByType)
                    && Objects.equal(typeHierarchies, rhs.typeHierarchies)
                    && serializersByTag.contentEquals(rhs.serializersByTag)
        }

        override fun hashCode(): Int {
            return Objects.hashCode(super.hashCode(), serializersByType, typeHierarchies, serializersByTag.contentHashCode())
        }

        fun map(valueType: Class<*>): TypeInfo {
            val typeInfo = serializersByType.get(valueType)
            if (typeInfo != null) {
                return typeInfo
            }
            for (entry in typeHierarchies.entries) {
                if (entry.key.isAssignableFrom(valueType)) {
                    return entry.value
                }
            }
            throw IllegalArgumentException(String.format("Don't know how to serialize an object of type %s.", valueType.getName()))
        }

        companion object {
            private const val JAVA_TYPE = 1 // Reserve 0 for null (to be added later)
            private val JAVA_SERIALIZATION = TypeInfo(JAVA_TYPE, true, DefaultSerializer<Any?>())
        }
    }

    private interface SerializerClassMatcherStrategy {
        fun matches(baseType: Class<*>, candidate: Class<*>): kotlin.Boolean

        companion object {
            val STRICT: SerializerClassMatcherStrategy = StrictSerializerMatcher()
            val HIERARCHY: SerializerClassMatcherStrategy = HierarchySerializerMatcher()
        }
    }

    /**
     * Serializer wrapper, that allows specific instance to be created when they cannot be shared or reused.
     *
     * @param <S> The type supported by the serializer
    </S> */
    protected interface SerializerFactory<S> {
        fun serializerInstance(): Serializer<S>
    }

    private class InstanceBasedSerializerFactory<S : Any>(private val instance: Serializer<S>) : SerializerFactory<S> {
        override fun serializerInstance(): Serializer<S> {
            return instance
        }
    }

    private class HierarchySerializerMatcher : SerializerClassMatcherStrategy {
        override fun matches(baseType: Class<*>, candidate: Class<*>): kotlin.Boolean {
            return baseType.isAssignableFrom(candidate)
        }
    }

    private class StrictSerializerMatcher : SerializerClassMatcherStrategy {
        override fun matches(baseType: Class<*>, candidate: Class<*>): kotlin.Boolean {
            return baseType == candidate
        }
    }

    companion object {
        private val CLASS_COMPARATOR: Comparator<Class<*>> = object : Comparator<Class<*>> {
            override fun compare(o1: Class<*>, o2: Class<*>): Int {
                return o1.getName().compareTo(o2.getName())
            }
        }
    }
}
