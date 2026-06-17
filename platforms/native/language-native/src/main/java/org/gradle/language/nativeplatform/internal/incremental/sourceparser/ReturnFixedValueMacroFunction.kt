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
 * A macro function that returns a fixed expression and ignores its parameters.
 */
class ReturnFixedValueMacroFunction(name: String?, parameters: Int, private val type: IncludeType, private val value: String?, private val arguments: MutableList<Expression?>) :
    AbstractMacroFunction(name, parameters), Expression {
    override fun getBody(): String? {
        return getAsSourceText()
    }

    override fun asMacroExpansion(): Expression {
        return AbstractExpression.Companion.asMacroExpansion(this)
    }

    override fun getAsSourceText(): String? {
        return AbstractExpression.Companion.format(this)
    }

    override fun getArguments(): MutableList<Expression?> {
        return arguments
    }

    override fun getType(): IncludeType {
        return type
    }

    override fun getValue(): String? {
        return value
    }

    override fun evaluate(arguments: MutableList<Expression?>?): Expression {
        return this
    }

    override fun equals(obj: Any?): Boolean {
        if (!super.equals(obj)) {
            return false
        }

        val other = obj as ReturnFixedValueMacroFunction
        return Objects.equal(value, other.value) && type == other.type && arguments == other.arguments
    }

    override fun hashCode(): Int {
        return super.hashCode() xor (if (value == null) 0 else value.hashCode()) xor type.hashCode() xor arguments.hashCode()
    }
}
