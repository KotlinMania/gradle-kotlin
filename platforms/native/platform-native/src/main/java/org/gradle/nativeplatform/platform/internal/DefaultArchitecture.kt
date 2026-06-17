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
package org.gradle.nativeplatform.platform.internal

class DefaultArchitecture(private val name: String) : ArchitectureInternal {
    override fun getName(): String {
        return name
    }

    override fun toString(): String {
        return displayName!!
    }

    val displayName: String?
        get() = "architecture '" + name + "'"

    override fun isI386(): Boolean {
        return Architectures.X86.isAlias(name)
    }

    override fun isAmd64(): Boolean {
        return Architectures.X86_64.isAlias(name)
    }

    override fun isIa64(): Boolean {
        return Architectures.IA_64.isAlias(name)
    }

    override fun isArm32(): Boolean {
        return Architectures.ARM_V7.isAlias(name)
    }

    override fun isArm64(): Boolean {
        return Architectures.AARCH64.isAlias(name)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val other = o as DefaultArchitecture
        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}
