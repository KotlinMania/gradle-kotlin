/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.enterprise.impl

import com.google.common.base.Strings
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.plugin.management.internal.DefaultPluginRequest
import org.gradle.plugin.management.internal.PluginCoordinates
import org.gradle.plugin.management.internal.PluginRequestInternal
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.plugin.management.internal.autoapply.AutoAppliedDevelocityPlugin
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginRegistry
import org.gradle.util.internal.VersionNumber

@ServiceScope(Scope.BuildTree::class)
class GradleEnterpriseAutoAppliedPluginRegistry : AutoAppliedPluginRegistry {
    override fun getAutoAppliedPlugins(target: Project): PluginRequests {
        return PluginRequests.EMPTY
    }

    override fun getAutoAppliedPlugins(target: Settings): PluginRequests {
        val startParameter = target.getStartParameter() as StartParameterInternal
        if (startParameter.isUseEmptySettings || !shouldApplyDevelocityPlugin(target)) {
            return PluginRequests.EMPTY
        } else {
            val develocityPluginVersion = startParameter.develocityPluginVersion
            val isDevelocityURLSpecified = !Strings.isNullOrEmpty(startParameter.develocityUrl)
            if (isDevelocityURLSpecified && develocityPluginVersion != null) {
                // Validate that the version is at least 4.4.0
                require(VersionNumber.parse(develocityPluginVersion).compareTo(VersionNumber.version(4, 4)) >= 0) {
                    String.format(
                        "The specified Develocity plugin version '%s' is not supported. Version 4.4.0 or higher is required when using a custom Develocity URL.",
                        develocityPluginVersion
                    )
                }
            }
            return PluginRequests.of(createDevelocityPluginRequest(develocityPluginVersion))
        }
    }

    companion object {
        private fun shouldApplyDevelocityPlugin(settings: Settings): Boolean {
            val gradle = settings.getGradle()
            val startParameter = gradle.getStartParameter() as StartParameterInternal
            return (startParameter.isBuildScan || !Strings.isNullOrEmpty(startParameter.develocityUrl)) && gradle.getParent() == null
        }

        private fun createDevelocityPluginRequest(pluginVersion: String?): PluginRequestInternal {
            val versionToApply = if (pluginVersion == null) AutoAppliedDevelocityPlugin.VERSION else pluginVersion
            val moduleIdentifier = DefaultModuleIdentifier.newId(AutoAppliedDevelocityPlugin.GROUP, AutoAppliedDevelocityPlugin.NAME)
            val selector = DefaultModuleComponentSelector.newSelector(moduleIdentifier, versionToApply)
            return DefaultPluginRequest(
                AutoAppliedDevelocityPlugin.ID,
                true,
                PluginRequestInternal.Origin.AUTO_APPLIED,
                scriptDisplayName,
                null,
                versionToApply,
                selector,
                null,
                gradleEnterprisePluginCoordinates(versionToApply)
            )
        }

        private fun gradleEnterprisePluginCoordinates(versionToApply: String): PluginCoordinates {
            val moduleIdentifier = DefaultModuleIdentifier.newId(AutoAppliedDevelocityPlugin.GROUP, AutoAppliedDevelocityPlugin.GRADLE_ENTERPRISE_PLUGIN_ARTIFACT_NAME)
            val selector = DefaultModuleComponentSelector.newSelector(moduleIdentifier, versionToApply)
            return PluginCoordinates(AutoAppliedDevelocityPlugin.GRADLE_ENTERPRISE_PLUGIN_ID, selector)
        }

        private val scriptDisplayName: String
            get() = String.format("auto-applied by using --%s", StartParameterBuildOptions.BuildScanOption.LONG_OPTION)
    }
}
