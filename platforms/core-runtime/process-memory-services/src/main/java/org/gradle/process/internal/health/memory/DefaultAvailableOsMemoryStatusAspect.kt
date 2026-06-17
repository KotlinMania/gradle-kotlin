/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.process.internal.health.memory

import org.jspecify.annotations.NullMarked

@NullMarked
class DefaultAvailableOsMemoryStatusAspect(name: String, total: Long, free: Long) : OsMemoryStatusAspect.Available {
    private val name: String
    private val total: Long
    private val free: Long

    init {
        requireNotNull(name) { "name cannot be null" }
        require(total >= 0) { "total must be >= 0" }
        require(free >= 0) { "free must be >= 0" }
        this.name = name
        this.total = total
        this.free = free
    }

    override fun getName(): String {
        return name
    }

    override fun getTotal(): Long {
        return total
    }

    override fun getFree(): Long {
        return free
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultAvailableOsMemoryStatusAspect
        return total == that.total && free == that.free && name == that.name
    }

    override fun hashCode(): Int {
        return arrayOf<Any>(name, total, free).contentHashCode()
    }

    override fun toString(): String {
        return "AvailableMemory[" + name + ", total=" + total + ", free=" + free + ']'
    }
}
