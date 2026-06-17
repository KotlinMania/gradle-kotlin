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
import org.gradle.internal.buildevents.BuildStartedTime
import org.gradle.internal.enterprise.GradleEnterprisePluginBuildState
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.internal.scopeids.id.UserScopeId
import org.gradle.internal.scopeids.id.WorkspaceScopeId
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.time.Clock
import org.gradle.launcher.daemon.server.scaninfo.DaemonScanInfo

class DefaultGradleEnterprisePluginBuildState(
    private val clock: Clock,
    private val buildStartedTime: BuildStartedTime,
    private val buildInvocationId: BuildInvocationScopeId,
    private val workspaceId: WorkspaceScopeId,
    private val userId: UserScopeId,
    private val startParameter: StartParameter,
    private val serviceRegistry: ServiceRegistry
) : GradleEnterprisePluginBuildState {
    override fun getBuildStartedTime(): Long {
        return buildStartedTime.getStartTime()
    }

    override fun getCurrentTime(): Long {
        return clock.currentTime
    }

    override fun getBuildInvocationId(): String {
        return buildInvocationId.getId().asString()
    }

    override fun getWorkspaceId(): String {
        return workspaceId.getId().asString()
    }

    override fun getUserId(): String {
        return userId.getId().asString()
    }

    override fun getDaemonScanInfo(): DaemonScanInfo {
        return serviceRegistry.find(DaemonScanInfo::class.java) as DaemonScanInfo
    }

    override fun getStartParameter(): StartParameter {
        return startParameter
    }
}
