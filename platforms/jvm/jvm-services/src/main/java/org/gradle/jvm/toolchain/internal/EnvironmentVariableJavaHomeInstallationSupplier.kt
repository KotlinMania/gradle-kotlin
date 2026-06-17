/*
 * Copyright 2025 the original author or authors.
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

import java.io.File

class EnvironmentVariableJavaHomeInstallationSupplier(private val buildOptions: ToolchainConfiguration) : InstallationSupplier {
    override fun getSourceName(): String {
        return "environment variable 'JAVA_HOME'"
    }

    override fun get(): MutableSet<InstallationLocation?> {
        val javaHomePath = buildOptions.getEnvironmentVariableValue("JAVA_HOME")
        if (javaHomePath == null || javaHomePath.isEmpty()) {
            return mutableSetOf<InstallationLocation?>()
        }
        val javaHome = File(javaHomePath)
        val installationLocation: InstallationLocation = InstallationLocation.Companion.userDefined(javaHome, getSourceName())
        return mutableSetOf<InstallationLocation?>(installationLocation)
    }
}
