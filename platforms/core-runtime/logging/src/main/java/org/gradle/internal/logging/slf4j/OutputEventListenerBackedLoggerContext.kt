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
package org.gradle.internal.logging.slf4j

import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider
import org.gradle.api.logging.LogLevel
import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.internal.logging.console.DefaultUserInputReceiver
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.sink.OutputEventRenderer
import org.gradle.internal.time.Clock
import org.gradle.wrapper.GradleUserHomeLookup
import org.slf4j.ILoggerFactory
import org.slf4j.Logger
import org.slf4j.Marker
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference

class OutputEventListenerBackedLoggerContext(private val clock: Clock) : ILoggerFactory {
    private val loggers: ConcurrentMap<String?, Logger?> = ConcurrentHashMap<String?, Logger?>()
    private val level = AtomicReference<LogLevel?>()
    private val outputEventListener = AtomicReference<OutputEventListener?>()

    init {
        applyDefaultLoggersConfig()
        reset()
    }

    private fun applyDefaultLoggersConfig() {
        addNoOpLogger("java.lang.ProcessBuilder")
        addNoOpLogger(HTTP_CLIENT_WIRE_LOGGER_NAME)
        addNoOpLogger("org.apache.http.headers")
        addNoOpLogger(META_INF_EXTENSION_MODULE_LOGGER_NAME)
        addNoOpLogger("org.littleshoot.proxy.HttpRequestHandler")
        addNoOpLogger("org.littleshoot.proxy.impl.ClientToProxyConnection")
        // We ignore logging from here because this is when the Groovy runtime is initialized.
        // This may happen in BuildOperationTrace, and then the logging from the plugin factory would go into the build operation trace again.
        // That then will fail because we can't use JsonOutput in BuildOperationTrace when the Groovy VM hasn't been initialized.
        addNoOpLogger(GROOVY_VM_PLUGIN_FACTORY)
    }

    private fun addNoOpLogger(name: String?) {
        loggers.put(name, NoOpLogger(name))
    }

    fun setOutputEventListener(outputEventListener: OutputEventListener?) {
        this.outputEventListener.set(outputEventListener)
    }

    fun getOutputEventListener(): OutputEventListener? {
        return outputEventListener.get()
    }

    override fun getLogger(name: String): Logger? {
        var logger = loggers.get(name)
        if (logger != null) {
            return logger
        }

        logger = loggers.putIfAbsent(name, OutputEventListenerBackedLogger(name, this, clock))
        return if (logger != null) logger else loggers.get(name)
    }

    fun reset() {
        setLevel(DEFAULT_LOG_LEVEL)
        val userInputReceiver = DefaultUserInputReceiver()
        val renderer = OutputEventRenderer(clock, userInputReceiver, GradleUserHomeTemporaryFileProvider(GradleUserHomeDirProvider { GradleUserHomeLookup.gradleUserHome() }))
        userInputReceiver.attachConsole(renderer)
        renderer.attachSystemOutAndErr()
        setOutputEventListener(renderer)
    }

    fun getLevel(): LogLevel? {
        return level.get()
    }

    fun setLevel(level: LogLevel) {
        requireNotNull(level) { "Global log level cannot be set to null" }
        this.level.set(level)
    }

    private class NoOpLogger(private val name: String?) : org.gradle.api.logging.Logger {
        override fun getName(): String? {
            return name
        }

        override fun isTraceEnabled(): Boolean {
            return false
        }

        override fun trace(msg: String?) {
        }

        override fun trace(format: String?, arg: Any?) {
        }

        override fun trace(format: String?, arg1: Any?, arg2: Any?) {
        }

        override fun trace(format: String?, vararg arguments: Any?) {
        }

        override fun trace(msg: String?, t: Throwable?) {
        }

        override fun isTraceEnabled(marker: Marker?): Boolean {
            return false
        }

        override fun trace(marker: Marker?, msg: String?) {
        }

        override fun trace(marker: Marker?, format: String?, arg: Any?) {
        }

        override fun trace(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        }

        override fun trace(marker: Marker?, format: String?, vararg argArray: Any?) {
        }

        override fun trace(marker: Marker?, msg: String?, t: Throwable?) {
        }

        override fun isDebugEnabled(): Boolean {
            return false
        }

        override fun debug(msg: String?) {
        }

        override fun debug(format: String?, arg: Any?) {
        }

        override fun debug(format: String?, arg1: Any?, arg2: Any?) {
        }

