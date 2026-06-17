/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.tooling.internal.adapter

import org.gradle.tooling.model.DomainObjectSet
import java.util.TreeMap
import java.util.TreeSet

internal class CollectionMapper {
    fun createEmptyCollection(collectionType: Class<*>): MutableCollection<Any> {
        if (collectionType == DomainObjectSet::class.java) {
            return ArrayList<Any>()
        }
        if (collectionType.isAssignableFrom(ArrayList::class.java)) {
            return ArrayList<Any>()
        }
        if (collectionType.isAssignableFrom(LinkedHashSet::class.java)) {
            return LinkedHashSet<Any>()
        }
        if (collectionType.isAssignableFrom(TreeSet::class.java)) {
            return TreeSet<Any>()
        }
        throw UnsupportedOperationException(String.format("Cannot convert a Collection to type %s.", collectionType.getName()))
    }

    fun createEmptyMap(mapType: Class<*>): MutableMap<Any, Any> {
        if (mapType.isAssignableFrom(LinkedHashMap::class.java)) {
            return LinkedHashMap<Any, Any>()
        }
        if (mapType.isAssignableFrom(TreeMap::class.java)) {
            return TreeMap<Any, Any>()
        }
        throw UnsupportedOperationException(String.format("Cannot convert a Map to type %s.", mapType.getName()))
    }
}
