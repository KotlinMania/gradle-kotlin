/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.service

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

internal object TypeStringFormatter {
    fun format(type: Type): String {
        if (type is Class<*>) {
            val aClass = type
            val enclosingClass = aClass.getEnclosingClass()
            if (enclosingClass != null) {
                val ownName = if (aClass.isAnonymousClass()) "<anonymous>" else aClass.getSimpleName()
                return format(enclosingClass) + "$" + ownName
            } else {
                return aClass.getSimpleName()
            }
        } else if (type is ParameterizedType) {
            val parameterizedType = type
            val builder = StringBuilder()
            builder.append(format(parameterizedType.getRawType()))
            builder.append("<")
            for (i in parameterizedType.getActualTypeArguments().indices) {
                val typeParam = parameterizedType.getActualTypeArguments()[i]
                if (i > 0) {
                    builder.append(", ")
                }
                builder.append(format(typeParam))
            }
            builder.append(">")
            return builder.toString()
        }

        return type.toString()
    }
}
