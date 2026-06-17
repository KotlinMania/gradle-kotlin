/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.util.internal.VersionNumber
import java.io.File
import java.nio.file.Path
import java.util.function.Function

class JvmInstallationMetadataComparator(private val currentJavaHome: File?) : Comparator<JvmInstallationMetadata?> {
    override fun compare(o1: JvmInstallationMetadata?, o2: JvmInstallationMetadata?): Int {
        return Comparator
            .comparing<JvmInstallationMetadata?, Boolean?>(Function { metadata: JvmInstallationMetadata? -> this.isCurrentJvm(metadata!!) }) // Prefer installations with compiler, javadoc and jar
            .thenComparing<Boolean?>(Function { metadata: JvmInstallationMetadata? -> this.hasCompiler(metadata!!) })
            .thenComparing<Boolean?>(Function { metadata: JvmInstallationMetadata? -> this.hasJavadoc(metadata!!) })
            .thenComparing<Boolean?>(Function { metadata: JvmInstallationMetadata? -> this.hasJar(metadata!!) })
            .thenComparing<JvmVendor.KnownJvmVendor?>(Function { metadata: JvmInstallationMetadata? -> this.extractVendor(metadata!!) }, Comparator.reverseOrder<JvmVendor.KnownJvmVendor?>())
            .thenComparing<VersionNumber?>(Function { metadata: JvmInstallationMetadata? -> this.getToolchainVersion(metadata!!) }) // It is possible for different JDK builds to have exact same version. The input order
            // may change so the installation path breaks ties to keep sorted output consistent
            // between runs.
            .thenComparing<Path?>(Function { obj: JvmInstallationMetadata? -> obj!!.getJavaHome() })
            .reversed()
            .compare(o1, o2)
    }

    fun isCurrentJvm(metadata: JvmInstallationMetadata): Boolean {
        return metadata.getJavaHome().toFile() == currentJavaHome
    }

    private fun hasCompiler(metadata: JvmInstallationMetadata): Boolean {
        return metadata.getCapabilities().contains(JavaInstallationCapability.JAVA_COMPILER)
    }

    private fun hasJavadoc(metadata: JvmInstallationMetadata): Boolean {
        return metadata.getCapabilities().contains(JavaInstallationCapability.JAVADOC_TOOL)
    }

    private fun hasJar(metadata: JvmInstallationMetadata): Boolean {
        return metadata.getCapabilities().contains(JavaInstallationCapability.JAR_TOOL)
    }

    private fun extractVendor(metadata: JvmInstallationMetadata): JvmVendor.KnownJvmVendor? {
        return metadata.getVendor().getKnownVendor()
    }

    private fun getToolchainVersion(metadata: JvmInstallationMetadata): VersionNumber? {
        return VersionNumber.withPatchNumber().parse(metadata.getJavaVersion())
    }
}
