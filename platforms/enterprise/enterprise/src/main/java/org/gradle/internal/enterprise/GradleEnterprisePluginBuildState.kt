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
package org.gradle.internal.enterprise

import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * Information about the current build invocation or build invocation environment required by the plugin.
 */
@ServiceScope(Scope.BuildTree::class)
interface GradleEnterprisePluginBuildState {
    val buildStartedTime: Long

    val currentTime: Long

    val buildInvocationId: String?

    val workspaceId: String?

    val userId: String?

    val daemonScanInfo: DaemonScanInfo?

    val startParameter: StartParameter?
}
