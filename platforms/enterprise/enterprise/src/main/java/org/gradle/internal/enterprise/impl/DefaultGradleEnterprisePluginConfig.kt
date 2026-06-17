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

import org.gradle.StartParameter
import org.gradle.api.internal.StartParameterInternal
import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.enterprise.GradleEnterprisePluginConfig

class DefaultGradleEnterprisePluginConfig(
    requirements: BuildActionModelRequirements,
    startParameter: StartParameter,
    autoAppliedStatus: GradleEnterprisePluginAutoAppliedStatus
) : GradleEnterprisePluginConfig {
    private val buildScanRequest: GradleEnterprisePluginConfig.BuildScanRequest
    private val taskExecutingBuild: Boolean
    private val autoApplied: Boolean
    private val develocityUrl: String?

    init {
        this.buildScanRequest = buildScanRequest(startParameter)
        this.taskExecutingBuild = requirements.isRunsTasks()
        this.autoApplied = autoAppliedStatus.isAutoApplied()
        this.develocityUrl = (startParameter as StartParameterInternal).develocityUrl
    }

    override fun getBuildScanRequest(): GradleEnterprisePluginConfig.BuildScanRequest {
        return buildScanRequest
    }

    override fun isTaskExecutingBuild(): Boolean {
        return taskExecutingBuild
    }

    override fun isAutoApplied(): Boolean {
        return autoApplied
    }

    override fun getDevelocityUrl(): String? {
        return develocityUrl
    }

    private fun buildScanRequest(startParameter: StartParameter): GradleEnterprisePluginConfig.BuildScanRequest {
        if (startParameter.isNoBuildScan) {
            return GradleEnterprisePluginConfig.BuildScanRequest.SUPPRESSED
        } else if (startParameter.isBuildScan) {
            return GradleEnterprisePluginConfig.BuildScanRequest.REQUESTED
        } else {
            return GradleEnterprisePluginConfig.BuildScanRequest.NONE
        }
    }
}
