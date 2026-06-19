/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.jvm.toolchain.internal

import com.google.common.base.MoreObjects
import org.gradle.api.internal.provider.PropertyFactory
import java.io.File
import java.util.Objects
import javax.inject.Inject

/**
 * Represents a request for a Java toolchain using a specific 'java' executable.  The resulting toolchain
 * will only be able to provide that executable, and no other tools.
 */
class SpecificExecutableToolchainSpec @Inject constructor(propertyFactory: PropertyFactory, javaExecutable: File) : DefaultToolchainSpec(propertyFactory) {
    val javaExecutable: File

    class Key(javaExecutable: File?) : JavaToolchainSpecInternal.Key {
        private val javaExecutable: File?

        init {
            this.javaExecutable = javaExecutable
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val key = o as Key
            return javaExecutable == key.javaExecutable
        }

        override fun hashCode(): Int {
            return Objects.hash(javaExecutable)
        }
    }

    init {
        this.javaExecutable = javaExecutable
    }

    override fun toKey(): SpecificInstallationToolchainSpec.Key {
        return SpecificInstallationToolchainSpec.Key(javaExecutable)
    }

    override val isConfigured: Boolean
        get() = true

    override val isValid: Boolean
        get() = true

    val javaHome: File?
        get() =// This allows for a "normal" JDK layout where the 'java' executable is in JAVA_HOME/bin/java.
        // If such an executable is provided, and Gradle can probe the executable, the resulting toolchain
            // could potentially be used to provide other tools as well (e.g. a compiler or javadoc tool).
            javaExecutable.getParentFile().getParentFile()

    override fun getDisplayName(): String {
        return MoreObjects.toStringHelper("SpecificToolchain ").add("javaExecutable", javaExecutable).toString()
    }

    override fun toString(): String {
        return getDisplayName()
    }
}
