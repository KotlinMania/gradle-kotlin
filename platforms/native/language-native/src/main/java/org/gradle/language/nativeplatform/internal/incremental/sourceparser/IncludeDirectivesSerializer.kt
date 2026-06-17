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

import com.google.common.collect.ImmutableList
import org.gradle.internal.serialize.AbstractCollectionSerializer
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.ListSerializer
import org.gradle.internal.serialize.Serializer
import org.gradle.language.nativeplatform.internal.Expression
import org.gradle.language.nativeplatform.internal.Include
import org.gradle.language.nativeplatform.internal.IncludeDirectives
import org.gradle.language.nativeplatform.internal.IncludeType
import org.gradle.language.nativeplatform.internal.Macro
import org.gradle.language.nativeplatform.internal.MacroFunction

class IncludeDirectivesSerializer private constructor() : Serializer<IncludeDirectives?> {
    private val enumSerializer = BaseSerializerFactory().getSerializerFor<IncludeType?>(IncludeType::class.java)
    private val expressionSerializer: Serializer<Expression?> = ExpressionSerializer(enumSerializer)
    private val includeListSerializer = ListSerializer<Include?>(IncludeSerializer(enumSerializer, expressionSerializer))
    private val macroListSerializer = CollectionSerializer<Macro?>(MacroSerializer(enumSerializer, expressionSerializer))
    private val macroFunctionListSerializer = CollectionSerializer<MacroFunction?>(MacroFunctionSerializer(enumSerializer, expressionSerializer))

    @Throws(Exception::class)
    override fun read(decoder: Decoder): IncludeDirectives? {
        return DefaultIncludeDirectives.Companion.of(
            ImmutableList.copyOf<Include?>(includeListSerializer.read(decoder)),
            ImmutableList.copyOf<Macro?>(macroListSerializer.read(decoder)),
            ImmutableList.copyOf<MacroFunction?>(macroFunctionListSerializer.read(decoder))
        )
    }

    @Throws(Exception::class)
    override fun write(encoder: Encoder, value: IncludeDirectives) {
        includeListSerializer.write(encoder, value.getAll())
        macroListSerializer.write(encoder, value.getAllMacros())
        macroFunctionListSerializer.write(encoder, value.getAllMacroFunctions())
    }

    private class ExpressionSerializer(private val enumSerializer: Serializer<IncludeType?>) : Serializer<Expression?> {
        private val argsSerializer: Serializer<MutableList<Expression?>?>

        init {
            this.argsSerializer = ListSerializer<Expression?>(this)
        }

        @Throws(Exception::class)
        override fun read(decoder: Decoder): Expression? {
            val tag = decoder.readByte()
            if (tag == SIMPLE) {
                val expressionValue = decoder.readNullableString()
                val expressionType = enumSerializer.read(decoder)
                return SimpleExpression(expressionValue, expressionType)
            } else if (tag == EMPTY_TOKENS) {
                return SimpleExpression.Companion.EMPTY_EXPRESSIONS
            } else if (tag == EMPTY_ARGS) {
                return SimpleExpression.Companion.EMPTY_ARGS
            } else if (tag == COMMA) {
                return SimpleExpression.Companion.COMMA
            } else if (tag == LEFT_PAREN) {
                return SimpleExpression.Companion.LEFT_PAREN
            } else if (tag == RIGHT_PAREN) {
                return SimpleExpression.Companion.RIGHT_PAREN
            } else if (tag == COMPLEX_ONE_ARG) {
                val expressionValue = decoder.readNullableString()
                val type = enumSerializer.read(decoder)
                val args: MutableList<Expression?> = ImmutableList.of<Expression?>(read(decoder))
                return ComplexExpression(type, expressionValue, args)
            } else if (tag == COMPLEX_MULTIPLE_ARGS) {
                val expressionValue = decoder.readNullableString()
                val type = enumSerializer.read(decoder)
                val args: MutableList<Expression?> = ImmutableList.copyOf<Expression?>(argsSerializer.read(decoder))
                return ComplexExpression(type, expressionValue, args)
            } else {
                throw IllegalArgumentException()
            }
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: Expression?) {
            if (value is SimpleExpression) {
                if (value == SimpleExpression.Companion.EMPTY_EXPRESSIONS) {
                    encoder.writeByte(EMPTY_TOKENS)
                } else if (value == SimpleExpression.Companion.EMPTY_ARGS) {
                    encoder.writeByte(EMPTY_ARGS)
                } else if (value == SimpleExpression.Companion.COMMA) {
                    encoder.writeByte(COMMA)
                } else if (value == SimpleExpression.Companion.LEFT_PAREN) {
                    encoder.writeByte(LEFT_PAREN)
                } else if (value == SimpleExpression.Companion.RIGHT_PAREN) {
                    encoder.writeByte(RIGHT_PAREN)
                } else {
                    encoder.writeByte(SIMPLE)
                    encoder.writeNullableString(value.getValue())
                    enumSerializer.write(encoder, value.getType())
                }
            } else if (value is ComplexExpression) {
                if (value.getArguments().size == 1) {
                    encoder.writeByte(COMPLEX_ONE_ARG)
                    encoder.writeNullableString(value.getValue())
                    enumSerializer.write(encoder, value.getType())
                    write(encoder, value.getArguments().get(0))
                } else {
                    encoder.writeByte(COMPLEX_MULTIPLE_ARGS)
                    encoder.writeNullableString(value.getValue())
                    enumSerializer.write(encoder, value.getType())
                    argsSerializer.write(encoder, value.getArguments())
                }
            } else {
                throw IllegalArgumentException()
            }
        }

