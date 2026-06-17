/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.internal.instrumentation.processor.codegen

import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import org.gradle.internal.instrumentation.util.NameUtil
import org.objectweb.asm.Type
import java.util.Collections
import java.util.Optional

object TypeUtils {
    private val PRIMITIVE_TYPES_DEFAULT_VALUES_AS_STRING: MutableMap<Type?, String?>

    init {
        val map: MutableMap<Type?, String?> = HashMap<Type?, String?>()
        map.put(Type.BYTE_TYPE, "0")
        map.put(Type.SHORT_TYPE, "0")
        map.put(Type.INT_TYPE, "0")
        map.put(Type.LONG_TYPE, "0L")
        map.put(Type.FLOAT_TYPE, "0.0f")
        map.put(Type.DOUBLE_TYPE, "0.0")
        map.put(Type.CHAR_TYPE, "'\\u0000'")
        map.put(Type.BOOLEAN_TYPE, "false")
        PRIMITIVE_TYPES_DEFAULT_VALUES_AS_STRING = Collections.unmodifiableMap<Type?, String?>(map)
    }

    fun getDefaultValue(type: Type?): String? {
        return PRIMITIVE_TYPES_DEFAULT_VALUES_AS_STRING.getOrDefault(type, "null")
    }

    /**
     * Converts an ASM [Type] to a JavaPoet [TypeName].
     */
    fun typeName(type: Type): TypeName? {
        if (type == Type.VOID_TYPE) {
            return ClassName.VOID
        }
        if (type == Type.BOOLEAN_TYPE) {
            return ClassName.BOOLEAN
        }
        if (type == Type.CHAR_TYPE) {
            return ClassName.CHAR
        }
        if (type == Type.BYTE_TYPE) {
            return ClassName.BYTE
        }
        if (type == Type.SHORT_TYPE) {
            return ClassName.SHORT
        }
        if (type == Type.INT_TYPE) {
            return ClassName.INT
        }
        if (type == Type.FLOAT_TYPE) {
            return ClassName.FLOAT
        }
        if (type == Type.LONG_TYPE) {
            return ClassName.LONG
        }
        if (type == Type.DOUBLE_TYPE) {
            return ClassName.DOUBLE
        }
        if (type.getSort() == Type.ARRAY) {
            return ArrayTypeName.of(typeName(type.getElementType()))
        }
        return className(type)
    }

    fun getTypeParameter(typeName: TypeName?, index: Int): Optional<TypeName?> {
        if (typeName is ParameterizedTypeName && typeName.typeArguments.size > index) {
            return Optional.of<TypeName?>(typeName.typeArguments.get(index))
        }
        return Optional.empty<TypeName?>()
    }

    fun className(type: Type): ClassName? {
        // If type contains $$ as $$BridgeFor$$ we keep $$
        // in the name instead of translating it to an inner class name
        return NameUtil.getClassName(
            type.getClassName()
                .replace("$$", "#")
                .replace("$", ".")
                .replace("#", "$$")
        )
    }
}
