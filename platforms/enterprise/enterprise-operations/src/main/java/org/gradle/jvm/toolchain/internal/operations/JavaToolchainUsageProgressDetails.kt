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
package org.gradle.jvm.toolchain.internal.operations


/**
 * Details about the Java tool being used and the toolchain it belongs to.
 *
 * @since 7.6
 */
interface JavaToolchainUsageProgressDetails {
    /**
     * Name of the tool from Java distribution such as `javac`, `java` or `javadoc`.
     */
    val toolName: String?

    /**
     * Toolchain to which the tool belongs.
     */
    val toolchain: JavaToolchain?

    @Suppress("unused")
    interface JavaToolchain {
        /**
         * Returns Java language version such as `11.0.15`.
         */
        val javaVersion: String?

        /**
         * Returns the display name of the toolchain vendor such as `Eclipse Temurin`.
         *
         *
         * The value could be normalized for uniformity, and does not necessarily correspond to a system property value.
         */
        val javaVendor: String?

        /**
         * Returns Java runtime name such as `OpenJDK Runtime Environment`.
         */
        val runtimeName: String?

        /**
         * Returns Java runtime version such as `17.0.3.1+2-LTS`.
         */
        val runtimeVersion: String?

        /**
         * Returns Java VM name such as `OpenJDK 64-Bit Server VM`.
         */
        val jvmName: String?

        /**
         * Returns Java VM version such as `17.0.3.1+2-LTS`.
         *
         *
         * This value is identical to [.getRuntimeVersion] for most of the vendors,
         * but could still differ for example by not including the language version.
         */
        val jvmVersion: String?

        /**
         * Returns Java VM vendor such as `Eclipse Adoptium`.
         */
        val jvmVendor: String?

        /**
         * Returns OS architecture such as `amd64`.
         */
        val architecture: String?
    }
}
