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
package org.gradle.internal.logging.text

import com.google.common.base.Objects
import java.util.EnumSet

class Style(val emphasises: MutableSet<Emphasis>, val color: Color) {
    enum class Emphasis {
        BOLD, REVERSE, ITALIC
    }

    enum class Color {
        DEFAULT, YELLOW, RED, GREY, GREEN, BLACK
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (obj === this) {
            return true
        }
        if (javaClass != obj.javaClass) {
            return false
        }

        val rhs = obj as Style
        return Objects.equal(this.emphasises, rhs.emphasises)
                && Objects.equal(this.color, rhs.color)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(this.emphasises, this.color)
    }

    companion object {
        val NORMAL: Style = of(Color.DEFAULT)

        @JvmOverloads
        fun of(emphasis: Emphasis, color: Color = Color.DEFAULT): Style {
            return Style(EnumSet.of(emphasis), color)
        }

        fun of(color: Color): Style {
            return Style(mutableSetOf(), color)
        }
    }
}
