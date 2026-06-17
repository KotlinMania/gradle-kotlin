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
package org.gradle.internal.serialize

import com.google.common.base.Objects
import com.google.common.collect.Sets

class SetSerializer<T> @JvmOverloads constructor(entrySerializer: Serializer<T?>, private val linkedHashSet: Boolean = true) : AbstractCollectionSerializer<T?, MutableSet<T?>?>(entrySerializer),
    Serializer<MutableSet<T?>?> {
    override fun createCollection(size: Int): MutableSet<T?>? {
        if (size == 0) {
            return mutableSetOf<T?>()
        }
        return if (linkedHashSet) Sets.newLinkedHashSetWithExpectedSize<T?>(size) else Sets.newHashSetWithExpectedSize<T?>(size)
    }

    override fun equals(obj: Any?): Boolean {
        if (!super.equals(obj)) {
            return false
        }

        val rhs = obj as SetSerializer<*>
        return linkedHashSet == rhs.linkedHashSet
    }

    override fun hashCode(): Int {
        return Objects.hashCode(super.hashCode(), linkedHashSet)
    }
}
