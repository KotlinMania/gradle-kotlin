/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.jvm.toolchain.JavaCompiler
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavadocTool
import org.gradle.jvm.toolchain.internal.operations.JavaToolchainUsageProgressDetails

class DefaultJavaToolchainUsageProgressDetails(private val toolName: JavaTool, private val toolchainMetadata: JvmInstallationMetadata) : JavaToolchainUsageProgressDetails {
    override fun getToolName(): String {
        return toolName.toolName
    }

    val toolchain: JavaToolchainUsageProgressDetails.JavaToolchain
        get() = if (toolchainMetadata.isValidInstallation) ToolchainFromMetadata(toolchainMetadata) else UNKNOWN_TOOLCHAIN

    private class ToolchainFromMetadata(private val metadata: JvmInstallationMetadata) : JavaToolchainUsageProgressDetails.JavaToolchain {
        val javaVersion: String
            get() = metadata.javaVersion!!

        val javaVendor: String
            get() = metadata.vendor!!.displayName!!

        val runtimeName: String
            get() = metadata.runtimeName!!

        val runtimeVersion: String
            get() = metadata.runtimeVersion!!

        val jvmName: String
            get() = metadata.jvmName!!

        val jvmVersion: String
            get() = metadata.jvmVersion!!

        val jvmVendor: String
            get() = metadata.jvmVendor!!

        val architecture: String
            get() = metadata.architecture!!
    }

    enum class JavaTool(val toolName: String) {
        COMPILER(JavaCompiler::class.java.getSimpleName()),
        LAUNCHER(JavaLauncher::class.java.getSimpleName()),
        JAVADOC(JavadocTool::class.java.getSimpleName())
    }

    companion object {
        private val UNKNOWN_TOOLCHAIN: JavaToolchainUsageProgressDetails.JavaToolchain = object : JavaToolchainUsageProgressDetails.JavaToolchain {
            val javaVersion: String
                get() = "unknown"

            val javaVendor: String
                get() = "unknown"

            val runtimeName: String
                get() = "unknown"

            val runtimeVersion: String
                get() = "unknown"

            val jvmName: String
                get() = "unknown"

            val jvmVersion: String
                get() = "unknown"

            val jvmVendor: String
                get() = "unknown"

            val architecture: String
                get() = "unknown"
        }
    }
}
