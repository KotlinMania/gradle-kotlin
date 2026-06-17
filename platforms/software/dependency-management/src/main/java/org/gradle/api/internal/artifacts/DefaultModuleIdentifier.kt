/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.artifacts

import org.gradle.api.artifacts.ModuleIdentifier
import java.util.Objects

class DefaultModuleIdentifier private constructor(private val group: String?, private val name: String) : ModuleIdentifier {
    private val hashCode: Int

    init {
        this.hashCode = computeHashCode(group, name)
    }

    override fun getGroup(): String {
        return group!!
    }

    override fun getName(): String {
        return name
    }

    override fun toString(): String {
        return group + ":" + name
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultModuleIdentifier
        return hashCode == that.hashCode &&
                group == that.group &&
                name == that.name
    }

    override fun hashCode(): Int {
        return hashCode
    }

    companion object {
        fun newId(other: ModuleIdentifier): ModuleIdentifier {
            if (other is DefaultModuleIdentifier) {
                return other
            }
            return newId(other.getGroup(), other.getName())
        }

        fun newId(group: String?, name: String): ModuleIdentifier {
            return DefaultModuleIdentifier(group, name)
        }

        private fun computeHashCode(group: String?, name: String): Int {
            var result = Objects.hashCode(group)
            result = 31 * result + name.hashCode()
            return result
        }
    }
}
