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
package org.gradle.launcher.cli

import org.gradle.api.Action
import org.gradle.api.logging.configuration.LoggingConfiguration
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.launcher.bootstrap.ExecutionListener
import org.gradle.launcher.configuration.BuildLayoutResult

class NativeServicesInitializingAction(
    private val buildLayout: BuildLayoutResult,
    private val loggingConfiguration: LoggingConfiguration,
    private val loggingManager: LoggingManagerInternal,
    private val action: Action<ExecutionListener?>
) : Action<ExecutionListener?> {
    override fun execute(executionListener: ExecutionListener?) {
        NativeServices.initializeOnClient(buildLayout.getGradleUserHomeDir(), NativeServices.NativeServicesMode.fromSystemProperties())
        loggingManager.attachProcessConsole(loggingConfiguration.consoleOutput, loggingConfiguration.consoleUnicodeSupport)
        action.execute(executionListener)
    }
}
