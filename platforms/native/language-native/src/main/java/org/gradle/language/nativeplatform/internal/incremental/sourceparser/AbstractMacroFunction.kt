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

import org.gradle.language.nativeplatform.internal.MacroFunction

abstract class AbstractMacroFunction internal constructor(private val name: String, private val parameters: Int) : MacroFunction {
    override fun toString(): String {
        return "#define " + getName() + "(...) " + this.body
    }

    protected open val body: String?
        get() = "=> ???"

    override fun getName(): String {
        return name
    }

    override fun getParameterCount(): Int {
        return parameters
    }

    override fun equals(obj: Any?): Boolean {
        if (obj === this) {
            return true
        }
        if (obj == null || obj.javaClass != javaClass) {
            return false
        }

        val other = obj as AbstractMacroFunction
        return name == other.name && parameters == other.parameters
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}
