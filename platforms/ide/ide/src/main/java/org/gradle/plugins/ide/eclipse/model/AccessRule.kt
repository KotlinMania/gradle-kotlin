/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model

import com.google.common.base.Objects
import com.google.common.base.Preconditions

/**
 * Access rule associated to a classpath entry.
 */
class AccessRule(kind: String?, pattern: String?) {
    var kind: String
    var pattern: String?

    init {
        this.kind = Preconditions.checkNotNull<String>(kind)
        this.pattern = Preconditions.checkNotNull<String?>(pattern)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as AccessRule
        return Objects.equal(kind, that.kind) && Objects.equal(pattern, that.pattern)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(kind, pattern)
    }

    override fun toString(): String {
        return "AccessRule{kind='" + kind + "', pattern='" + pattern + "'}"
    }
}

