/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice

import org.apache.ivy.util.AbstractMessageLogger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.logging.LogLevelMapping

/**
 * This class is for integrating Ivy log statements into our logging system. We don't want to have a dependency on
 * logback. This would be bad for embedded usage. We only want one on slf4j. But slf4j has no constants for log levels.
 * As we want to avoid the execution of if statements for each Ivy request, we use Map which delegates Ivy log
 * statements to Sl4j action classes.
 */
class IvyLoggingAdaper : AbstractMessageLogger() {
    private val logger = getLogger(IvyLoggingAdaper::class.java)

    override fun log(msg: String?, level: Int) {
        logger!!.log(LogLevelMapping.ANT_IVY_2_SLF4J.get(level), msg)
    }

    override fun rawlog(msg: String?, level: Int) {
        log(msg, level)
    }

    /**
     * Overrides the default implementation, which doesn't delegate to [.log].
     */
    override fun warn(msg: String?) {
        logger!!.warn(msg)
    }

    /**
     * Overrides the default implementation, which doesn't delegate to [.log].
     */
    override fun error(msg: String?) {
        logger!!.error(msg)
    }

    public override fun doProgress() {
    }

    public override fun doEndProgress(msg: String?) {
        logger!!.info(msg)
    }
}
