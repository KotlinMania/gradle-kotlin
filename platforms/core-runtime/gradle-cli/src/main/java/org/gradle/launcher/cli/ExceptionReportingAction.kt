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
package org.gradle.launcher.cli

import org.gradle.api.Action
import org.gradle.initialization.ReportedException
import org.gradle.initialization.exception.InitializationException
import org.gradle.internal.exceptions.ContextAwareException
import org.gradle.internal.logging.LoggingOutputInternal
import org.gradle.internal.service.ServiceCreationException
import org.gradle.launcher.bootstrap.ExecutionListener

class ExceptionReportingAction(private val reporter: Action<Throwable?>, private val loggingOutput: LoggingOutputInternal, private val action: Action<ExecutionListener?>) :
    Action<ExecutionListener?> {
    override fun execute(executionListener: ExecutionListener) {
        try {
            try {
                action.execute(executionListener)
            } finally {
                loggingOutput.flush()
            }
        } catch (e: ReportedException) {
            // Exception has already been reported
            executionListener.onFailure(e)
        } catch (e: ServiceCreationException) {
            reporter.execute(ContextAwareException(InitializationException(e)))
            executionListener.onFailure(e)
        } catch (t: Throwable) {
            reporter.execute(t)
            executionListener.onFailure(t)
        }
    }
}
