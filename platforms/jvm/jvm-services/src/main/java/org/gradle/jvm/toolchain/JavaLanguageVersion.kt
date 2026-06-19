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
package org.gradle.jvm.toolchain

import org.gradle.api.Incubating
import org.gradle.internal.HasInternalProtocol
import org.gradle.jvm.toolchain.internal.DefaultJavaLanguageVersion

/**
 * Represents a Java Language version
 *
 * @since 6.7
 */
@HasInternalProtocol
interface JavaLanguageVersion : Comparable<JavaLanguageVersion> {
    /**
     * Return this version as a number, 14 for Java 14.
     *
     *
     * Given the type used, this method returns the simple version even for versions lower than 5.
     *
     * @return the version number
     * @see .toString
     */
    fun asInt(): Int

    /**
     * Return this version as a String, "14" for Java 14.
     *
     *
     * This method will return `1.<version>` when the version is lower than 5.
     *
     * @since 6.8
     * @return the version number
     */
    override fun toString(): String

    /**
     * Indicates if this version can compile or run code based on the passed in language version.
     *
     *
     * For example, Java 14 can compile or run code from Java 11, but not the opposite.
     *
     * @param other the language version to check
     *
     * @return `true` if this version can compile or run code from the other version, `false` otherwise
     */
    fun canCompileOrRun(other: JavaLanguageVersion): Boolean

    /**
     * Indicates if this version can compile or run code based on the passed in language version.
     *
     *
     * For example, Java 14 can compile or run code from Java 11, but not the opposite.
     *
     * @param otherVersion the language version to check, as an `int`
     *
     * @return `true` if this version can compile or run code from the other version, `false` otherwise
     */
    fun canCompileOrRun(otherVersion: Int): Boolean

    companion object {
        @JvmStatic
        fun of(version: Int): JavaLanguageVersion {
            return DefaultJavaLanguageVersion.Companion.of(version)!!
        }

        @JvmStatic
        fun of(version: String): JavaLanguageVersion {
            try {
                return of(version.toInt())
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("JavaLanguageVersion must be a positive integer, not '" + version + "'")
            }
        }

        /**
         * Get the current (i.e., the current runtime) Java Language version.
         *
         * @return the current Java Language version
         * @since 8.8
         */
        @JvmStatic
        @Incubating
        fun current(): JavaLanguageVersion {
            return DefaultJavaLanguageVersion.Companion.fromFullVersion(System.getProperty("java.version"))!!
        }
    }
}
