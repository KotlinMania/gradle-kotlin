/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.problems.internal

import com.google.common.base.Objects
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.util.internal.TextUtil
import java.io.Serializable

class DefaultProblemId(name: String, displayName: String, parent: ProblemGroup) : ProblemId(), Serializable {
    private val name: String
    private val displayName: String
    private val parent: ProblemGroup

    init {
        validateFields(name, displayName, parent)
        this.name = TextUtil.replaceLineSeparatorsOf(name, "")
        this.displayName = TextUtil.replaceLineSeparatorsOf(displayName, "")
        this.parent = parent
    }

    override fun getName(): String {
        return name
    }

    override fun getDisplayName(): String {
        return displayName
    }

    override fun getGroup(): ProblemGroup {
        return parent
    }

    override fun toString(): String {
        return groupPath(getGroup()) + getName()
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || o.javaClass.isAssignableFrom(ProblemId::class.java)) {
            return false
        }

        val that = o as ProblemId

        if (name != that.name) {
            return false
        }
        return parent == that.group
    }

    override fun hashCode(): Int {
        return Objects.hashCode(name, parent)
    }

    companion object {
        private fun validateFields(name: String, displayName: String, parent: ProblemGroup) {
            require(!TextUtil.isBlank(name)) { "Problem id name must not be blank" }
            require(!TextUtil.isBlank(displayName)) { "Problem id displayName must not be blank" }
            requireNotNull(parent) { "Problem id parent must not be null" }
        }

        fun groupPath(group: ProblemGroup?): String {
            if (group == null) {
                return ""
            }
            val parent = group.parent
            return groupPath(parent) + group.name + ":"
        }
    }
}
