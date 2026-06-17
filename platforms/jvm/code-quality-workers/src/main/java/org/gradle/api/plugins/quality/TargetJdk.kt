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
package org.gradle.api.plugins.quality

import org.gradle.internal.deprecation.DeprecationLogger.deprecateType

/**
 * Represents the PMD targetjdk property available for PMD &lt; 5.0.
 *
 */
@Deprecated(
    """This type is only used by the deprecated {@code targetJdk} property on the PMD plugin.
      It will be removed in Gradle 10. PMD 5.0 and later infer the Java language version from the rule sets."""
)
enum class TargetJdk {
    VERSION_1_3,
    VERSION_1_4,
    VERSION_1_5,
    VERSION_1_6,
    VERSION_1_7,
    VERSION_JSP;

    override fun toString(): String {
        return this.name
    }

    val name: String
        get() = field.substring("VERSION_".length).replace('_', '.').lowercase()

    companion object {
        /**
         * Converts the given object into a `TargetJdk`.
         *
         * @param value An object whose toString() value is to be converted. May be null.
         * @return The version, or null if the provided value is null.
         * @throws IllegalArgumentException when the provided value cannot be converted.
         */
        @Throws(IllegalArgumentException::class)
        fun toVersion(value: Any?): TargetJdk? {
            deprecateType(TargetJdk::class.java)
                .willBeRemovedInGradle10()
                .withUpgradeGuideSection(9, "deprecated_pmd_target_jdk")!!
                .nagUser()
            if (value == null) {
                return null
            }
            if (value is TargetJdk) {
                return value
            }

            val name = value.toString()
            if (name.equals("1.7", ignoreCase = true)) {
                return TargetJdk.VERSION_1_7
            } else if (name.equals("1.6", ignoreCase = true)) {
                return TargetJdk.VERSION_1_6
            } else if (name.equals("1.5", ignoreCase = true)) {
                return TargetJdk.VERSION_1_5
            } else if (name.equals("1.4", ignoreCase = true)) {
                return TargetJdk.VERSION_1_4
            } else if (name.equals("1.3", ignoreCase = true)) {
                return TargetJdk.VERSION_1_3
            } else if (name.equals("jsp", ignoreCase = true)) {
                return TargetJdk.VERSION_JSP
            } else {
                throw IllegalArgumentException(String.format("Could not determine targetjdk from '%s'.", name))
            }
        }
    }
}
