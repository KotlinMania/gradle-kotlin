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
package org.gradle.tooling.internal.consumer

import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.wrapper.GradleUserHomeLookup.gradleUserHome
import org.gradle.wrapper.PropertiesFileHandler.getJvmArgs
import org.gradle.wrapper.PropertiesFileHandler.getSystemProperties
import org.jspecify.annotations.NullMarked
import java.io.File

/**
 * Contains convenience methods to read some Gradle configuration without launching a daemon.
 */
@NullMarked
object ConnectionConfigurationUtil {
    @JvmStatic
    fun determineSystemProperties(connectionParameters: ConnectionParameters): MutableMap<String, String> {
        val systemProperties: MutableMap<String, String> = HashMap<String, String>()
        for (entry in System.getProperties().entries) {
            systemProperties.put(entry.key.toString(), (if (entry.value == null) null else entry.value.toString())!!)
        }
        getSystemProperties(File(determineRootDir(connectionParameters), "gradle.properties")).forEach { (key, value) -> if (key != null && value != null) systemProperties[key] = value }
        getSystemProperties(File(determineRealUserHomeDir(connectionParameters), "gradle.properties")).forEach { (key, value) -> if (key != null && value != null) systemProperties[key] = value }
        return systemProperties
    }

    fun determineJvmArguments(connectionParameters: ConnectionParameters): MutableList<String> {
        val jvmArgs: MutableList<String> = ArrayList<String>()
        jvmArgs.addAll(getJvmArgs(File(determineRootDir(connectionParameters), "gradle.properties")).filterNotNull())
        jvmArgs.addAll(getJvmArgs(File(determineRealUserHomeDir(connectionParameters), "gradle.properties")).filterNotNull())
        return jvmArgs
    }

    @JvmStatic
    fun determineRootDir(connectionParameters: ConnectionParameters): File {
        return BuildLayoutFactory().getLayoutFor(
            connectionParameters.projectDir!!,
            connectionParameters.isSearchUpwards ?: true
        ).rootDirectory
    }

    @JvmStatic
    fun determineRealUserHomeDir(connectionParameters: ConnectionParameters): File {
        val distributionBaseDir = connectionParameters.distributionBaseDir
        if (distributionBaseDir != null) {
            return distributionBaseDir
        }
        val userHomeDir = connectionParameters.gradleUserHomeDir
        return if (userHomeDir != null) userHomeDir else gradleUserHome()
    }
}
