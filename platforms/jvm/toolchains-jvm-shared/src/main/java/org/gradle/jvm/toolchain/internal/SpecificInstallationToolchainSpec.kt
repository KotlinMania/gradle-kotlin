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
package org.gradle.jvm.toolchain.internal

import com.google.common.base.MoreObjects
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.provider.PropertyFactory
import java.io.File
import java.util.Objects
import javax.inject.Inject

class SpecificInstallationToolchainSpec @Inject constructor(propertyFactory: PropertyFactory, javaHome: File?) : DefaultToolchainSpec(propertyFactory) {
    class Key(javaHome: File?) : JavaToolchainSpecInternal.Key {
        private val javaHome: File?

        init {
            this.javaHome = javaHome
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val key = o as Key
            return javaHome == key.javaHome
        }

        override fun hashCode(): Int {
            return Objects.hash(javaHome)
        }
    }

    val javaHome: File?

    init {
        this.javaHome = javaHome

        // disallow changing property values
        finalizeProperties()
    }

    override fun toKey(): Key {
        return Key(javaHome)
    }

    override fun isConfigured(): Boolean {
        return true
    }

    override fun isValid(): Boolean {
        return true
    }

    override fun getDisplayName(): String {
        return MoreObjects.toStringHelper("SpecificToolchain ").add("javaHome", javaHome).toString()
    }

    override fun toString(): String {
        return getDisplayName()
    }

    companion object {
        fun fromJavaHome(propertyFactory: PropertyFactory, javaHome: File): SpecificInstallationToolchainSpec {
            if (javaHome.exists()) {
                if (javaHome.isDirectory()) {
                    return SpecificInstallationToolchainSpec(propertyFactory, javaHome)
                } else {
                    throw InvalidUserDataException("The configured Java home is not a directory (" + javaHome.getAbsolutePath() + ")")
                }
            } else {
                throw InvalidUserDataException("The configured Java home does not exist (" + javaHome.getAbsolutePath() + ")")
            }
        }

        @JvmStatic
        fun fromJavaExecutable(propertyFactory: PropertyFactory, executable: String?): SpecificInstallationToolchainSpec {
            return SpecificInstallationToolchainSpec(propertyFactory, JavaExecutableUtils.resolveJavaHomeOfExecutable(executable))
        }
    }
}
