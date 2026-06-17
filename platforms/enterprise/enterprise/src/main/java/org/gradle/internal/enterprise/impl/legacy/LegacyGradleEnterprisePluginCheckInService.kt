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
package org.gradle.internal.enterprise.impl.legacy

import org.gradle.StartParameter
import org.gradle.api.internal.GradleInternal
import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.internal.scan.config.BuildScanConfig
import org.gradle.internal.scan.config.BuildScanConfigProvider
import org.gradle.internal.scan.config.BuildScanPluginMetadata
import org.gradle.internal.scan.eob.BuildScanEndOfBuildNotifier
import org.gradle.util.internal.VersionNumber
import javax.inject.Inject

/**
 * A check-in service used by the Gradle Enterprise plugin versions until 3.4, none of which are supported anymore.
 *
 *
 * We keep this service, because for the plugin versions 3.0+ we can gracefully avoid plugin application and report an unsupported message.
 *
 *
 * More modern versions of the plugin use [org.gradle.internal.enterprise.GradleEnterprisePluginCheckInService].
 */
class LegacyGradleEnterprisePluginCheckInService @Inject constructor(
    private val gradle: GradleInternal,
    private val manager: GradleEnterprisePluginManager,
    requirements: BuildActionModelRequirements
) : BuildScanConfigProvider, BuildScanEndOfBuildNotifier {
    private val taskExecutingBuild: Boolean

    init {
        this.taskExecutingBuild = requirements.isRunsTasks()
    }

    override fun collect(pluginMetadata: BuildScanPluginMetadata): BuildScanConfig {
        check(!manager.isPresent()) { "Configuration has already been collected." }

        val pluginVersion = pluginMetadata.version
        val pluginBaseVersion = VersionNumber.parse(pluginVersion).getBaseVersion()
        if (pluginBaseVersion.compareTo(FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION) < 0) {
            throw UnsupportedBuildScanPluginVersionException(GradleEnterprisePluginManager.OLD_SCAN_PLUGIN_VERSION_MESSAGE)
        }

        val unsupportedReason = DevelocityPluginCompatibility.getUnsupportedPluginMessage(pluginVersion)
        manager.unsupported()
        if (!isPluginAwareOfUnsupported(pluginBaseVersion)) {
            throw UnsupportedBuildScanPluginVersionException(unsupportedReason)
        }

        return Config(
            Requestedness.Companion.from(gradle),
            AttributesImpl(taskExecutingBuild),
            unsupportedReason
        )
    }

    override fun notify(listener: BuildScanEndOfBuildNotifier.Listener) {
        // Should not get here, since none of the plugin versions using this service are supported
    }

    private class Config(private val requestedness: Requestedness, attributes: AttributesImpl, private val unsupported: String) : BuildScanConfig {
        private val attributes: BuildScanConfig.Attributes

        init {
            this.attributes = attributes
        }

        override fun isEnabled(): Boolean {
            return requestedness.enabled
        }

        override fun isDisabled(): Boolean {
            return requestedness.disabled
        }

        override fun getUnsupportedMessage(): String {
            return unsupported
        }

        override fun getAttributes(): BuildScanConfig.Attributes {
            return attributes
        }
    }

    private enum class Requestedness(private val enabled: Boolean, private val disabled: Boolean) {
        DEFAULTED(false, false),
        ENABLED(true, false),
        DISABLED(false, true);

        companion object {
            private fun from(gradle: GradleInternal): Requestedness {
                val startParameter: StartParameter = gradle.getStartParameter()
                if (startParameter.isNoBuildScan) {
                    return Requestedness.DISABLED
                } else if (startParameter.isBuildScan) {
                    return Requestedness.ENABLED
                } else {
                    return Requestedness.DEFAULTED
                }
            }
        }
    }

    private class AttributesImpl(private val taskExecutingBuild: Boolean) : BuildScanConfig.Attributes {
        override fun isRootProjectHasVcsMappings(): Boolean {
            return false
        }

        override fun isTaskExecutingBuild(): Boolean {
            return taskExecutingBuild
        }
    }

    companion object {
        const val FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION_DISPLAY: String = "3.0"
        val FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION: VersionNumber = VersionNumber.parse(FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION_DISPLAY)

        private val FIRST_VERSION_AWARE_OF_UNSUPPORTED: VersionNumber = VersionNumber.parse("1.11")

        private fun isPluginAwareOfUnsupported(pluginVersion: VersionNumber): Boolean {
            return pluginVersion.compareTo(FIRST_VERSION_AWARE_OF_UNSUPPORTED) >= 0
        }
    }
}
