/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.language.nativeplatform.internal.incremental

import com.google.common.base.Objects
import org.gradle.internal.hash.HashCode

class IncludeFileEdge(val includePath: String, val includedBy: HashCode?, val resolvedTo: HashCode) {
    override fun equals(obj: Any?): Boolean {
        if (obj === this) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other = obj as IncludeFileEdge
        return includePath == other.includePath && Objects.equal(includedBy, other.includedBy) && resolvedTo == other.resolvedTo
    }

    override fun hashCode(): Int {
        return includePath.hashCode()
    }
}
