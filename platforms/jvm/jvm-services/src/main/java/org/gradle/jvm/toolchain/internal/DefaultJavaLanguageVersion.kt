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

import org.gradle.api.internal.jvm.JavaVersionParser.parseMajorVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.io.Serializable

class DefaultJavaLanguageVersion private constructor(private val version: Int) : JavaLanguageVersion, Serializable {
    override fun asInt(): Int {
        return version
    }

    override fun toString(): String {
        if (version < 5) {
            return String.format("1.%d", version)
        }
        return version.toString()
    }

    override fun canCompileOrRun(other: JavaLanguageVersion): Boolean {
        return compareTo(other) >= 0
    }

    override fun compareTo(other: JavaLanguageVersion): Int {
        return Integer.compare(version, other.asInt())
    }

    override fun canCompileOrRun(otherVersion: Int): Boolean {
        return version >= otherVersion
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultJavaLanguageVersion
        return version == that.version
    }

    override fun hashCode(): Int {
        return version
    }

    companion object {
        const val LOWER_CACHED_VERSION: Int = 4
        const val HIGHER_CACHED_VERSION: Int = 19
        val KNOWN_VERSIONS: Array<JavaLanguageVersion?>

        init {
            KNOWN_VERSIONS = arrayOfNulls<JavaLanguageVersion>(HIGHER_CACHED_VERSION - LOWER_CACHED_VERSION + 1)
            for (version in LOWER_CACHED_VERSION..HIGHER_CACHED_VERSION) {
                KNOWN_VERSIONS[version - LOWER_CACHED_VERSION] = DefaultJavaLanguageVersion(version)
            }
        }

        /**
         * A constant representing an unknown Java language version.
         */
        @JvmField
        val UNKNOWN: JavaLanguageVersion = object : JavaLanguageVersion {
            override fun asInt(): Int {
                return -1
            }

            override fun canCompileOrRun(other: JavaLanguageVersion): Boolean {
                return false
            }

            override fun canCompileOrRun(otherVersion: Int): Boolean {
                return false
            }

            override fun compareTo(javaLanguageVersion: JavaLanguageVersion): Int {
                return Integer.compare(asInt(), javaLanguageVersion.asInt())
            }

            override fun toString(): String {
                return "unknown"
            }
        }

        fun of(version: Int): JavaLanguageVersion? {
            require(version > 0) { "JavaLanguageVersion must be a positive integer, not '" + version + "'" }
            if (version >= LOWER_CACHED_VERSION && version <= HIGHER_CACHED_VERSION) {
                return KNOWN_VERSIONS[version - LOWER_CACHED_VERSION]
            } else {
                return DefaultJavaLanguageVersion(version)
            }
        }

        @JvmStatic
        fun fromFullVersion(version: String): JavaLanguageVersion? {
            return of(parseMajorVersion(version))
        }
    }
}
