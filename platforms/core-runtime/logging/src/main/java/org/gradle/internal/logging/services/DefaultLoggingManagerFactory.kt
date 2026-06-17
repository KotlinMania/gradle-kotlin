/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.logging.services

import org.gradle.internal.logging.LoggingManagerFactory
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.config.LoggingRouter
import org.gradle.internal.logging.config.LoggingSourceSystem

class DefaultLoggingManagerFactory(
    private val loggingRouter: LoggingRouter?,
    private val slfLoggingSystem: LoggingSourceSystem?,
    private val javaUtilLoggingSystem: LoggingSourceSystem?,
    private val stdOutLoggingSystem: LoggingSourceSystem?,
    private val stdErrLoggingSystem: LoggingSourceSystem?
) : LoggingManagerFactory {
    private val rootManager: DefaultLoggingManager
    private var created = false

    init {
        rootManager = newManager()
    }

    override fun getRoot(): LoggingManagerInternal {
        return rootManager
    }

    override fun createLoggingManager(): LoggingManagerInternal {
        if (!created) {
            created = true
            return getRoot()
        }
        return newManager()
    }

    private fun newManager(): DefaultLoggingManager {
        return DefaultLoggingManager(slfLoggingSystem, javaUtilLoggingSystem, stdOutLoggingSystem, stdErrLoggingSystem, loggingRouter)
    }
}
