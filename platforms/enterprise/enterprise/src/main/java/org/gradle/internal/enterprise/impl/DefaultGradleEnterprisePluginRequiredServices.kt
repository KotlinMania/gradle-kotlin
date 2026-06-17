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

import org.gradle.api.internal.tasks.userinput.UserInputHandler
import org.gradle.internal.enterprise.DevelocityPluginUnsafeConfigurationService
import org.gradle.internal.enterprise.GradleEnterprisePluginBackgroundJobExecutors
import org.gradle.internal.enterprise.GradleEnterprisePluginRequiredServices
import org.gradle.internal.logging.text.StyledTextOutputFactory

class DefaultGradleEnterprisePluginRequiredServices(
    private val userInputHandler: UserInputHandler,
    private val styledTextOutputFactory: StyledTextOutputFactory,
    private val backgroundJobExecutors: GradleEnterprisePluginBackgroundJobExecutors,
    private val unsafeConfigurationService: DevelocityPluginUnsafeConfigurationService
) : GradleEnterprisePluginRequiredServices {
    override fun getUserInputHandler(): UserInputHandler {
        return userInputHandler
    }

    override fun getStyledTextOutputFactory(): StyledTextOutputFactory {
        return styledTextOutputFactory
    }

    override fun getBackgroundJobExecutors(): GradleEnterprisePluginBackgroundJobExecutors {
        return backgroundJobExecutors
    }

    override fun getUnsafeConfigurationService(): DevelocityPluginUnsafeConfigurationService {
        return unsafeConfigurationService
    }
}
