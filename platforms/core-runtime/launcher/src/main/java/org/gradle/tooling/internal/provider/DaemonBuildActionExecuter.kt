/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.provider

import org.gradle.internal.SystemProperties
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.daemon.client.execution.ClientBuildRequestContext
import org.gradle.internal.invocation.BuildAction
import org.gradle.launcher.exec.BuildActionExecutor
import org.gradle.launcher.exec.BuildActionParameters
import org.gradle.launcher.exec.BuildActionResult
import org.gradle.launcher.exec.DefaultBuildActionParameters

class DaemonBuildActionExecuter(private val delegate: BuildActionExecutor<BuildActionParameters, ClientBuildRequestContext>) :
    BuildActionExecutor<ConnectionOperationParameters, ClientBuildRequestContext> {
    override fun execute(action: BuildAction, parameters: ConnectionOperationParameters, buildRequestContext: ClientBuildRequestContext): BuildActionResult {
        val operationParameters = parameters.getOperationParameters()
        val classPath = DefaultClassPath.of(operationParameters.injectedPluginClasspath)

        val daemonParameters = parameters.getDaemonParameters()
        val actionParameters: BuildActionParameters = DefaultBuildActionParameters(
            parameters.getTapiSystemProperties(),
            daemonParameters.getEnvironmentVariables(),
            SystemProperties.getInstance().getCurrentDir(),
            action.getStartParameter().getLogLevel(),
            daemonParameters.isEnabled(),
            classPath
        )
        return delegate.execute(action, actionParameters, buildRequestContext)
    }
}
