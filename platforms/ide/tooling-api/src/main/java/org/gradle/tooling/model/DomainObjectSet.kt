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
package org.gradle.tooling.model

/**
 * A set of domain objects of type T.
 *
 * @param <T> The type of objects in this collection.
</T> */
interface DomainObjectSet<T> : MutableSet<T?> {
    /**
     * Returns the elements of this set in the set's iteration order.
     *
     * @return The elements of this set in the set's iteration order.
     */
    val all: MutableList<T?>?

    /**
     * Returns the element at the given index according to the set's iteration order.
     *
     * @param index The index of the element to get.
     * @return The element at the given index according to the set's iteration order.
     */
    @Throws(IndexOutOfBoundsException::class)
    fun getAt(index: Int): T?
}
