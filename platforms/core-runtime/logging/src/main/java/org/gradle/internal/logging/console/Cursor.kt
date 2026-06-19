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
package org.gradle.internal.logging.console

import com.google.common.base.MoreObjects
import com.google.common.base.Objects

/**
 * A virtual console screen cursor. This class avoid complex screen position management.
 */
class Cursor {
    @JvmField
    var col: Int = 0 // count from left of screen, 0 = left most
    @JvmField
    var row: Int = 0 // count from bottom of screen, 0 = bottom most, 1 == 2nd from bottom

    fun copyFrom(position: Cursor) {
        if (position === this) {
            return
        }
        this.col = position.col
        this.row = position.row
    }

    fun bottomLeft() {
        col = 0
        row = 0
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (obj === this) {
            return true
        }
        if (obj.javaClass != javaClass) {
            return false
        }

        val rhs = obj as Cursor
        return col == rhs.col && row == rhs.row
    }

    override fun hashCode(): Int {
        return Objects.hashCode(col, row)
    }

    override fun toString(): String {
        return MoreObjects.toStringHelper(this.javaClass)
            .add("row", row)
            .add("col", col)
            .toString()
    }

    companion object {
        @JvmStatic
        fun at(row: Int, col: Int): Cursor {
            val result = Cursor()
            result.row = row
            result.col = col
            return result
        }

        @JvmStatic
        fun newBottomLeft(): Cursor {
            val result = Cursor()
            result.bottomLeft()
            return result
        }

        fun from(position: Cursor): Cursor {
            val result = Cursor()
            result.copyFrom(position)
            return result
        }
    }
}
