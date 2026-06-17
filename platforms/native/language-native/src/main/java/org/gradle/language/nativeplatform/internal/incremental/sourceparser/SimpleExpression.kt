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
package org.gradle.language.nativeplatform.internal.incremental.sourceparser

import com.google.common.base.Objects
import org.gradle.language.nativeplatform.internal.Expression
import org.gradle.language.nativeplatform.internal.IncludeType

/**
 * An expression that has a type and value and no arguments.
 */
class SimpleExpression(private val value: String?, private val type: IncludeType) : AbstractExpression() {
    override fun getValue(): String? {
        return value
    }

    override fun getType(): IncludeType {
        return type
    }

    override fun equals(obj: Any?): Boolean {
        if (obj === this) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other = obj as SimpleExpression
        return Objects.equal(value, other.value) && type == other.type
    }

    override fun hashCode(): Int {
        return (if (value == null) 0 else value.hashCode()) xor type.hashCode()
    }

    companion object {
        val EMPTY_EXPRESSIONS: Expression = SimpleExpression(null, IncludeType.EXPRESSIONS)
        val EMPTY_ARGS: Expression = SimpleExpression(null, IncludeType.ARGS_LIST)
        val LEFT_PAREN: Expression = SimpleExpression("(", IncludeType.TOKEN)
        val COMMA: Expression = SimpleExpression(",", IncludeType.TOKEN)
        val RIGHT_PAREN: Expression = SimpleExpression(")", IncludeType.TOKEN)
    }
}
