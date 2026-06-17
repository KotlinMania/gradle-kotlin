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
import org.gradle.internal.jvm.inspection.JvmVendor
import org.gradle.jvm.toolchain.JvmVendorSpec.Companion.ADOPTIUM
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec

/**
 * Represents a filter for a vendor of a Java Virtual Machine implementation.
 *
 * @since 6.8
 */
@HasInternalProtocol
abstract class JvmVendorSpec {
    /**
     * Determines if the vendor passed as an argument matches this spec.
     * @param vendor the vendor to test
     * @return true if this spec matches the vendor
     *
     * @since 7.6
     */
    @Incubating
    abstract fun matches(vendor: String): Boolean

    companion object {
        /**
         * A constant for using [Eclipse Adoptium](https://projects.eclipse.org/projects/adoptium) as the JVM vendor.
         *
         * @since 7.4
         */
        val ADOPTIUM: JvmVendorSpec = matching(JvmVendor.KnownJvmVendor.ADOPTIUM)

        val ADOPTOPENJDK: JvmVendorSpec = matching(JvmVendor.KnownJvmVendor.ADOPTOPENJDK)

        val AMAZON: JvmVendorSpec = matching(JvmVendor.KnownJvmVendor.AMAZON)

        val APPLE: JvmVendorSpec = matching(JvmVendor.KnownJvmVendor.APPLE)

        val AZUL: JvmVendorSpec = matching(JvmVendor.KnownJvmVendor.AZUL)

        val BELLSOFT: JvmVendorSpec = matching(JvmVendor.KnownJvmVendor.BELLSOFT)

        /**
         * A constant for using [GraalVM](https://www.graalvm.org/) as the JVM vendor.
         *
         * @since 7.1
         */
        val GRAAL_VM: JvmVendorSpec = matching(JvmVendor.KnownJvmVendor.GRAAL_VM)

        val HEWLETT_PACKARD: JvmVendorSpec = matching(JvmVendor.KnownJvmVendor.HEWLETT_PACKARD)

        val IBM: JvmVendorSpec = matching(JvmVendor.KnownJvmVendor.IBM)

        /**
         * A constant for using [JetBrains Runtime](https://www.jetbrains.com/jetbrains-runtime) as the JVM vendor.
         *
         * @since 8.4
         */
        @Incubating
        val JETBRAINS: JvmVendorSpec = matching(JvmVendor.KnownJvmVendor.JETBRAINS)

        /**
         * A constant for using [Microsoft OpenJDK](https://www.microsoft.com/openjdk) as the JVM vendor.
         *
         * @since 7.3
         */
        val MICROSOFT: JvmVendorSpec = matching(JvmVendor.KnownJvmVendor.MICROSOFT)

        val ORACLE: JvmVendorSpec = matching(JvmVendor.KnownJvmVendor.ORACLE)

        val SAP: JvmVendorSpec = matching(JvmVendor.KnownJvmVendor.SAP)

        /**
         * A constant for using [Tencent Kona JDK](https://tencent.github.io/konajdk) as the JVM vendor.
         *
         * @since 8.6
         */
        @Incubating
        val TENCENT: JvmVendorSpec = matching(JvmVendor.KnownJvmVendor.TENCENT)

        /**
         * Returns a vendor spec that matches a VM by its vendor.
         *
         *
         * A VM is determined eligible if the system property `java.vendor` contains
         * the given match string. The comparison is done case-insensitive.
         *
         * @param match the sequence to search for
         * @return a new filter object
         */
        fun matching(match: String): JvmVendorSpec {
            return DefaultJvmVendorSpec.Companion.matching(match)
        }

        /**
         * Returns a vendor spec that matches a VM by its vendor.
         *
         *
         * The passed in vendor will first be matched against the known vendors.
         * If there is a match, then the vendor spec matching the known vendor will be returned.
         *
         * @param vendor the vendor string to match
         * @return a `JvmVendorSpec` that matches the given vendor
         * @see ADOPTIUM ADOPTIUM and others known vendor specs
         *
         *
         * @since 8.13
         */
        @JvmStatic
        @Incubating
        fun of(vendor: String): JvmVendorSpec {
            val knownVendor: JvmVendor.KnownJvmVendor = JvmVendor.Companion.fromString(vendor).getKnownVendor()
            if (knownVendor != JvmVendor.KnownJvmVendor.UNKNOWN) {
                return matching(knownVendor)
            } else {
                return matching(vendor)
            }
        }

        private fun matching(vendor: JvmVendor.KnownJvmVendor): JvmVendorSpec {
            return DefaultJvmVendorSpec.Companion.of(vendor)
        }
    }
}
