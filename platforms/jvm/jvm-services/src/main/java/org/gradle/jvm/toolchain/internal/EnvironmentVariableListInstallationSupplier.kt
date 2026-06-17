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
package org.gradle.jvm.toolchain.internal

import org.gradle.api.internal.file.FileResolver
import java.util.Optional
import java.util.stream.Collectors
import javax.inject.Inject

class EnvironmentVariableListInstallationSupplier @Inject constructor(private val buildOptions: ToolchainConfiguration, private val fileResolver: FileResolver) : InstallationSupplier {
    override fun getSourceName(): String {
        return "environment variables from gradle property '" + JAVA_INSTALLATIONS_FROM_ENV_PROPERTY + "'"
    }

    override fun get(): MutableSet<InstallationLocation?> {
        val possibleInstallations = buildOptions.getJavaInstallationsFromEnvironment()
        return possibleInstallations.stream().map<Optional<InstallationLocation?>?> { environmentVariable: String? -> this.resolveEnvironmentVariable(environmentVariable!!) }
            .filter { obj: Optional<InstallationLocation?>? -> obj!!.isPresent() }
            .map<InstallationLocation?> { obj: Optional<InstallationLocation?>? -> obj!!.get() }
            .collect(Collectors.toSet())
    }

    private fun resolveEnvironmentVariable(environmentVariable: String): Optional<InstallationLocation?> {
        val value = environmentVariableValue(environmentVariable)
        if (value != null) {
            val path = value.trim { it <= ' ' }
            if (!path.isEmpty()) {
                return Optional.of<InstallationLocation?>(InstallationLocation.Companion.userDefined(fileResolver.resolve(path), "environment variable '" + environmentVariable + "'"))
            }
        }
        return Optional.empty<InstallationLocation?>()
    }

    private fun environmentVariableValue(environmentVariable: String): String? {
        return buildOptions.getEnvironmentVariableValue(environmentVariable.trim { it <= ' ' })
    }


    companion object {
        const val JAVA_INSTALLATIONS_FROM_ENV_PROPERTY: String = "org.gradle.java.installations.fromEnv"
    }
}
