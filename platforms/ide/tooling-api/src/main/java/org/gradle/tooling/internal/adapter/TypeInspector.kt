/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.tooling.internal.adapter

import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import javax.annotation.concurrent.ThreadSafe

@ThreadSafe
internal class TypeInspector {
    private val stopAt: MutableSet<Class<*>> = HashSet<Class<*>>()
    private val inspected: MutableMap<Class<*>, MutableSet<Class<*>>> = HashMap<Class<*>, MutableSet<Class<*>>>()

    init {
        stopAt.add(MutableList::class.java)
        stopAt.add(MutableSet::class.java)
        stopAt.add(MutableCollection::class.java)
        stopAt.add(MutableMap::class.java)
    }

    /**
     * Returns all interfaces reachable from the given interface, including the interface itself.
     */
    fun getReachableTypes(type: Class<*>): MutableSet<Class<*>> {
        var types = inspected.get(type)
        if (types == null) {
            types = HashSet<Class<*>>()
            visit(type, types)
            inspected.put(type, types)
        }
        return types
    }

    private fun visit(type: Class<*>, types: MutableSet<Class<*>>) {
        if (type.isArray()) {
            visit(type.getComponentType(), types)
            return
        }

        if (!type.isInterface() || !types.add(type) || stopAt.contains(type)) {
            return
        }

        val preventEndlessRecursiveSetInClassDefinition: MutableSet<Type> = HashSet<Type>()
        for (superType in type.getGenericInterfaces()) {
            visit(superType, types, preventEndlessRecursiveSetInClassDefinition)
        }

        for (method in type.getDeclaredMethods()) {
            val methodSet: MutableSet<Type> = HashSet<Type>()
            visit(method.getGenericReturnType(), types, methodSet)
            for (typeVariable in method.getTypeParameters()) {
                visit(typeVariable, types, methodSet)
            }
        }
    }

    private fun visit(type: Type, types: MutableSet<Class<*>>, preventEndlessRecursiveSet: MutableSet<Type>) {
        if (!preventEndlessRecursiveSet.add(type)) {
            return
        }
        if (type is Class<*>) {
            visit(type, types)
        } else if (type is ParameterizedType) {
            val parameterizedType = type
            visit(parameterizedType.getRawType(), types, preventEndlessRecursiveSet)
            for (typeArg in parameterizedType.getActualTypeArguments()) {
                visit(typeArg, types, preventEndlessRecursiveSet)
            }
        } else if (type is WildcardType) {
            val wildcardType = type
            for (bound in wildcardType.getUpperBounds()) {
                visit(bound, types, preventEndlessRecursiveSet)
            }
            for (bound in wildcardType.getLowerBounds()) {
                visit(bound, types, preventEndlessRecursiveSet)
            }
        } else if (type is GenericArrayType) {
            val arrayType = type
            visit(arrayType.getGenericComponentType(), types, preventEndlessRecursiveSet)
        } else if (type is TypeVariable<*>) {
            val typeVariable = type
            for (bound in typeVariable.getBounds()) {
                visit(bound, types, preventEndlessRecursiveSet)
            }
        }
    }
}
