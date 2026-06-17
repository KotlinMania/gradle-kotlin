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
package org.gradle.plugins.ide.idea.model

import com.google.common.base.Objects
import org.gradle.api.JavaVersion.Companion.toVersion

/**
 * Java language level used by IDEA projects.
 */
class IdeaLanguageLevel(version: Any?) {
    var level: String?

    init {
        if (version != null && version is String && version.startsWith("JDK_")) {
            level = version
            return
        }
        level = toVersion(version)!!.name.replaceFirst("VERSION".toRegex(), "JDK")
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (javaClass != o.javaClass) {
            return false
        }
        val that = o as IdeaLanguageLevel
        return Objects.equal(level, that.level)
    }

    override fun hashCode(): Int {
        return if (level != null) level.hashCode() else 0
    }
}
