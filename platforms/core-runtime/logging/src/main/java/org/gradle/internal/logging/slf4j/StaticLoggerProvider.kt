/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.internal.logging.slf4j

import org.gradle.internal.time.Time
import org.jspecify.annotations.NullMarked
import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.helpers.NOPMDCAdapter
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider

@NullMarked
class StaticLoggerProvider : SLF4JServiceProvider {
    private val markerFactory: IMarkerFactory = BasicMarkerFactory()
    private val mdcAdapter: MDCAdapter = NOPMDCAdapter()

    private var loggerFactory: ILoggerFactory? = null // Set in initialize()

    override fun getLoggerFactory(): ILoggerFactory? {
        return loggerFactory
    }

    override fun getMarkerFactory(): IMarkerFactory {
        return markerFactory
    }

    override fun getMDCAdapter(): MDCAdapter {
        return mdcAdapter
    }

    override fun getRequestedApiVersion(): String {
        return REQUESTED_API_VERSION
    }

    override fun initialize() {
        loggerFactory = OutputEventListenerBackedLoggerContext(Time.clock())
    }

    companion object {
        /**
         * Declare the version of the SLF4J API this implementation is compiled against.
         * The value of this field is usually modified with each release.
         */
        const val REQUESTED_API_VERSION: String = "2.0.17"
    }
}
