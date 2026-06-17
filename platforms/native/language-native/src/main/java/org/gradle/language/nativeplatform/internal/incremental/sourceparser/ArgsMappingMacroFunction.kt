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

internal class ArgsMappingMacroFunction(macroName: String?, parameters: Int, val argsMap: IntArray, val type: IncludeType, val value: String?, val arguments: MutableList<Expression>) :
    AbstractMacroFunction(macroName, parameters) {
    override fun getBody(): String? {
        return AbstractExpression.Companion.format(type, value, arguments)
    }

    override fun evaluate(arguments: MutableList<Expression?>): Expression {
        val mapped: MutableList<Expression?> = ArrayList<Expression?>(this.arguments.size)
        var currentMapPos = 0
        for (argument in this.arguments) {
            currentMapPos = mapInto(argument, arguments, currentMapPos, mapped)
        }
        return ComplexExpression(type, value, mapped)
    }

    private fun mapInto(expression: Expression, arguments: MutableList<Expression?>, currentMapPos: Int, mapped: MutableList<Expression?>): Int {
        var currentMapPos = currentMapPos
        val replaceWith = argsMap[currentMapPos]
        currentMapPos++
        if (replaceWith == KEEP) {
            // Keep this expression
            mapped.add(expression)
        } else if (replaceWith == REPLACE_ARGS) {
            // Map the arguments of this expression
            val mappedArgs: MutableList<Expression?> = ArrayList<Expression?>(expression.getArguments().size)
            for (arg in expression.getArguments()) {
                currentMapPos = mapInto(arg, arguments, currentMapPos, mappedArgs)
            }
            mapped.add(ComplexExpression(expression.getType(), expression.getValue(), mappedArgs))
        } else if (type == IncludeType.MACRO_FUNCTION) {
            // Macro expand parameter
            mapped.add(arguments.get(replaceWith)!!.asMacroExpansion())
        } else {
            // Do not macro expand parameter
            mapped.add(arguments.get(replaceWith))
        }
        return currentMapPos
    }

    override fun equals(obj: Any?): Boolean {
        if (!super.equals(obj)) {
            return false
        }

        val other = obj as ArgsMappingMacroFunction
        return type == other.type && Objects.equal(value, other.value) && argsMap.contentEquals(other.argsMap) && arguments == other.arguments
    }

    override fun hashCode(): Int {
        return super.hashCode() xor type.hashCode() xor arguments.hashCode() xor argsMap.contentHashCode()
    }

    companion object {
        // Keep the argument from this expression
        val KEEP: Int = -1

        // Map the arguments of the argument from this expression
        val REPLACE_ARGS: Int = -2
    }
}
