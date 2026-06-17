/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api

import org.gradle.api.internal.jvm.JavaVersionParser

/**
 * An enumeration of Java versions.
 * Before 9: http://www.oracle.com/technetwork/java/javase/versioning-naming-139433.html
 * 9+: http://openjdk.java.net/jeps/223
 */
enum class JavaVersion {
    VERSION_1_1, VERSION_1_2, VERSION_1_3, VERSION_1_4,
    VERSION_1_5, VERSION_1_6, VERSION_1_7, VERSION_1_8,
    VERSION_1_9, VERSION_1_10,

    /**
     * Java 11 major version.
     *
     * @since 4.7
     */
    VERSION_11,

    /**
     * Java 12 major version.
     *
     * @since 5.0
     */
    VERSION_12,

    /**
     * Java 13 major version.
     *
     * @since 6.0
     */
    VERSION_13,

    /**
     * Java 14 major version.
     *
     * @since 6.3
     */
    VERSION_14,

    /**
     * Java 15 major version.
     *
     * @since 6.3
     */
    VERSION_15,

    /**
     * Java 16 major version.
     *
     * @since 6.3
     */
    VERSION_16,

    /**
     * Java 17 major version.
     *
     * @since 6.3
     */
    VERSION_17,

    /**
     * Java 18 major version.
     *
     * @since 7.0
     */
    VERSION_18,

    /**
     * Java 19 major version.
     *
     * @since 7.0
     */
    VERSION_19,

    /**
     * Java 20 major version.
     *
     * @since 7.0
     */
    VERSION_20,

    /**
     * Java 21 major version.
     *
     * @since 7.6
     */
    VERSION_21,

    /**
     * Java 22 major version.
     *
     * @since 7.6
     */
    VERSION_22,

    /**
     * Java 23 major version.
     *
     * @since 7.6
     */
    VERSION_23,

    /**
     * Java 24 major version.
     *
     * @since 7.6
     */
    VERSION_24,

    /**
     * Java 25 major version.
     *
     * @since 8.4
     */
    VERSION_25,

    /**
     * Java 26 major version.
     *
     * @since 8.7
     */
    VERSION_26,

    /**
     * Java 27 major version.
     * Not officially supported by Gradle. Use at your own risk.
     *
     * @since 8.10
     */
    @Incubating
    VERSION_27,

    /**
     * Java 28 major version.
     * Not officially supported by Gradle. Use at your own risk.
     *
     * @since 8.14
     */
    @Incubating
    VERSION_28,

    /**
     * Java 29 major version.
     * Not officially supported by Gradle. Use at your own risk.
     *
     * @since 9.1.0
     */
    @Incubating
    VERSION_29,

    /**
     * Java 30 major version.
     * Not officially supported by Gradle. Use at your own risk.
     *
     * @since 9.4.0
     */
    @Incubating
    VERSION_30,

    /**
     * Higher version of Java.
     * @since 4.7
     */
    VERSION_HIGHER;

    private val versionName: String

    init {
        this.versionName = if (ordinal >= FIRST_MAJOR_VERSION_ORDINAL) this.majorVersion else "1." + this.majorVersion
    }

    /**
     * Returns the JVM class file major version for this Java version.
     *
     * @return The JVM class file major version for this Java version.
     *
     * @since 9.5.0
     */
    fun toClassVersion(): Int {
        return ordinal + 1 + CLASS_MAJOR_VERSION_OFFSET
    }

    val isJava5: Boolean
        get() = this == JavaVersion.VERSION_1_5

    val isJava6: Boolean
        get() = this == JavaVersion.VERSION_1_6

    val isJava7: Boolean
        get() = this == JavaVersion.VERSION_1_7

    val isJava8: Boolean
        get() = this == JavaVersion.VERSION_1_8

    val isJava9: Boolean
        get() = this == JavaVersion.VERSION_1_9

    val isJava10: Boolean
        get() = this == JavaVersion.VERSION_1_10

