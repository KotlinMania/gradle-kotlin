/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.GradleException
import org.gradle.language.nativeplatform.internal.Expression
import org.gradle.language.nativeplatform.internal.Include
import org.gradle.language.nativeplatform.internal.IncludeDirectives
import org.gradle.language.nativeplatform.internal.IncludeType
import org.gradle.language.nativeplatform.internal.Macro
import org.gradle.language.nativeplatform.internal.MacroFunction
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.Reader
import java.util.Arrays
import kotlin.math.min

/**
 * Parses a subset of the C preprocessor language, to extract details of `#include`, `#import` and `#define` directives. Only handles a subset of the possible expressions that can be
 * used as the body of these directives.
 */
class RegexBackedCSourceParser : CSourceParser {
    override fun parseSource(sourceFile: File): IncludeDirectives? {
        // Note: source files can have non-UTF8 encoding. FileReader uses default Charset and also handles invalid characters.
        try {
            FileReader(sourceFile).use { fileReader ->
                return parseSource(fileReader)
            }
        } catch (e: Exception) {
            throw GradleException(String.format("Could not extract includes from source file %s.", sourceFile), e)
        }
    }

    @Throws(IOException::class)
    protected fun parseSource(sourceReader: Reader): IncludeDirectives? {
        val includes: MutableSet<Include?> = LinkedHashSet<Include?>()
        val macros: MutableList<Macro?> = ArrayList<Macro?>()
        val macroFunctions: MutableList<MacroFunction?> = ArrayList<MacroFunction?>()
        val reader = BufferedReader(sourceReader)
        val lineReader = PreprocessingReader(reader)
        val buffer = Buffer()
        while (true) {
            buffer.reset()
            if (!lineReader.readNextLine(buffer.value)) {
                break
            }
            buffer.consumeWhitespace()
            if (!buffer.consume('#')) {
                continue
            }
            buffer.consumeWhitespace()
            if (buffer.consume("define")) {
                parseDefineDirectiveBody(buffer, macros, macroFunctions)
            } else if (buffer.consume("include")) {
                parseIncludeOrImportDirectiveBody(buffer, false, includes)
            } else if (buffer.consume("import")) {
                parseIncludeOrImportDirectiveBody(buffer, true, includes)
            }
        }
        return DefaultIncludeDirectives.Companion.of(ImmutableList.copyOf<Include?>(includes), ImmutableList.copyOf<Macro?>(macros), ImmutableList.copyOf<MacroFunction?>(macroFunctions))
    }

    /**
     * Parses an #include/#import directive body. Consumes all input.
     */
    private fun parseIncludeOrImportDirectiveBody(buffer: Buffer, isImport: Boolean, includes: MutableCollection<Include?>) {
        if (!buffer.hasAny()) {
            // No include expression, ignore
            return
        }
        if (buffer.hasIdentifierChar()) {
            // An identifier with no separator, so this is not an #include or #import directive, it is some other directive
            return
        }
        var expression: Expression = parseDirectiveBodyExpression(buffer)
        if (expression.getType() == IncludeType.TOKEN_CONCATENATION || expression.getType() == IncludeType.ARGS_LIST || expression.getType() == IncludeType.EXPRESSIONS) {
            // Token concatenation is only allowed inside a #define body
            // Arbitrary tokens won't resolve to an include path
            // Treat both these cases as an unresolvable include directive
            expression = SimpleExpression(expression.getAsSourceText(), IncludeType.OTHER)
        }
        expression = expression.asMacroExpansion()
        if (expression.getType() != IncludeType.OTHER || !expression.getValue()!!.isEmpty()) {
            // Either a resolvable expression or a non-empty unresolvable expression, collect. Ignore includes with no value
            includes.add(IncludeWithSimpleExpression.Companion.create(expression, isImport))
        }
    }

    /**
     * Parses a #define directive body. Consumes all input.
     */
    private fun parseDefineDirectiveBody(buffer: Buffer, macros: MutableCollection<Macro?>, macroFunctions: MutableCollection<MacroFunction?>) {
        if (!buffer.consumeWhitespace()) {
            // No separating whitespace between the #define and the name
            return
        }
        val name = buffer.readIdentifier()
        if (name == null) {
            // No macro name
            return
        }
        if (buffer.consume('(')) {
            // A function-like macro
            parseMacroFunctionDirectiveBody(buffer, name, macroFunctions)
        } else {
            // An object-like macro
            parseMacroObjectDirectiveBody(buffer, name, macros)
        }
    }

