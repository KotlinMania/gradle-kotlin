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
package org.gradle.api.problems.internal

import com.google.common.base.Objects
import org.gradle.api.problems.FileLocation
import org.gradle.api.problems.OffsetInFileLocation

class DefaultOffsetInFileLocation private constructor(path: String, private val offset: Int, private val length: Int) : DefaultFileLocation(path), OffsetInFileLocation {
    override fun getOffset(): Int {
        return offset
    }

    override fun getLength(): Int {
        return length
    }

    override fun equals(o: Any): Boolean {
        if (o !is DefaultOffsetInFileLocation) {
            return false
        }
        if (!super.equals(o)) {
            return false
        }
        val that = o
        return offset == that.offset && length == that.length
    }

    override fun hashCode(): Int {
        return Objects.hashCode(super.hashCode(), offset, length)
    }

    companion object {
        fun from(path: String, offset: Int, length: Int): FileLocation {
            return DefaultOffsetInFileLocation(path, offset, length)
        }
    }
}