    val isJava11: Boolean
        /**
         * Returns if the version is Java 11.
         *
         * @since 4.7
         */
        get() = this == JavaVersion.VERSION_11

    val isJava12: Boolean
        /**
         * Returns if the version is Java 12.
         *
         * @since 5.0
         */
        get() = this == JavaVersion.VERSION_12

    val isJava5Compatible: Boolean
        get() = isCompatibleWith(JavaVersion.VERSION_1_5)

    val isJava6Compatible: Boolean
        get() = isCompatibleWith(JavaVersion.VERSION_1_6)

    val isJava7Compatible: Boolean
        get() = isCompatibleWith(JavaVersion.VERSION_1_7)

    val isJava8Compatible: Boolean
        get() = isCompatibleWith(JavaVersion.VERSION_1_8)

    val isJava9Compatible: Boolean
        get() = isCompatibleWith(JavaVersion.VERSION_1_9)

    val isJava10Compatible: Boolean
        get() = isCompatibleWith(JavaVersion.VERSION_1_10)

    val isJava11Compatible: Boolean
        /**
         * Returns if the version is Java 11 compatible.
         *
         * @since 4.7
         */
        get() = isCompatibleWith(JavaVersion.VERSION_11)

    val isJava12Compatible: Boolean
        /**
         * Returns if the version is Java 12 compatible.
         *
         * @since 5.0
         */
        get() = isCompatibleWith(JavaVersion.VERSION_12)

    /**
     * Returns if this version is compatible with the given version
     *
     * @since 6.0
     */
    fun isCompatibleWith(otherVersion: JavaVersion): Boolean {
        return this.compareTo(otherVersion) >= 0
    }

    override fun toString(): String {
        return versionName
    }

    val majorVersion: String
        get() = (ordinal + 1).toString()

    companion object {
        // Since Java 9, version should be X instead of 1.X
        private val FIRST_MAJOR_VERSION_ORDINAL = 9 - 1

        // Class file versions: 1.1 == 45, 1.2 == 46...
        private const val CLASS_MAJOR_VERSION_OFFSET = 44
        private var currentJavaVersion: JavaVersion? = null

        /**
         * Converts the given object into a `JavaVersion`.
         *
         * @param value An object whose toString() value is to be converted. May be null.
         * @return The version, or null if the provided value is null.
         * @throws IllegalArgumentException when the provided value cannot be converted.
         */
        @JvmStatic
        @Throws(IllegalArgumentException::class)  // We cannot annotate it as nullable as it would be a breaking change for Kotlin clients.
        fun toVersion(value: Any?): JavaVersion? {
            if (value == null) {
                return null
            }
            if (value is JavaVersion) {
                return value
            }
            if (value is Int) {
                return getVersionForMajor(value)
            }

            val name: String? = value.toString()
            return getVersionForMajor(JavaVersionParser.parseMajorVersion(name))
        }

        /**
         * Returns the version of the current JVM.
         *
         * @return The version of the current JVM.
         */
        @JvmStatic
        fun current(): JavaVersion? {
            var version: JavaVersion? = currentJavaVersion
            if (version == null) {
                version = toVersion(System.getProperty("java.version"))
                currentJavaVersion = version
            }
            return version
        }

        fun resetCurrent() {
            currentJavaVersion = null
        }

        @JvmStatic
        fun forClassVersion(classVersion: Int): JavaVersion? {
            return getVersionForMajor(classVersion - CLASS_MAJOR_VERSION_OFFSET)
        }

        fun forClass(classData: ByteArray): JavaVersion? {
            require(classData.size >= 8) { "Invalid class format. Should contain at least 8 bytes" }
            return forClassVersion(classData[7].toInt() and 0xFF)
        }

        private fun getVersionForMajor(major: Int): JavaVersion? {
            return if (major >= entries.toTypedArray().length) JavaVersion.VERSION_HIGHER else entries[major - 1]
        }
    }
}