    /**
     * Parse an "object-like" macro directive body. Consumes all input.
     */
    private fun parseMacroObjectDirectiveBody(buffer: Buffer, macroName: String?, macros: MutableCollection<Macro?>) {
        var expression: Expression = parseDirectiveBodyExpression(buffer)
        expression = expression.asMacroExpansion()
        if (!expression.getArguments().isEmpty()) {
            // Body is an expression with one or more arguments
            macros.add(MacroWithComplexExpression(macroName, expression.getType(), expression.getValue(), expression.getArguments()))
        } else if (expression.getType() != IncludeType.OTHER) {
            // Body is a simple expression, including a macro function call with no arguments
            macros.add(MacroWithSimpleExpression(macroName, expression.getType(), expression.getValue()))
        } else {
            // Discard the body when the expression is not resolvable
            macros.add(UnresolvableMacro(macroName))
        }
    }

    /**
     * Parse a "function-like" macro directive body. Consumes all input.
     */
    private fun parseMacroFunctionDirectiveBody(buffer: Buffer, macroName: String?, macroFunctions: MutableCollection<MacroFunction?>) {
        buffer.consumeWhitespace()
        val paramNames: MutableList<String> = ArrayList<String>()
        consumeParameterList(buffer, paramNames)
        if (!buffer.consume(')')) {
            // Badly form args list
            return
        }
        var expression: Expression = parseDirectiveBodyExpression(buffer)
        if (expression.getType() == IncludeType.QUOTED || expression.getType() == IncludeType.SYSTEM) {
            // Returns a fixed value expression
            macroFunctions.add(ReturnFixedValueMacroFunction(macroName, paramNames.size, expression.getType(), expression.getValue(), mutableListOf<Expression?>()))
            return
        }
        if (expression.getType() == IncludeType.IDENTIFIER) {
            for (i in paramNames.indices) {
                val name = paramNames.get(i)
                if (name == expression.getValue()) {
                    // Returns a parameter
                    macroFunctions.add(ReturnParameterMacroFunction(macroName, paramNames.size, i))
                    return
                }
            }
            // References some fixed value expression, return it after macro expanding
            macroFunctions.add(ReturnFixedValueMacroFunction(macroName, paramNames.size, IncludeType.MACRO, expression.getValue(), mutableListOf<Expression?>()))
            return
        }

        if (expression.getType() != IncludeType.OTHER) {
            // Look for parameter substitutions
            if (paramNames.isEmpty() || expression.getArguments().isEmpty()) {
                // When this function has no parameters, we don't need to substitute parameters, so return the expression after macro expanding it
                // Also handle calling a zero args function, as we also don't need to substitute parameters
                expression = expression.asMacroExpansion()
                macroFunctions.add(ReturnFixedValueMacroFunction(macroName, paramNames.size, expression.getType(), expression.getValue(), expression.getArguments()))
                return
            }
            val argsMap: MutableList<Int?> = ArrayList<Int?>(expression.getArguments().size)
            val usesArgs = mapArgs(paramNames, expression, argsMap)
            if (!usesArgs) {
                // Don't need to do parameter substitution, return the value of the expression after macro expanding it
                expression = expression.asMacroExpansion()
                macroFunctions.add(ReturnFixedValueMacroFunction(macroName, paramNames.size, expression.getType(), expression.getValue(), expression.getArguments()))
            } else {
                //Need to do parameter substitution, return the value of the expression after parameter substitutions and macro expanding the result
                val argsMapArray = IntArray(argsMap.size)
                for (i in argsMap.indices) {
                    argsMapArray[i] = argsMap.get(i)!!
                }
                expression = expression.asMacroExpansion()
                macroFunctions.add(ArgsMappingMacroFunction(macroName, paramNames.size, argsMapArray, expression.getType(), expression.getValue(), expression.getArguments()))
            }
            return
        }

        // Not resolvable. Discard the body when the expression is not resolvable
        macroFunctions.add(UnresolvableMacroFunction(macroName, paramNames.size))
    }

