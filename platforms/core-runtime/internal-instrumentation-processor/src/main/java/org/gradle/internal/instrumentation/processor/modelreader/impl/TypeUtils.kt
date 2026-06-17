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
package org.gradle.internal.instrumentation.processor.modelreader.impl

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import org.objectweb.asm.Type
import java.util.Comparator
import java.util.Optional
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror

object TypeUtils {
    fun extractType(typeMirror: TypeMirror): Type {
        return requireNotNull(typeMirror.accept<Type?, Void?>(TypeMirrorToType(), null))
    }

    fun extractRawType(typeName: TypeName): Type? {
        if (typeName.isPrimitive()) {
            if (typeName == TypeName.BOOLEAN) {
                return Type.BOOLEAN_TYPE
            }
            if (typeName == TypeName.BYTE) {
                return Type.BYTE_TYPE
            }
            if (typeName == TypeName.SHORT) {
                return Type.SHORT_TYPE
            }
            if (typeName == TypeName.INT) {
                return Type.INT_TYPE
            }
            if (typeName == TypeName.LONG) {
                return Type.LONG_TYPE
            }
            if (typeName == TypeName.CHAR) {
                return Type.CHAR_TYPE
            }
            if (typeName == TypeName.FLOAT) {
                return Type.FLOAT_TYPE
            }
            if (typeName == TypeName.DOUBLE) {
                return Type.DOUBLE_TYPE
            }
        } else if (typeName == TypeName.VOID) {
            return Type.VOID_TYPE
        }

        val className: ClassName
        if (typeName is ParameterizedTypeName) {
            className = typeName.rawType
        } else if (typeName is ClassName) {
            className = typeName
        } else {
            throw IllegalArgumentException("Not supported to extract raw type from: " + typeName.javaClass)
        }
        return Type.getType("L" + className.reflectionName().replace('.', '/') + ";")
    }

    fun extractReturnType(methodElement: ExecutableElement): Type {
        return extractType(methodElement.getReturnType())
    }

    fun extractMethodDescriptor(methodElement: ExecutableElement): String {
        val parameterTypes = methodElement.getParameters().stream()
            .map { variableElement: VariableElement -> extractType(variableElement.asType()) }
            .toArray<Type?> { size: Int -> arrayOfNulls<Type>(size) }
        return Type.getMethodDescriptor(extractReturnType(methodElement), *parameterTypes.filterNotNull().toTypedArray())
    }

    fun getTypeParameter(typeMirror: TypeMirror?, index: Int): Optional<TypeName> {
        if (typeMirror is DeclaredType && typeMirror.getTypeArguments().size > index) {
            return Optional.of<TypeName?>(TypeName.get(typeMirror.getTypeArguments().get(index)))
        }
        return Optional.empty<TypeName?>()
    }

    fun getTypeParameterOrThrow(typeMirror: TypeMirror?, index: Int): TypeName {
        return getTypeParameter(typeMirror, index).orElseThrow<IllegalArgumentException?>(Supplier {
            IllegalArgumentException(
                String.format(
                    "Missing type parameter with index %s for %s",
                    index,
                    typeMirror
                )
            )
        })
    }

    fun getExecutableElementsFromElements(elements: Stream<out Element?>): MutableList<ExecutableElement?> {
        return elements
            .flatMap<Element?> { element: Element? -> if (element!!.getKind() == ElementKind.METHOD) Stream.of(element) else element.getEnclosedElements().stream() }
            .filter { it: Element? -> it!!.getKind() == ElementKind.METHOD }
            .map<ExecutableElement?> { it: Element? -> it as ExecutableElement }  // Ensure that the elements have a stable order, as the annotation processing engine does not guarantee that for type elements.
            // The order in which the executable elements are listed should be the order in which they appear in the code but
            // we take an extra measure of care here and ensure the ordering between all elements.
            .sorted(Comparator.comparing<ExecutableElement?, String> { obj: ExecutableElement? -> TypeUtils.elementQualifiedName(obj!!) })
            .distinct()
            .collect(Collectors.toList())
    }

    @JvmStatic
    fun elementQualifiedName(element: Element): String {
        if (element is ExecutableElement) {
            val enclosingTypeName: String = (element.getEnclosingElement() as TypeElement).getQualifiedName().toString()
            return enclosingTypeName + "." + element.getSimpleName()
        } else if (element is TypeElement) {
            return element.getQualifiedName().toString()
        } else {
            throw IllegalArgumentException("Unsupported element type to read qualified name from: " + element.javaClass)
        }
    }
}
