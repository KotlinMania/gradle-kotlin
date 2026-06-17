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

import com.google.common.base.Joiner
import org.gradle.language.nativeplatform.internal.Expression
import org.gradle.language.nativeplatform.internal.IncludeType

abstract class AbstractExpression : Expression {
    override fun toString(): String {
        return getAsSourceText()!!
    }

    override fun getAsSourceText(): String? {
        return format(this)
    }

    override fun getArguments(): MutableList<Expression?>? {
        return mutableListOf<Expression?>()
    }

    override fun asMacroExpansion(): Expression {
        return asMacroExpansion(this)
    }

    companion object {
        fun asMacroExpansion(expression: Expression): Expression {
            if (expression.getType() == IncludeType.IDENTIFIER) {
                return SimpleExpression(expression.getValue(), IncludeType.MACRO)
            }
            if (expression.getType() == IncludeType.TOKEN_CONCATENATION) {
                return ComplexExpression(IncludeType.EXPAND_TOKEN_CONCATENATION, expression.getValue(), expression.getArguments())
            }
            if (expression.getType() == IncludeType.ARGS_LIST && !expression.getArguments().isEmpty()) {
                val mapped: MutableList<Expression?> = ArrayList<Expression?>(expression.getArguments().size)
                for (arg in expression.getArguments()) {
                    mapped.add(arg.asMacroExpansion())
                }
                return ComplexExpression(IncludeType.ARGS_LIST, null, mapped)
            }
            return expression
        }

        fun format(expression: Expression): String? {
            return format(expression.getType(), expression.getValue(), expression.getArguments())
        }

        fun format(type: IncludeType, value: String?, arguments: MutableList<Expression>): String? {
            when (type) {
                IncludeType.QUOTED -> return '"'.toString() + value + '"'
                IncludeType.SYSTEM -> return '<'.toString() + value + '>'
                IncludeType.MACRO, IncludeType.IDENTIFIER, IncludeType.TOKEN -> return value
                IncludeType.TOKEN_CONCATENATION, IncludeType.EXPAND_TOKEN_CONCATENATION -> return arguments.get(0).getAsSourceText() + "##" + arguments.get(1).getAsSourceText()
                IncludeType.MACRO_FUNCTION -> return value + "(" + Joiner.on(", ").join(arguments) + ")"
                IncludeType.EXPRESSIONS -> return Joiner.on(" ").join(arguments)
                IncludeType.ARGS_LIST -> return "(" + Joiner.on(", ").join(arguments) + ")"
                else -> return if (value != null) value else "??"
            }
        }
    }
}
