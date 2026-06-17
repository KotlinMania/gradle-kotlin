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
import org.gradle.api.Incubating
import org.gradle.api.problems.ProblemGroup
import org.gradle.util.internal.TextUtil
import java.io.Serializable

@Incubating
class DefaultProblemGroup(name: String, displayName: String, parent: ProblemGroup?) : ProblemGroup(), Serializable {
    private val name: String
    private val displayName: String
    private val parent: ProblemGroup?

    constructor(groupId: String, displayName: String) : this(groupId, displayName, null)

    init {
        validateFields(name, displayName)
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

    override fun getParent(): ProblemGroup? {
        return parent
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || o.javaClass.isAssignableFrom(ProblemGroup::class.java)) {
            return false
        }
        val that = o as ProblemGroup
        return Objects.equal(parent, that.parent) && Objects.equal(name, that.name)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(name, parent)
    }

    companion object {
        private fun validateFields(name: String, displayName: String) {
            require(!TextUtil.isBlank(name)) { "Problem group name must not be blank" }
            require(!TextUtil.isBlank(displayName)) { "Problem group displayName must not be blank" }
        }
    }
}
