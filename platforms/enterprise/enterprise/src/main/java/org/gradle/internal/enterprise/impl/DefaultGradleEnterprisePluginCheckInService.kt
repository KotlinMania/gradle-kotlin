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
package org.gradle.internal.enterprise.impl

import com.google.common.annotations.VisibleForTesting
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.deprecation.DeprecationLogger.deprecateIndirectUsage
import org.gradle.internal.enterprise.GradleEnterprisePluginCheckInResult
import org.gradle.internal.enterprise.GradleEnterprisePluginCheckInService
import org.gradle.internal.enterprise.GradleEnterprisePluginMetadata
import org.gradle.internal.enterprise.GradleEnterprisePluginServiceFactory
import org.gradle.internal.enterprise.GradleEnterprisePluginServiceRef
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.internal.enterprise.impl.legacy.DevelocityPluginCompatibility
import org.gradle.util.internal.VersionNumber
import java.lang.Boolean
import java.util.function.Supplier
import kotlin.IllegalStateException
import kotlin.String

class DefaultGradleEnterprisePluginCheckInService(
    buildModelParameters: BuildModelParameters,
    private val manager: GradleEnterprisePluginManager,
    private val pluginAdapterFactory: DefaultGradleEnterprisePluginAdapterFactory
) : GradleEnterprisePluginCheckInService {
    private val isConfigurationCacheEnabled: Boolean
    private val isIsolatedProjectsEnabled: Boolean

    init {
        this.isConfigurationCacheEnabled = buildModelParameters.isConfigurationCache()
        this.isIsolatedProjectsEnabled = buildModelParameters.isIsolatedProjects()
    }

    override fun checkIn(pluginMetadata: GradleEnterprisePluginMetadata, serviceFactory: GradleEnterprisePluginServiceFactory): GradleEnterprisePluginCheckInResult {
        val pluginVersion = pluginMetadata.getVersion()
        val pluginBaseVersion = VersionNumber.parse(pluginVersion).getBaseVersion()

        if (Boolean.getBoolean(UNSUPPORTED_TOGGLE)) {
            return checkInUnsupportedResult(pluginBaseVersion, UNSUPPORTED_TOGGLE_MESSAGE)
        }

        if (DevelocityPluginCompatibility.isUnsupportedPluginVersion(pluginBaseVersion)) {
            return checkInUnsupportedResult(pluginBaseVersion, DevelocityPluginCompatibility.getUnsupportedPluginMessage(pluginVersion))
        }

        if (isIsolatedProjectsEnabled && DevelocityPluginCompatibility.isUnsupportedWithIsolatedProjects(pluginBaseVersion)) {
            return checkInUnsupportedResult(pluginBaseVersion, DevelocityPluginCompatibility.getUnsupportedWithIsolatedProjectsMessage(pluginVersion))
        }

        if (DevelocityPluginCompatibility.isAffectedByParentPropertyLookup(pluginBaseVersion)) {
            deprecateIndirectUsage("Usage of the Develocity plugin " + pluginVersion)
                .withAdvice("Upgrade to version " + org.gradle.internal.enterprise.impl.legacy.DevelocityPluginCompatibility.FIRST_PLUGIN_VERSION_WITHOUT_PARENT_PROPERTY_LOOKUP + " or later of the Develocity plugin.")!!
                .willBecomeAnErrorInGradle10()
                .withUpgradeGuideSection(9, "deprecated_develocity_plugin_pre_4_0")!!
                .nagUser()
        }

        val adapter = pluginAdapterFactory.create(serviceFactory)
        val ref = adapter.getPluginServiceRef()
        manager.registerAdapter(adapter)
        return checkInResult(null, Supplier { ref })
    }

    private fun checkInUnsupportedResult(pluginBaseVersion: VersionNumber, unsupportedMessage: String): GradleEnterprisePluginCheckInResult {
        // Test Acceleration can be applied even if the check-in returns an "unsupported" result.
        // We have to disable it manually, because it is not compatible with Configuration Cache
        if (isConfigurationCacheEnabled && !supportsAutoDisableTestAcceleration(pluginBaseVersion)) {
            System.setProperty(DISABLE_TEST_ACCELERATION_PROPERTY, "true")
        }

        manager.unsupported()
        return checkInResult(unsupportedMessage, Supplier {
            throw IllegalStateException()
        })
    }

    companion object {
        // Used just for testing
        @VisibleForTesting
        const val UNSUPPORTED_TOGGLE: String = "org.gradle.internal.unsupported-develocity-plugin"

        @VisibleForTesting
        const val UNSUPPORTED_TOGGLE_MESSAGE: String = "Develocity plugin unsupported due to secret toggle"

        private const val DISABLE_TEST_ACCELERATION_PROPERTY = "gradle.internal.testacceleration.disableImplicitApplication"
        private val AUTO_DISABLE_TEST_ACCELERATION_SINCE_VERSION: VersionNumber = VersionNumber.parse("3.14")

        private fun supportsAutoDisableTestAcceleration(pluginBaseVersion: VersionNumber): kotlin.Boolean {
            return AUTO_DISABLE_TEST_ACCELERATION_SINCE_VERSION.compareTo(pluginBaseVersion) <= 0
        }

        private fun checkInResult(unsupportedMessage: String?, pluginServiceRefSupplier: Supplier<GradleEnterprisePluginServiceRef>): GradleEnterprisePluginCheckInResult {
            return object : GradleEnterprisePluginCheckInResult {
                override fun getUnsupportedMessage(): String? {
                    return unsupportedMessage
                }

                override fun getPluginServiceRef(): GradleEnterprisePluginServiceRef {
                    return pluginServiceRefSupplier.get()
                }
            }
        }
    }
}
