/*
 * Copyright 2015 the original author or authors.
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

import com.google.common.collect.ImmutableList
import com.google.common.collect.Interner
import com.google.common.collect.Interners
import org.gradle.language.nativeplatform.internal.Expression
import org.gradle.language.nativeplatform.internal.Include
import org.gradle.language.nativeplatform.internal.IncludeType

/**
 * An #include directive whose body is an expression with no arguments.
 */
class IncludeWithSimpleExpression(value: String, isImport: Boolean, type: IncludeType) : AbstractInclude() {
    private val value: String
    private val isImport: Boolean
    private val type: IncludeType

    init {
        requireNotNull(value) { "value cannot be null" }
        requireNotNull(type) { "type cannot be null" }
        this.value = value
        this.isImport = isImport
        this.type = type
    }

    override fun getValue(): String {
        return value
    }

    override fun isImport(): Boolean {
        return isImport
    }

    override fun getType(): IncludeType {
        return type
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as IncludeWithSimpleExpression

        if (isImport != that.isImport) {
            return false
        }
        if (type != that.type) {
            return false
        }
        if (value != that.value) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + (if (isImport) 1 else 0)
        result = 31 * result + type.hashCode()
        return result
    }

    companion object {
        private val INTERNER: Interner<IncludeWithSimpleExpression> = Interners.newWeakInterner<IncludeWithSimpleExpression?>()

        fun create(value: String, isImport: Boolean, type: IncludeType): IncludeWithSimpleExpression {
            return INTERNER.intern(IncludeWithSimpleExpression(value, isImport, type))
        }

        fun create(expression: Expression, isImport: Boolean): Include {
            if (expression.getType() == IncludeType.MACRO_FUNCTION && !expression.getArguments().isEmpty()) {
                return IncludeWithMacroFunctionCallExpression(expression.getValue(), isImport, ImmutableList.copyOf<Expression?>(expression.getArguments()))
            }
            return Companion.create(expression.getValue()!!, isImport, expression.getType())
        }
    }
}
