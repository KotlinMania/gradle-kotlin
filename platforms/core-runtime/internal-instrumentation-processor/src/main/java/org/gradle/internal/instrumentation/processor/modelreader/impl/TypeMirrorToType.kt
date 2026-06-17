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

import org.objectweb.asm.Type
import java.util.Collections
import java.util.function.Consumer
import javax.lang.model.element.Element
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ErrorType
import javax.lang.model.type.ExecutableType
import javax.lang.model.type.IntersectionType
import javax.lang.model.type.NoType
import javax.lang.model.type.NullType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVariable
import javax.lang.model.type.UnionType
import javax.lang.model.type.WildcardType
import javax.lang.model.util.AbstractTypeVisitor8

class TypeMirrorToType : AbstractTypeVisitor8<Type?, Void?>() {
    override fun visitPrimitive(t: PrimitiveType, unused: Void?): Type {
        val kind = t.getKind()
        if (kind == TypeKind.INT) {
            return Type.INT_TYPE
        }
        if (kind == TypeKind.BYTE) {
            return Type.BYTE_TYPE
        }
        if (kind == TypeKind.BOOLEAN) {
            return Type.BOOLEAN_TYPE
        }
        if (kind == TypeKind.DOUBLE) {
            return Type.DOUBLE_TYPE
        }
        if (kind == TypeKind.FLOAT) {
            return Type.FLOAT_TYPE
        }
        if (kind == TypeKind.LONG) {
            return Type.LONG_TYPE
        }
        if (kind == TypeKind.SHORT) {
            return Type.SHORT_TYPE
        }
        if (kind == TypeKind.VOID) {
            return Type.VOID_TYPE
        }
        throw unsupportedType(t)
    }

    override fun visitNoType(t: NoType?, unused: Void?): Type {
        return Type.VOID_TYPE
    }

    override fun visitNull(t: NullType?, unused: Void?): Type? {
        throw unsupportedType(t)
    }

    override fun visitArray(t: ArrayType, unused: Void?): Type {
        return Type.getObjectType("[" + visit(t.getComponentType(), null)!!.getDescriptor())
    }

    override fun visitDeclared(t: DeclaredType, unused: Void?): Type? {
        val typeNesting: MutableList<Element?> = ArrayList<Element?>()
        var current = t.asElement()
        // In Java 9+, we also get `ModuleElement`s here, which do not contribute to the type name.
        while (current is TypeElement || current is PackageElement) {
            typeNesting.add(current)
            current = current.getEnclosingElement()
        }
        Collections.reverse(typeNesting)

        // TODO: replace with javax.lang.model.util.Elements.getBinaryName, which is a more universal way but requires refactoring and passing the utility around
        val typeName = StringBuilder("L")
        typeNesting.forEach(Consumer { element: Element? ->
            if (element is PackageElement) {
                typeName.append(element.getQualifiedName().toString().replace(".", "/")).append("/")
            } else {
                typeName.append(element!!.getSimpleName().toString())
                if (element !== typeNesting.get(typeNesting.size - 1)) {
                    typeName.append("$")
                }
            }
        })
        typeName.append(";")

        return Type.getType(typeName.toString())
    }

    override fun visitError(t: ErrorType?, unused: Void?): Type? {
        throw unsupportedType(t)
    }

    override fun visitTypeVariable(t: TypeVariable?, unused: Void?): Type? {
        throw unsupportedType(t)
    }

    override fun visitWildcard(t: WildcardType?, unused: Void?): Type? {
        throw unsupportedType(t)
    }

    override fun visitExecutable(t: ExecutableType?, unused: Void?): Type? {
        throw unsupportedType(t)
    }

    override fun visitIntersection(t: IntersectionType?, unused: Void?): Type? {
        throw unsupportedType(t)
    }

    override fun visitUnion(t: UnionType?, unused: Void?): Type? {
        throw unsupportedType(t)
    }

    companion object {
        private fun unsupportedType(t: TypeMirror?): UnsupportedOperationException {
            return UnsupportedOperationException("unsupported type " + t)
        }
    }
}