    private fun mapArgs(paramNames: MutableList<String>, expression: Expression, argsMap: MutableList<Int?>): Boolean {
        var usesParameters = false
        for (i in expression.getArguments().indices) {
            val argument = expression.getArguments().get(i)
            if (argument.getType() == IncludeType.IDENTIFIER) {
                var matches = false
                for (j in paramNames.indices) {
                    val paramName: String? = paramNames.get(j)
                    if (argument.getValue() == paramName) {
                        argsMap.add(j)
                        usesParameters = true
                        matches = true
                        break
                    }
                }
                if (matches) {
                    continue
                }
            }
            if (argument.getArguments().isEmpty()) {
                // Don't map
                argsMap.add(ArgsMappingMacroFunction.Companion.KEEP)
                continue
            }
            val nestedMap: MutableList<Int?> = ArrayList<Int?>(argument.getArguments().size)
            val argUsesParameters = mapArgs(paramNames, argument, nestedMap)
            if (argUsesParameters) {
                argsMap.add(ArgsMappingMacroFunction.Companion.REPLACE_ARGS)
                argsMap.addAll(nestedMap)
            } else {
                argsMap.add(ArgsMappingMacroFunction.Companion.KEEP)
            }
            usesParameters = usesParameters or argUsesParameters
        }
        return usesParameters
    }

    private fun consumeParameterList(buffer: Buffer, paramNames: MutableList<String>) {
        var paramName = buffer.readIdentifier()
        while (paramName != null) {
            paramNames.add(paramName)
            buffer.consumeWhitespace()
            if (!buffer.consume(',')) {
                // Missing ','
                return
            }
            buffer.consumeWhitespace()
            paramName = buffer.readIdentifier()
            if (paramName == null) {
                // Missing parameter name
                return
            }
        }
    }

    private class Buffer {
        val value: StringBuilder = StringBuilder()
        var pos: Int = 0

        override fun toString(): String {
            return "{buffer remaining: '" + value.substring(pos, pos + min(value.length - pos, 20)) + "'}"
        }

        fun reset() {
            value.setLength(0)
            pos = 0
        }

        /**
         * Returns text from the specified location to the end of the buffer.
         */
        fun substring(pos: Int): String? {
            return value.substring(pos)
        }

        /**
         * Is there another character available? Does not consume the character.
         */
        fun hasAny(): Boolean {
            return pos < value.length
        }

        /**
         * Is the given character available? Does not consume the character.
         */
        fun has(c: Char): Boolean {
            return pos < value.length && value.get(pos) == c
        }

        /**
         * Is one of the given character available? Does not consume the character.
         */
        fun hasAny(chars: String): Boolean {
            if (pos >= value.length) {
                return false
            }
            val ch = value.get(pos)
            for (i in 0..<chars.length) {
                if (chars.get(i) == ch) {
                    return true
                }
            }
            return false
        }

        /**
         * Is there an identifier character at the current location? Does not consume the character.
         */
        fun hasIdentifierChar(): Boolean {
            if (pos < value.length) {
                val ch = value.get(pos)
                return Character.isLetterOrDigit(ch) || ch == '_' || ch == '$'
            }
            return false
        }

        /**
         * Reads an identifier from the current location. Does not consume anything if there is not an identifier at the current location.
         *
         * @return the identifier or null if none present.
         */
        fun readIdentifier(): String? {
            val oldPos = pos
            pos = consumeIdentifier(value, pos)
            if (pos == oldPos) {
                return null
            }
            return value.substring(oldPos, pos)
        }

        /**
         * Reads any character except the given. Does not consume anything if there is no more input or one of the given chars are at the current location.
         *
         * @return the character or null if none present.
         */
        fun readAnyExcept(chars: String): String? {
            if (pos >= value.length) {
                return null
            }
            val ch = value.get(pos)
            for (i in 0..<chars.length) {
                if (chars.get(i) == ch) {
                    return null
                }
            }
            pos++
            return ch.toString()
        }

        /**
         * Skip any whitespace at the current location.
         *
         * @return true if skipped, false if not.
         */
        fun consumeWhitespace(): Boolean {
            val oldPos = pos
            pos = consumeWhitespace(value, pos)
            return pos != oldPos
        }

