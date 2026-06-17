/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.project.ant

import org.apache.tools.ant.BuildEvent
import org.apache.tools.ant.BuildLogger
import org.gradle.api.AntBuilder
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.logging.LogLevelMapping
import java.io.PrintStream

class AntLoggingAdapter : BuildLogger {
    private val logger = getLogger(AntLoggingAdapter::class.java)

    var lifecycleLogLevel: AntBuilder.AntMessagePriority? = null
        private set

    override fun setMessageOutputLevel(level: Int) {
        // ignore
    }

    override fun setOutputPrintStream(output: PrintStream?) {
        // ignore
    }

    override fun setEmacsMode(emacsMode: Boolean) {
        // ignore
    }

    override fun setErrorPrintStream(err: PrintStream?) {
        // ignore
    }

    override fun buildStarted(event: BuildEvent?) {
        // ignore
    }

    override fun buildFinished(event: BuildEvent?) {
        // ignore
    }

    override fun targetStarted(event: BuildEvent?) {
        // ignore
    }

    override fun targetFinished(event: BuildEvent?) {
        // ignore
    }

    override fun taskStarted(event: BuildEvent?) {
        // ignore
    }

    override fun taskFinished(event: BuildEvent?) {
        // ignore
    }

    override fun messageLogged(event: BuildEvent) {
        val message = StringBuffer()
        if (event.getTask() != null) {
            val taskName = event.getTask().getTaskName()
            message.append("[ant:").append(taskName).append("] ")
        }
        val messageText = event.getMessage()
        message.append(messageText)

        val level = getLogLevelForMessagePriority(event.getPriority())

        if (event.getException() != null) {
            logger!!.log(level, message.toString(), event.getException())
        } else {
            logger!!.log(level, message.toString())
        }
    }

    fun setLifecycleLogLevel(lifecycleLogLevel: String?) {
        setLifecycleLogLevel(if (lifecycleLogLevel == null) null else AntBuilder.AntMessagePriority.valueOf(lifecycleLogLevel))
    }

    fun setLifecycleLogLevel(lifecycleLogLevel: AntBuilder.AntMessagePriority?) {
        this.lifecycleLogLevel = lifecycleLogLevel
    }

    private fun getLogLevelForMessagePriority(messagePriority: Int): LogLevel? {
        val defaultLevel = LogLevelMapping.ANT_IVY_2_SLF4J.get(messagePriority)

        // Check to see if we should adjust the level based on a set lifecycle log level
        if (lifecycleLogLevel != null) {
            if (defaultLevel!!.ordinal < LogLevel.LIFECYCLE.ordinal
                && AntBuilder.AntMessagePriority.from(messagePriority).ordinal >= lifecycleLogLevel!!.ordinal
            ) {
                // we would normally log at a lower level than lifecycle, but the Ant message priority is actually higher
                // than (or equal to) the set lifecycle log level
                return LogLevel.LIFECYCLE
            } else if (defaultLevel.ordinal >= LogLevel.LIFECYCLE.ordinal
                && AntBuilder.AntMessagePriority.from(messagePriority).ordinal < lifecycleLogLevel!!.ordinal
            ) {
                // would normally log at a level higher than (or equal to) lifecycle, but the Ant message priority is
                // actually lower than the set lifecycle log level
                return LogLevel.INFO
            }
        }

        return defaultLevel
    }
}
