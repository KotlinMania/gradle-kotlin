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
package org.gradle.internal.instrumentation.util

import com.google.common.base.Strings
import com.squareup.javapoet.ClassName
import org.gradle.internal.instrumentation.model.CallableInfo
import org.gradle.internal.instrumentation.model.CallableKindInfo
import org.objectweb.asm.Type
import java.util.regex.Pattern
import kotlin.text.titlecaseChar
import kotlin.text.uppercase

object NameUtil {
    private val UPPER_CASE: Pattern = Pattern.compile("(?=\\p{Upper})")

    fun getterName(propertyName: String, propertyType: Type): String {
        val prefix = if (propertyType == Type.BOOLEAN_TYPE || propertyType.getClassName() == "java.lang.Boolean") "is" else "get"
        return prefix + capitalize(propertyName)
    }

    fun setterName(propertyName: String): String {
        return "set" + capitalize(propertyName)
    }

    fun capitalize(value: String): String {
        return if (Strings.isNullOrEmpty(value))
            value
        else
            value.get(0).titlecaseChar().toString() + value.substring(1)
    }

    fun camelToUpperUnderscoreCase(camelCase: String): String {
        val split = UPPER_CASE.split(camelCase)
        for (i in split.indices) {
            split[i] = split[i].uppercase()
        }
        return split.joinToString("_")
    }

    fun interceptedJvmMethodName(callableInfo: CallableInfo): String {
        if (callableInfo.kind === CallableKindInfo.GROOVY_PROPERTY_GETTER) {
            return NameUtil.getterName(callableInfo.callableName!!, callableInfo.returnType!!.type!!)
        }
        if (callableInfo.kind === CallableKindInfo.GROOVY_PROPERTY_SETTER) {
            return NameUtil.setterName(callableInfo.callableName!!)
        }
        return callableInfo.callableName!!
    }

    /**
     * ClassName that correctly resolves name for classes that starts with $
     */
    fun getClassName(fullClassName: String): ClassName {
        val splitted = fullClassName.split("[.]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val className = splitted[splitted.size - 1]
        val packageName = fullClassName.replace("." + className, "")
        return ClassName.get(packageName, className)
    }
}