        override fun isLifecycleEnabled(): Boolean {
            return false
        }

        override fun debug(format: String?, vararg arguments: Any?) {
        }

        override fun lifecycle(message: String?) {
        }

        override fun lifecycle(message: String?, vararg objects: Any?) {
        }

        override fun lifecycle(message: String?, throwable: Throwable?) {
        }

        override fun debug(msg: String?, t: Throwable?) {
        }

        override fun isDebugEnabled(marker: Marker?): Boolean {
            return false
        }

        override fun debug(marker: Marker?, msg: String?) {
        }

        override fun debug(marker: Marker?, format: String?, arg: Any?) {
        }

        override fun debug(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        }

        override fun debug(marker: Marker?, format: String?, vararg arguments: Any?) {
        }

        override fun debug(marker: Marker?, msg: String?, t: Throwable?) {
        }

        override fun isInfoEnabled(): Boolean {
            return false
        }

        override fun info(msg: String?) {
        }

        override fun info(format: String?, arg: Any?) {
        }

        override fun info(format: String?, arg1: Any?, arg2: Any?) {
        }

        override fun info(format: String?, vararg arguments: Any?) {
        }

        override fun isQuietEnabled(): Boolean {
            return false
        }

        override fun quiet(message: String?) {
        }

        override fun quiet(message: String?, vararg objects: Any?) {
        }

        override fun quiet(message: String?, throwable: Throwable?) {
        }

        override fun isEnabled(level: LogLevel?): Boolean {
            return false
        }

        override fun log(level: LogLevel?, message: String?) {
        }

        override fun log(level: LogLevel?, message: String?, vararg objects: Any?) {
        }

        override fun log(level: LogLevel?, message: String?, throwable: Throwable?) {
        }

        override fun info(msg: String?, t: Throwable?) {
        }

        override fun isInfoEnabled(marker: Marker?): Boolean {
            return false
        }

        override fun info(marker: Marker?, msg: String?) {
        }

        override fun info(marker: Marker?, format: String?, arg: Any?) {
        }

        override fun info(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        }

        override fun info(marker: Marker?, format: String?, vararg arguments: Any?) {
        }

        override fun info(marker: Marker?, msg: String?, t: Throwable?) {
        }

        override fun isWarnEnabled(): Boolean {
            return false
        }

        override fun warn(msg: String?) {
        }

        override fun warn(format: String?, arg: Any?) {
        }

        override fun warn(format: String?, vararg arguments: Any?) {
        }

        override fun warn(format: String?, arg1: Any?, arg2: Any?) {
        }

        override fun warn(msg: String?, t: Throwable?) {
        }

        override fun isWarnEnabled(marker: Marker?): Boolean {
            return false
        }

        override fun warn(marker: Marker?, msg: String?) {
        }

        override fun warn(marker: Marker?, format: String?, arg: Any?) {
        }

        override fun warn(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        }

        override fun warn(marker: Marker?, format: String?, vararg arguments: Any?) {
        }

        override fun warn(marker: Marker?, msg: String?, t: Throwable?) {
        }

        override fun isErrorEnabled(): Boolean {
            return false
        }

        override fun error(msg: String?) {
        }

        override fun error(format: String?, arg: Any?) {
        }

        override fun error(format: String?, arg1: Any?, arg2: Any?) {
        }

        override fun error(format: String?, vararg arguments: Any?) {
        }

        override fun error(msg: String?, t: Throwable?) {
        }

        override fun isErrorEnabled(marker: Marker?): Boolean {
            return false
        }

        override fun error(marker: Marker?, msg: String?) {
        }

        override fun error(marker: Marker?, format: String?, arg: Any?) {
        }

        override fun error(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        }

        override fun error(marker: Marker?, format: String?, vararg arguments: Any?) {
        }

        override fun error(marker: Marker?, msg: String?, t: Throwable?) {
        }
    }

    companion object {
        private val DEFAULT_LOG_LEVEL = LogLevel.LIFECYCLE

        const val HTTP_CLIENT_WIRE_LOGGER_NAME: String = "org.apache.http.wire"
        const val META_INF_EXTENSION_MODULE_LOGGER_NAME: String = "org.codehaus.groovy.runtime.m12n.MetaInfExtensionModule"
        private const val GROOVY_VM_PLUGIN_FACTORY = "org.codehaus.groovy.vmplugin.VMPluginFactory"
    }
}
