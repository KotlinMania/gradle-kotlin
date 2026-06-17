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
class DefaultUnavailableOsMemoryStatusAspect(name: String) : OsMemoryStatusAspect.Unavailable {
    private val name: String

    init {
        requireNotNull(name) { "name cannot be null" }
        this.name = name
    }

    override fun getName(): String {
        return name
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultUnavailableOsMemoryStatusAspect
        return name == that.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        return "UnavailableMemory[" + name + ']'
    }
}
