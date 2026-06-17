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
 * An expression with arguments.
 */
class ComplexExpression(private val type: IncludeType, private val value: String?, private val arguments: MutableList<Expression?>) : AbstractExpression() {
    override fun getValue(): String? {
        return value
    }

    override fun getType(): IncludeType {
        return type
    }

    override fun getArguments(): MutableList<Expression?> {
        return arguments
    }

    override fun equals(obj: Any?): Boolean {
        if (obj === this) {
            return true
        }
        if (obj == null || obj.javaClass != javaClass) {
            return false
        }
        val other = obj as ComplexExpression
        return Objects.equal(value, other.value) && arguments == other.arguments
    }

    override fun hashCode(): Int {
        return type.hashCode() xor (if (value == null) 0 else value.hashCode()) xor arguments.hashCode()
    }
}
