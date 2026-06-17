/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.artifacts.mvnsettings

import org.apache.maven.settings.building.SettingsBuildingException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern

class DefaultLocalMavenRepositoryLocator protected constructor(
    private val settingsProvider: MavenSettingsProvider,
    private val mavenFileLocations: MavenFileLocations,
    private val system: SystemPropertyAccess
) : LocalMavenRepositoryLocator {
    private var localRepoPathFromMavenSettings: String? = null

    constructor(settingsProvider: MavenSettingsProvider) : this(settingsProvider, DefaultMavenFileLocations(), CurrentSystemPropertyAccess())

    @Throws(CannotLocateLocalMavenRepositoryException::class)
    override fun getLocalMavenRepository(): File {
        val localOverride = system.getProperty("maven.repo.local")
        if (localOverride != null) {
            return File(localOverride)
        }
        try {
            val repoPath = parseLocalRepoPathFromMavenSettings()
            if (repoPath != null) {
                val file = File(resolvePlaceholders(repoPath.trim { it <= ' ' }))
                if (isDriveRelativeWindowsPath(file)) {
                    return file.getAbsoluteFile()
                } else {
                    return file
                }
            } else {
                val defaultLocation = File(mavenFileLocations.getUserMavenDir(), "repository").getAbsoluteFile()
                LOGGER.debug("No local repository in Settings file defined. Using default path: {}", defaultLocation)
                return defaultLocation
            }
        } catch (e: SettingsBuildingException) {
            throw CannotLocateLocalMavenRepositoryException("Unable to parse local Maven settings.", e)
        }
    }

    private fun isDriveRelativeWindowsPath(file: File): Boolean {
        return !file.isAbsolute() && file.getPath().startsWith(File.separator)
    }

    // We only cache the result of parsing the Maven settings files, but allow this value to be updated in-flight
    // via system properties. This allows the local maven repo to be overridden when publishing to maven
    // (see http://forums.gradle.org/gradle/topics/override_location_of_the_local_maven_repo).
    @Synchronized
    @Throws(SettingsBuildingException::class)
    private fun parseLocalRepoPathFromMavenSettings(): String? {
        if (localRepoPathFromMavenSettings == null) {
            localRepoPathFromMavenSettings = settingsProvider.getLocalRepository()
        }
        return localRepoPathFromMavenSettings
    }

    private fun resolvePlaceholders(value: String): String {
        val result = StringBuffer()
        val matcher: Matcher = PLACEHOLDER_PATTERN.matcher(value)
        while (matcher.find()) {
            val placeholder = matcher.group(1)
            val replacement = (if (placeholder.startsWith("env.")) system.getEnv(placeholder.substring(4)) else system.getProperty(placeholder))!!
            if (replacement == null) {
                throw CannotLocateLocalMavenRepositoryException(String.format("Cannot resolve placeholder '%s' in value '%s'", placeholder, value))
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(result)
        return result.toString()
    }

    interface SystemPropertyAccess {
        fun getProperty(name: String?): String?
        fun getEnv(name: String?): String
    }

    class CurrentSystemPropertyAccess : SystemPropertyAccess {
        override fun getProperty(name: String): String? {
            return System.getProperty(name)
        }

        override fun getEnv(name: String?): String? {
            return System.getenv(name)
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultLocalMavenRepositoryLocator::class.java)
        private val PLACEHOLDER_PATTERN: Pattern = Pattern.compile("\\$\\{([^}]*)}")
    }
}
