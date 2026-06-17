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
package org.gradle.plugins.ide.internal.tooling.idea

import com.google.common.base.Objects
import org.gradle.tooling.model.idea.IdeaLanguageLevel
import java.io.Serializable

class DefaultIdeaLanguageLevel(private val level: String?) : IdeaLanguageLevel, Serializable {
    val isJDK_1_4: Boolean
        get() = "JDK_1_4" == level

    val isJDK_1_5: Boolean
        get() = "JDK_1_5" == level

    val isJDK_1_6: Boolean
        get() = "JDK_1_6" == level

    val isJDK_1_7: Boolean
        get() = "JDK_1_7" == level

    val isJDK_1_8: Boolean
        get() = "JDK_1_8" == level

    override fun getLevel(): String? {
        return level
    }

    override fun toString(): String {
        return "IdeaLanguageLevel{level='" + level + "'}"
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is DefaultIdeaLanguageLevel) {
            return false
        }

        val that = o
        return Objects.equal(level, that.level)
    }

    override fun hashCode(): Int {
        return if (level != null) level.hashCode() else 0
    }
}
