/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.publish.internal.versionmapping

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer

class DefaultVariantVersionMappingStrategy(private val configurations: ConfigurationContainer) : VariantVersionMappingStrategyInternal {
    private var enabled = false
    private var userConfiguration: Configuration? = null
    private var defaultConfiguration: Configuration? = null

    override fun fromResolutionResult() {
        enabled = true
    }

    override fun fromResolutionOf(configuration: Configuration) {
        enabled = true
        userConfiguration = configuration
    }

    override fun fromResolutionOf(configurationName: String) {
        fromResolutionOf(configurations.getByName(configurationName))
    }

    fun setDefaultResolutionConfiguration(defaultConfiguration: Configuration?) {
        this.defaultConfiguration = defaultConfiguration
    }

    override fun isEnabled(): Boolean {
        return enabled
    }

    override fun getUserResolutionConfiguration(): Configuration? {
        return userConfiguration
    }

    override fun getDefaultResolutionConfiguration(): Configuration? {
        return defaultConfiguration
    }
}