        /**
         * Skip the given string if present at the current location.
         *
         * @return true if skipped, false if not.
         */
        fun consume(token: String): Boolean {
            if (pos + token.length < value.length) {
                for (i in 0..<token.length) {
                    if (value.get(pos + i) != token.get(i)) {
                        return false
                    }
                }
                pos += token.length
                return true
            }
            return false
        }

        /**
         * Skip the given character if present at the current location.
         *
         * @return true if skipped, false if not.
         */
        fun consume(c: Char): Boolean {
            if (pos < value.length && value.get(pos) == c) {
                pos++
                return true
            }
            return false
        }

        /**
         * Skip characters up to the given character. Does not consume the character.
         */
        fun consumeUpTo(c: Char) {
            while (pos < value.length && value.get(pos) != c) {
                pos++
            }
        }
    }

    companion object {
        fun parseExpression(value: String?): Expression {
            val buffer = Buffer()
            buffer.value.append(value)
            return parseDirectiveBodyExpression(buffer).asMacroExpansion()
        }

        /**
         * Parses an expression that forms the body of a directive. Consumes all of the input.
         */
        private fun parseDirectiveBodyExpression(buffer: Buffer): Expression {
            val startPos = buffer.pos
            val expression: Expression? = parseExpression(buffer)
            buffer.consumeWhitespace()
            if (expression == null || buffer.hasAny()) {
                // Unrecognized expression or extra stuff after the expression, possibly another expression
                return SimpleExpression(buffer.substring(startPos)!!.trim { it <= ' ' }, IncludeType.OTHER)
            }
            return expression
        }

        /**
         * Parses an expression that forms the body of a directive or a macro function parameter:
         *
         * - A path or string
         * - An argument list, ie zero or more expressions delimited by '(' and ')'
         * - An identifier
         * - Token concatenation
         * - A macro function call
         * - Any single character that does not delimit function arguments
         *
         * Returns null when an expression cannot be parsed and consumes input up to the parse failure point.
         */
        private fun parseExpression(buffer: Buffer): Expression? {
            buffer.consumeWhitespace()
            if (!buffer.hasAny()) {
                // Empty or only whitespace
                return null
            }

            var expression: Expression? = readPathExpression(buffer)
            if (expression != null) {
                // A path, either "" or <> delimited
                return expression
            }

            // A sequence of tokens that look like a function call argument list. Should support an arbitrary token sequence
            var arguments: MutableList<Expression?>? = readArgumentList(buffer)
            if (arguments != null) {
                if (arguments.isEmpty()) {
                    return SimpleExpression.Companion.EMPTY_ARGS
                } else {
                    return ComplexExpression(IncludeType.ARGS_LIST, null, arguments)
                }
            }

            val identifier = buffer.readIdentifier()
            if (identifier == null) {
                // No identifier, allow anything except '(' or ',' or ')'
                val token = buffer.readAnyExcept("(),")
                if (token != null) {
                    return SimpleExpression(token, IncludeType.TOKEN)
                }
                return null
            }

            // Either a macro function, a macro or token concatenation
            buffer.consumeWhitespace()

            arguments = readArgumentList(buffer)
            if (arguments != null) {
                // A macro function call
                if (arguments.isEmpty()) {
                    return SimpleExpression(identifier, IncludeType.MACRO_FUNCTION)
                } else {
                    return ComplexExpression(IncludeType.MACRO_FUNCTION, identifier, arguments)
                }
            }

            expression = readTokenConcatenation(buffer, identifier)
            if (expression != null) {
                return expression
            }

            // Just an identifier, this is a token
            return SimpleExpression(identifier, IncludeType.IDENTIFIER)
        }

        private fun readPathExpression(buffer: Buffer): Expression? {
            if (buffer.consume('<')) {
                return readDelimitedExpression(buffer, '>', IncludeType.SYSTEM)
            } else if (buffer.consume('"')) {
                return readDelimitedExpression(buffer, '"', IncludeType.QUOTED)
            }
            return null
        }

        /**
         * Reads a token concatenation expression. Does not consume anything if not present.
         */
        private fun readTokenConcatenation(buffer: Buffer, leftToken: String?): Expression? {
            val pos = buffer.pos
            if (!buffer.consume("##")) {
                return null
            }
            buffer.consumeWhitespace()
            var right = buffer.readIdentifier()
            if (right == null) {
                // Need another identifier
                buffer.pos = pos
                return null
            }
            var concatExpression = ComplexExpression(
                IncludeType.TOKEN_CONCATENATION,
                null,
                Arrays.asList<Expression?>(SimpleExpression(leftToken, IncludeType.IDENTIFIER), SimpleExpression(right, IncludeType.IDENTIFIER))
            )

            buffer.consumeWhitespace()
            while (buffer.consume("##")) {
                buffer.consumeWhitespace()
                right = buffer.readIdentifier()
                if (right == null) {
                    // Need another identifier
                    buffer.pos = pos
                    return null
                }
                concatExpression = ComplexExpression(IncludeType.TOKEN_CONCATENATION, null, Arrays.asList<Expression?>(concatExpression, SimpleExpression(right, IncludeType.IDENTIFIER)))
                buffer.consumeWhitespace()
            }
            return concatExpression
        }

        /**
         * Reads an argument list. Does not consume anything if an argument list is not present
         */
        private fun readArgumentList(buffer: Buffer): MutableList<Expression?>? {
            val pos = buffer.pos
            if (!buffer.consume('(')) {
                return null
            }
            val argumentExpressions: MutableList<Expression?> = ArrayList<Expression?>()
            buffer.consumeWhitespace()
            consumeArgumentList(buffer, argumentExpressions)
            if (!buffer.consume(')')) {
                // Badly formed arguments
                buffer.pos = pos
                return null
            }
            return argumentExpressions
        }

        /**
         * Parses a macro function argument list. Stops when unable to recognize any further arguments.
         */
        private fun consumeArgumentList(buffer: Buffer, expressions: MutableList<Expression?>) {
            var expression: Expression? = readArgument(buffer)
            if (expression == null) {
                if (!buffer.has(',')) {
                    // No args
                    return
                }
                expression = SimpleExpression.Companion.EMPTY_EXPRESSIONS
            }
            expressions.add(expression)
            while (true) {
                buffer.consumeWhitespace()
                if (!buffer.consume(',')) {
                    return
                }
                expression = readArgument(buffer)
                if (expression == null) {
                    expression = SimpleExpression.Companion.EMPTY_EXPRESSIONS
                }
                expressions.add(expression)
            }
        }

        private fun readArgument(buffer: Buffer): Expression? {
            var expression: Expression? = parseExpression(buffer)
            if (expression == null) {
                return null
            }
            buffer.consumeWhitespace()
            if (buffer.hasAny(",)")) {
                return expression
            }
            val expressions: MutableList<Expression?> = ArrayList<Expression?>()
            expressions.add(expression)
            do {
                expression = parseExpression(buffer)
                if (expression == null) {
                    return null
                }
                expressions.add(expression)
                buffer.consumeWhitespace()
            } while (!buffer.hasAny(",)"))
            return ComplexExpression(IncludeType.EXPRESSIONS, null, expressions)
        }

        /**
         * Parses an expression that ends with the given delimiter. Returns null on failure and consumes input up to the parse failure point.
         */
        private fun readDelimitedExpression(buffer: Buffer, endDelim: Char, type: IncludeType?): Expression? {
            val startValue = buffer.pos
            buffer.consumeUpTo(endDelim)
            val endValue = buffer.pos
            if (!buffer.consume(endDelim)) {
                return null
            }
            return SimpleExpression(buffer.value.substring(startValue, endValue), type)
        }

        /**
         * Finds the end of an identifier.
         */
        private fun consumeIdentifier(value: CharSequence, startOffset: Int): Int {
            var pos = startOffset
            while (pos < value.length) {
                val ch = value.get(pos)
                if (!Character.isLetterOrDigit(ch) && ch != '_' && ch != '$') {
                    break
                }
                pos++
            }
            return pos
        }

        /**
         * Finds the end of a sequence of whitespace characters.
         */
        private fun consumeWhitespace(value: CharSequence, startOffset: Int): Int {
            var pos = startOffset
            while (pos < value.length) {
                val ch = value.get(pos)
                if (!Character.isWhitespace(ch) && ch.code != 0) {
                    break
                }
                pos++
            }
            return pos
        }
    }
}
