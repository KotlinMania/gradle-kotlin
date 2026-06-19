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
package org.gradle.internal.jvm.inspection

import java.util.regex.Pattern

interface JvmVendor {
    enum class KnownJvmVendor {
        ADOPTIUM("adoptium", "temurin|adoptium|eclipse foundation", "Eclipse Temurin"),
        ADOPTOPENJDK("adoptopenjdk", "aoj|adoptopenjdk", "AdoptOpenJDK"),
        AMAZON("amazon", "amazon|corretto", "Amazon Corretto"),
        APPLE("apple", "Apple"),
        AZUL("azul systems", "azul|zulu", "Azul Zulu"),
        BELLSOFT("bellsoft", "bellsoft|liberica", "BellSoft Liberica"),
        GRAAL_VM("graalvm community", "graalvm|graal vm", "GraalVM Community"),
        HEWLETT_PACKARD("hewlett-packard", "hp|hewlett", "HP-UX"),
        IBM("ibm", "ibm|semeru|international business machines corporation", "IBM"),
        JETBRAINS("jetbrains", "jbr|jetbrains", "JetBrains"),
        MICROSOFT("microsoft", "Microsoft"),
        ORACLE("oracle", "Oracle"),
        SAP("sap se", "sap", "SAP SapMachine"),
        TENCENT("tencent", "tencent|kona", "Tencent"),
        UNKNOWN("gradle", "Unknown Vendor");

        private val indicatorString: String
        private val indicatorPattern: Pattern
        val displayName: String

        constructor(indicatorString: String, displayName: String) {
            this.indicatorString = indicatorString
            this.indicatorPattern = Pattern.compile(indicatorString, Pattern.CASE_INSENSITIVE)
            this.displayName = displayName
        }

        constructor(indicatorString: String, pattern: String, displayName: String) {
            this.indicatorString = indicatorString
            this.indicatorPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            this.displayName = displayName
        }

        fun asJvmVendor(): JvmVendor {
            return fromString(indicatorString)
        }

        companion object {
            fun parse(rawVendor: String?): KnownJvmVendor {
                if (rawVendor == null) {
                    return KnownJvmVendor.UNKNOWN
                }
                for (jvmVendor in entries) {
                    if (jvmVendor.name == rawVendor) {
                        return jvmVendor
                    }
                    if (jvmVendor.indicatorString == rawVendor) {
                        return jvmVendor
                    }
                    if (jvmVendor.indicatorPattern.matcher(rawVendor).find()) {
                        return jvmVendor
                    }
                }
                return KnownJvmVendor.UNKNOWN
            }
        }
    }

    val rawVendor: String

    val knownVendor: KnownJvmVendor

    val displayName: String

    companion object {
        fun fromString(vendor: String?): JvmVendor {
            val rawVendor = vendor ?: ""
            return object : JvmVendor {
                override val rawVendor: String = rawVendor

                override val knownVendor: KnownJvmVendor = KnownJvmVendor.Companion.parse(vendor)

                override val displayName: String
                    get() = if (knownVendor != KnownJvmVendor.UNKNOWN) knownVendor.displayName else rawVendor
            }
        }
    }
}
