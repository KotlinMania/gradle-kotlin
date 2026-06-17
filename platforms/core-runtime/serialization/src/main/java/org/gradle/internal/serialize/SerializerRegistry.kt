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

interface SerializerRegistry {
    /**
     * Use the given serializer for objects of the given type.
     *
     *
     * The provided serializer must be thread-safe and reusable.
     */
    fun <T> register(implementationType: Class<T?>?, serializer: Serializer<T?>?)

    /**
     * Use Java serialization for the specified type and all subtypes. Should be avoided, but useful when migrating to using serializers or when dealing with
     * arbitrary user types.
     */
    fun <T> useJavaSerialization(implementationType: Class<T?>?)

    /**
     * Returns true when this registry can serialize objects of the given type.
     */
    fun canSerialize(baseType: Class<*>?): Boolean

    /**
     * Creates a serializer that uses the current registrations to serialize objects of type T.
     */
    fun <T> build(baseType: Class<T?>?): Serializer<T?>?
}
