/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.tasks.scala.internal

import org.gradle.api.tasks.scala.ScalaCompileOptions
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.internal.JavaToolchain
import org.gradle.util.internal.VersionNumber
import java.io.File

/**
 * Configures scala compile options to include the proper jvm target.
 * Gradle tool is not responsible for understanding if the version of Scala selected is compatible with the toolchain
 * Does not configure if Java Toolchain feature is not in use
 *
 * @since 7.3
 */
object ScalaCompileOptionsConfigurer {
    private const val FALLBACK_JVM_TARGET = 8

    /**
     * Support for these flags in different minor releases of Scala varies,
     * but we need to detect as many variants as possible to avoid overriding the target or release.
     */
    private val TARGET_DEFINING_PARAMETERS = mutableListOf<String?>( // Scala 2
        "-target", "--target",  // Scala 2 and 3
        "-release", "--release",  // Scala 3
        "-java-output-version", "--java-output-version",
        "-Xunchecked-java-output-version", "--Xunchecked-java-output-version",
        "-Xtarget", "--Xtarget"
    )

    private val PLAIN_TARGET_FORMAT_SINCE_VERSION: VersionNumber = VersionNumber.parse("2.13.1")
    private val RELEASE_REPLACES_TARGET_SINCE_VERSION: VersionNumber = VersionNumber.parse("2.13.9")

    fun configure(scalaCompileOptions: ScalaCompileOptions, toolchain: JavaInstallationMetadata?, scalaClasspath: MutableSet<File?>) {
        if (toolchain == null) {
            return
        }

        // When Scala 3 is used it appears on the classpath together with Scala 2
        var scalaJar = ScalaRuntimeHelper.findScalaJar(scalaClasspath, "library_3")
        if (scalaJar == null) {
            scalaJar = ScalaRuntimeHelper.findScalaJar(scalaClasspath, "library")
            if (scalaJar == null) {
                return
            }
        }

        val scalaVersion = VersionNumber.parse(ScalaRuntimeHelper.getScalaVersion(scalaJar))
        if (VersionNumber.UNKNOWN == scalaVersion) {
            return
        }

        if (hasTargetDefiningParameter(scalaCompileOptions.getAdditionalParameters())) {
            return
        }

        val targetParameter = determineTargetParameter(scalaVersion, toolchain as JavaToolchain)
        scalaCompileOptions.getAdditionalParameters().add(targetParameter)
    }

    private fun hasTargetDefiningParameter(additionalParameters: MutableList<String?>): Boolean {
        return additionalParameters.stream()
            .anyMatch { s: String? -> TARGET_DEFINING_PARAMETERS.stream().anyMatch { param: String? -> param == s || s!!.startsWith(param + ":") } }
    }

    /**
     * Computes parameter to specify how Scala should handle Java APIs and produced bytecode version.
     *
     *
     * The exact result depends on the Scala version in use and if the toolchain is user specified or not.
     *
     * @param scalaVersion The detected scala version
     * @param javaToolchain The toolchain used to run compilation
     * @return a Scala compiler parameter
     */
    private fun determineTargetParameter(scalaVersion: VersionNumber, javaToolchain: JavaToolchain): String {
        val explicitToolchain = !javaToolchain.isFallbackToolchain
        val effectiveTarget = if (!explicitToolchain) FALLBACK_JVM_TARGET else javaToolchain.getLanguageVersion().asInt()
        if (scalaVersion.compareTo(VersionNumber.parse("3.0.0")) >= 0) {
            if (explicitToolchain) {
                return String.format("-release:%s", effectiveTarget)
            } else {
                return String.format("-Xtarget:%s", effectiveTarget)
            }
        } else if (scalaVersion.compareTo(RELEASE_REPLACES_TARGET_SINCE_VERSION) >= 0) {
            if (explicitToolchain) {
                return String.format("-release:%s", effectiveTarget)
            } else {
                return String.format("-target:%s", effectiveTarget)
            }
        } else if (scalaVersion.compareTo(PLAIN_TARGET_FORMAT_SINCE_VERSION) >= 0) {
            return String.format("-target:%s", effectiveTarget)
        } else {
            return String.format("-target:jvm-1.%s", effectiveTarget)
        }
    }
}
