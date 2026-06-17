/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.tasks.scala

import org.gradle.api.logging.Logging.getLogger
import java.util.function.Supplier

internal class SbtLoggerAdapter : xsbti.Logger {
    override fun error(msg: Supplier<String?>) {
        LOGGER!!.error(msg.get())
    }

    override fun warn(msg: Supplier<String?>) {
        LOGGER!!.warn(msg.get())
    }

    override fun info(msg: Supplier<String?>) {
        LOGGER!!.info(msg.get())
    }

    override fun debug(msg: Supplier<String?>) {
        LOGGER!!.debug(msg.get())
    }

    override fun trace(exception: Supplier<Throwable?>) {
        LOGGER!!.trace(exception.get().toString())
    }

    companion object {
        private val LOGGER = getLogger(ZincScalaCompilerFactory::class.java)
    }
}