        companion object {
            private val SIMPLE = 1.toByte()
            private val COMPLEX_ONE_ARG = 2.toByte()
            private val COMPLEX_MULTIPLE_ARGS = 3.toByte()
            private val EMPTY_TOKENS = 4.toByte()
            private val EMPTY_ARGS = 5.toByte()
            private val COMMA = 6.toByte()
            private val LEFT_PAREN = 7.toByte()
            private val RIGHT_PAREN = 8.toByte()
        }
    }

    private class IncludeSerializer(private val enumSerializer: Serializer<IncludeType?>, private val expressionSerializer: Serializer<Expression?>) : Serializer<Include?> {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): Include {
            val value = decoder.readString()
            val isImport = decoder.readBoolean()
            val type = enumSerializer.read(decoder)
            val argsCount = decoder.readSmallInt()
            if (argsCount == 0) {
                return IncludeWithSimpleExpression.Companion.create(value, isImport, type)
            }
            val args: MutableList<Expression?> = ArrayList<Expression?>(argsCount)
            for (i in 0..<argsCount) {
                args.add(expressionSerializer.read(decoder))
            }
            return IncludeWithMacroFunctionCallExpression(value, isImport, ImmutableList.copyOf<Expression?>(args))
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: Include) {
            encoder.writeString(value.getValue())
            encoder.writeBoolean(value.isImport())
            enumSerializer.write(encoder, value.getType())
            if (value is IncludeWithSimpleExpression) {
                encoder.writeSmallInt(0)
            } else if (value is IncludeWithMacroFunctionCallExpression) {
                encoder.writeSmallInt(value.getArguments().size)
                for (expression in value.getArguments()) {
                    expressionSerializer.write(encoder, expression)
                }
            } else {
                throw IllegalArgumentException()
            }
        }
    }

    private class MacroSerializer(private val enumSerializer: Serializer<IncludeType?>, expressionSerializer: Serializer<Expression?>) : Serializer<Macro?> {
        private val expressionSerializer: Serializer<MutableList<Expression?>?>

        init {
            this.expressionSerializer = ListSerializer<Expression?>(expressionSerializer)
        }

        @Throws(Exception::class)
        override fun read(decoder: Decoder): Macro {
            val tag = decoder.readByte()
            if (tag == SIMPLE) {
                val name = decoder.readString()
                val type = enumSerializer.read(decoder)
                val value = decoder.readNullableString()
                return MacroWithSimpleExpression(name, type, value)
            } else if (tag == COMPLEX) {
                val name = decoder.readString()
                val type = enumSerializer.read(decoder)
                val value = decoder.readNullableString()
                val args: MutableList<Expression?> = ImmutableList.copyOf<Expression?>(expressionSerializer.read(decoder))
                return MacroWithComplexExpression(name, type, value, args)
            } else if (tag == UNRESOLVED) {
                val name = decoder.readString()
                return UnresolvableMacro(name)
            } else {
                throw IllegalArgumentException()
            }
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: Macro?) {
            if (value is MacroWithSimpleExpression) {
                encoder.writeByte(SIMPLE)
                encoder.writeString(value.getName())
                enumSerializer.write(encoder, value.getType())
                encoder.writeNullableString(value.getValue())
            } else if (value is MacroWithComplexExpression) {
                encoder.writeByte(COMPLEX)
                encoder.writeString(value.getName())
                enumSerializer.write(encoder, value.getType())
                encoder.writeNullableString(value.getValue())
                expressionSerializer.write(encoder, value.getArguments())
            } else if (value is UnresolvableMacro) {
                encoder.writeByte(UNRESOLVED)
                encoder.writeString(value.getName())
            } else {
                throw IllegalArgumentException()
            }
        }

        companion object {
            private val SIMPLE = 1.toByte()
            private val COMPLEX = 2.toByte()
            private val UNRESOLVED = 3.toByte()
        }
    }

    private class MacroFunctionSerializer(private val enumSerializer: Serializer<IncludeType?>, expressionSerializer: Serializer<Expression?>) : Serializer<MacroFunction?> {
        private val expressionSerializer: Serializer<MutableList<Expression?>?>

        init {
            this.expressionSerializer = ListSerializer<Expression?>(expressionSerializer)
        }

        @Throws(Exception::class)
        override fun read(decoder: Decoder): MacroFunction {
            val tag = decoder.readByte()
            if (tag == FIXED_VALUE) {
                val name = decoder.readString()
                val parameters = decoder.readSmallInt()
                val type = enumSerializer.read(decoder)
                val value = decoder.readNullableString()
                val args: MutableList<Expression?> = ImmutableList.copyOf<Expression?>(expressionSerializer.read(decoder))
                return ReturnFixedValueMacroFunction(name, parameters, type, value, args)
            } else if (tag == RETURN_PARAM) {
                val name = decoder.readString()
                val parameters = decoder.readSmallInt()
                val parameterToReturn = decoder.readSmallInt()
                return ReturnParameterMacroFunction(name, parameters, parameterToReturn)
            } else if (tag == MAPPING) {
                val name = decoder.readString()
                val parameters = decoder.readSmallInt()
                val type = enumSerializer.read(decoder)
                val value = decoder.readNullableString()
                val arguments: MutableList<Expression?> = ImmutableList.copyOf<Expression?>(expressionSerializer.read(decoder))
                val mapCount = decoder.readSmallInt()
                val argsMap = IntArray(mapCount)
                for (i in 0..<mapCount) {
                    argsMap[i] = decoder.readSmallInt()
                }
                return ArgsMappingMacroFunction(name, parameters, argsMap, type, value, arguments)
            } else if (tag == UNRESOLVED) {
                val name = decoder.readString()
                val parameters = decoder.readSmallInt()
                return UnresolvableMacroFunction(name, parameters)
            } else {
                throw IllegalArgumentException()
            }
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: MacroFunction?) {
            if (value is ReturnFixedValueMacroFunction) {
                val fixedValueFunction = value
                encoder.writeByte(FIXED_VALUE)
                encoder.writeString(value.getName())
                encoder.writeSmallInt(value.getParameterCount())
                enumSerializer.write(encoder, fixedValueFunction.getType())
                encoder.writeNullableString(fixedValueFunction.getValue())
                expressionSerializer.write(encoder, fixedValueFunction.getArguments())
            } else if (value is ReturnParameterMacroFunction) {
                val returnParameterFunction = value
                encoder.writeByte(RETURN_PARAM)
                encoder.writeString(value.getName())
                encoder.writeSmallInt(value.getParameterCount())
                encoder.writeSmallInt(returnParameterFunction.getParameterToReturn())
            } else if (value is ArgsMappingMacroFunction) {
                val argsMappingFunction = value
                encoder.writeByte(MAPPING)
                encoder.writeString(value.getName())
                encoder.writeSmallInt(value.getParameterCount())
                enumSerializer.write(encoder, argsMappingFunction.getType())
                encoder.writeNullableString(argsMappingFunction.getValue())
                expressionSerializer.write(encoder, argsMappingFunction.getArguments())
                val argsMap = argsMappingFunction.getArgsMap()
                encoder.writeSmallInt(argsMap.size)
                for (anArgsMap in argsMap) {
                    encoder.writeSmallInt(anArgsMap)
                }
            } else if (value is UnresolvableMacroFunction) {
                encoder.writeByte(UNRESOLVED)
                encoder.writeString(value.getName())
                encoder.writeSmallInt(value.getParameterCount())
            } else {
                throw IllegalArgumentException()
            }
        }

        companion object {
            private val FIXED_VALUE = 1.toByte()
            private val RETURN_PARAM = 2.toByte()
            private val MAPPING = 3.toByte()
            private val UNRESOLVED = 4.toByte()
        }
    }

    private class CollectionSerializer<T>(entrySerializer: Serializer<T?>) : AbstractCollectionSerializer<T?, MutableCollection<T?>?>(entrySerializer) {
        override fun createCollection(size: Int): MutableCollection<T?> {
            return ArrayList<T?>(size)
        }
    }

    companion object {
        val INSTANCE: IncludeDirectivesSerializer = IncludeDirectivesSerializer()
    }
}
