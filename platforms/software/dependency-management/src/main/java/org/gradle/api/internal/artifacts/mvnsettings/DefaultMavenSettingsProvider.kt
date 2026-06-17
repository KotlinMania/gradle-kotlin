/*
 * Copyright 2012 the original author or authors.
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

import org.apache.commons.lang3.StringUtils
import org.apache.maven.settings.Settings
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest
import org.apache.maven.settings.building.SettingsBuildingException
import org.apache.maven.settings.io.DefaultSettingsReader
import org.apache.maven.settings.io.SettingsReader
import java.io.File
import java.lang.Boolean
import java.util.Collections
import kotlin.Exception
import kotlin.String
import kotlin.Throws

class DefaultMavenSettingsProvider(private val mavenFileLocations: MavenFileLocations) : MavenSettingsProvider {
    /**
     * Builds a complete `Settings` instance for this machine.
     *
     * Note that this can be an expensive operation, spawning an external process
     * and doing a bunch of additional processing.
     */
    @Throws(SettingsBuildingException::class)
    override fun buildSettings(): Settings? {
        val factory = DefaultSettingsBuilderFactory()
        val defaultSettingsBuilder = factory.newInstance()
        val settingsBuildingRequest = DefaultSettingsBuildingRequest()
        settingsBuildingRequest.setSystemProperties(System.getProperties())
        settingsBuildingRequest.setUserSettingsFile(mavenFileLocations.getUserSettingsFile())
        settingsBuildingRequest.setGlobalSettingsFile(mavenFileLocations.getGlobalSettingsFile())
        val settingsBuildingResult = defaultSettingsBuilder.build(settingsBuildingRequest)
        return settingsBuildingResult.getEffectiveSettings()
    }

    /**
     * Read the local repository location from local Maven settings files.
     *
     * @return The path to the local repository, or `null` if not specified in Maven settings.
     */
    override fun getLocalRepository(): String? {
        var localRepo = readLocalRepository(mavenFileLocations.getUserSettingsFile())
        if (localRepo == null) {
            localRepo = readLocalRepository(mavenFileLocations.getGlobalSettingsFile())
        }
        return localRepo
    }

    private fun readLocalRepository(settingsFile: File?): String? {
        if (settingsFile == null || !settingsFile.exists()) {
            return null
        }
        val options: MutableMap<String?, *> = Collections.singletonMap<String?, Boolean?>(SettingsReader.IS_STRICT, Boolean.FALSE)
        val settingsReader: SettingsReader = DefaultSettingsReader()
        try {
            val localRepository = settingsReader.read(settingsFile, options).getLocalRepository()
            return if (StringUtils.isEmpty(localRepository)) null else localRepository
        } catch (parseException: Exception) {
            throw CannotLocateLocalMavenRepositoryException("Unable to parse local Maven settings: " + settingsFile.getAbsolutePath(), parseException)
        }
    }
}
