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
import org.gradle.language.nativeplatform.internal.Expression
import org.gradle.language.nativeplatform.internal.IncludeType

/**
 * An #include directive whose body is a macro function call.
 */
class IncludeWithMacroFunctionCallExpression(private val name: String, private val isImport: Boolean, private val arguments: ImmutableList<Expression?>) : AbstractInclude() {
    override fun getType(): IncludeType {
        return IncludeType.MACRO_FUNCTION
    }

    override fun getValue(): String {
        return name
    }

    override fun isImport(): Boolean {
        return isImport
    }

    override fun getArguments(): MutableList<Expression?> {
        return arguments
    }

    override fun equals(obj: Any?): Boolean {
        if (obj === this) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other = obj as IncludeWithMacroFunctionCallExpression
        return name == other.name && isImport == other.isImport && arguments == other.arguments
    }

    override fun hashCode(): Int {
        return name.hashCode() xor arguments.hashCode()
    }
}
