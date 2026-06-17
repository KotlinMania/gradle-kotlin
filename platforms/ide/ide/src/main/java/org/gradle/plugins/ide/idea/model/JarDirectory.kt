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


/**
 * Represents a jar directory element of an idea module library.
 */
class JarDirectory(
    /**
     * The value for the recursive attribute of the jar directory element.
     */
    var path: Path, var isRecursive: Boolean
) {
    /**
     * The path of the jar directory
     */

    override fun toString(): String {
        return "JarDirectory{" + "path=" + path + ", recursive=" + this.isRecursive + "}"
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (javaClass != o.javaClass) {
            return false
        }
        val that = o as JarDirectory
        return this.isRecursive == that.isRecursive
                && Objects.equal(path, that.path)
    }

    override fun hashCode(): Int {
        var result: Int
        result = path.hashCode()
        result = 31 * result + (if (this.isRecursive) 1 else 0)
        return result
    }
}
