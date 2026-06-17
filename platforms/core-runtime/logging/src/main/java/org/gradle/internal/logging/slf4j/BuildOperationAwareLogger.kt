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
package org.gradle.internal.logging.slf4j

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.operations.OperationIdentifier
import org.slf4j.Marker
import org.slf4j.helpers.MessageFormatter

internal abstract class BuildOperationAwareLogger : Logger {
    abstract override fun getName(): String?

    abstract fun isLevelAtMost(level: LogLevel?): Boolean

    abstract fun log(logLevel: LogLevel?, throwable: Throwable?, message: String?, operationIdentifier: OperationIdentifier?)

    override fun isTraceEnabled(): Boolean {
        return false
    }

    override fun isTraceEnabled(marker: Marker?): Boolean {
        return isTraceEnabled()
    }

    override fun isDebugEnabled(): Boolean {
        return isLevelAtMost(LogLevel.DEBUG)
    }

    override fun isDebugEnabled(marker: Marker?): Boolean {
        return isDebugEnabled()
    }

    override fun isInfoEnabled(): Boolean {
        return isLevelAtMost(LogLevel.INFO)
    }

    override fun isInfoEnabled(marker: Marker?): Boolean {
        return isLevelAtMost(toLogLevel(marker))
    }

    override fun isWarnEnabled(): Boolean {
        return isLevelAtMost(LogLevel.WARN)
    }

    override fun isWarnEnabled(marker: Marker?): Boolean {
        return isWarnEnabled()
    }

    override fun isErrorEnabled(): Boolean {
        return isLevelAtMost(LogLevel.ERROR)
    }

    override fun isErrorEnabled(marker: Marker?): Boolean {
        return isErrorEnabled()
    }

    override fun isLifecycleEnabled(): Boolean {
        return isLevelAtMost(LogLevel.LIFECYCLE)
    }

    override fun isQuietEnabled(): Boolean {
        return isLevelAtMost(LogLevel.QUIET)
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

    private fun log(logLevel: LogLevel?, throwable: Throwable?, message: String?) {
        log(logLevel, throwable, message, CurrentBuildOperationRef.instance().getId())
    }

    private fun log(logLevel: LogLevel?, throwable: Throwable?, format: String?, arg: Any?) {
        log(logLevel, throwable, format, arrayOf<Any?>(arg))
    }

    private fun log(logLevel: LogLevel?, throwable: Throwable?, format: String?, arg1: Any?, arg2: Any?) {
        log(logLevel, throwable, format, arrayOf<Any?>(arg1, arg2))
    }

    private fun log(logLevel: LogLevel?, throwable: Throwable?, format: String?, args: Array<Any?>?) {
        val tuple = MessageFormatter.arrayFormat(format, args)
        val loggedThrowable = if (throwable == null) tuple.getThrowable() else throwable
        log(logLevel, loggedThrowable, tuple.getMessage())
    }

    override fun debug(message: String?) {
        if (isDebugEnabled()) {
            log(LogLevel.DEBUG, null, message)
        }
    }

    override fun debug(format: String?, arg: Any?) {
        if (isDebugEnabled()) {
            log(LogLevel.DEBUG, null, format, arg)
        }
    }

    override fun debug(format: String?, arg1: Any?, arg2: Any?) {
        if (isDebugEnabled()) {
            log(LogLevel.DEBUG, null, format, arg1, arg2)
        }
    }

    override fun debug(format: String?, vararg arguments: Any?) {
        if (isDebugEnabled()) {
            log(LogLevel.DEBUG, null, format, arguments)
        }
    }

    override fun debug(msg: String?, t: Throwable?) {
        if (isDebugEnabled()) {
            log(LogLevel.DEBUG, t, msg)
        }
    }

    override fun debug(marker: Marker?, msg: String?) {
        if (isDebugEnabled(marker)) {
            log(LogLevel.DEBUG, null, msg)
        }
    }

    override fun debug(marker: Marker?, format: String?, arg: Any?) {
        if (isDebugEnabled(marker)) {
            log(LogLevel.DEBUG, null, format, arg)
        }
    }

    override fun debug(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        if (isDebugEnabled(marker)) {
            log(LogLevel.DEBUG, null, format, arg1, arg2)
        }
    }

    override fun debug(marker: Marker?, format: String?, vararg argArray: Any?) {
        if (isDebugEnabled(marker)) {
            log(LogLevel.DEBUG, null, format, argArray)
        }
    }

    override fun debug(marker: Marker?, msg: String?, t: Throwable?) {
        if (isDebugEnabled(marker)) {
            log(LogLevel.DEBUG, t, msg)
        }
    }

    override fun info(message: String?) {
        if (isInfoEnabled()) {
            log(LogLevel.INFO, null, message)
        }
    }

    override fun info(format: String?, arg: Any?) {
        if (isInfoEnabled()) {
            log(LogLevel.INFO, null, format, arg)
        }
    }

    override fun info(format: String?, arg1: Any?, arg2: Any?) {
        if (isInfoEnabled()) {
            log(LogLevel.INFO, null, format, arg1, arg2)
        }
    }

    override fun info(format: String?, vararg arguments: Any?) {
        if (isInfoEnabled()) {
            log(LogLevel.INFO, null, format, arguments)
        }
    }

    override fun lifecycle(message: String?) {
        if (isLifecycleEnabled()) {
            log(LogLevel.LIFECYCLE, null, message)
        }
    }

    override fun lifecycle(message: String?, vararg objects: Any?) {
        if (isLifecycleEnabled()) {
            log(LogLevel.LIFECYCLE, null, message, objects)
        }
    }

    override fun lifecycle(message: String?, throwable: Throwable?) {
        if (isLifecycleEnabled()) {
            log(LogLevel.LIFECYCLE, throwable, message)
        }
    }

    override fun quiet(message: String?) {
        if (isQuietEnabled()) {
            log(LogLevel.QUIET, null, message)
        }
    }

    override fun quiet(message: String?, vararg objects: Any?) {
        if (isQuietEnabled()) {
            log(LogLevel.QUIET, null, message, objects)
        }
    }

    override fun quiet(message: String?, throwable: Throwable?) {
        if (isQuietEnabled()) {
            log(LogLevel.QUIET, throwable, message)
        }
    }

    override fun isEnabled(level: LogLevel?): Boolean {
        return isLevelAtMost(level)
    }

    override fun log(level: LogLevel?, message: String?) {
        if (isEnabled(level)) {
            log(level, null, message)
        }
    }

    override fun log(level: LogLevel?, message: String?, vararg objects: Any?) {
        if (isEnabled(level)) {
            log(level, null, message, objects)
        }
    }

    override fun log(level: LogLevel?, message: String?, throwable: Throwable?) {
        if (isEnabled(level)) {
            log(level, throwable, message)
        }
    }

    override fun info(msg: String?, t: Throwable?) {
        if (isInfoEnabled()) {
            log(LogLevel.INFO, t, msg)
        }
    }

    override fun info(marker: Marker?, msg: String?) {
        if (isInfoEnabled(marker)) {
            log(toLogLevel(marker), null, msg)
        }
    }

    override fun info(marker: Marker?, format: String?, arg: Any?) {
        if (isInfoEnabled(marker)) {
            log(toLogLevel(marker), null, format, arg)
        }
    }

    override fun info(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        if (isInfoEnabled(marker)) {
            log(toLogLevel(marker), null, format, arg1, arg2)
        }
    }

    override fun info(marker: Marker?, format: String?, vararg argArray: Any?) {
        if (isInfoEnabled(marker)) {
            log(toLogLevel(marker), null, format, argArray)
        }
    }

    override fun info(marker: Marker?, msg: String?, t: Throwable?) {
        if (isInfoEnabled(marker)) {
            log(toLogLevel(marker), t, msg)
        }
    }

    override fun warn(message: String?) {
        if (isWarnEnabled()) {
            log(LogLevel.WARN, null, message)
        }
    }

    override fun warn(format: String?, arg: Any?) {
        if (isWarnEnabled()) {
            log(LogLevel.WARN, null, format, arg)
        }
    }

    override fun warn(format: String?, arg1: Any?, arg2: Any?) {
        if (isWarnEnabled()) {
            log(LogLevel.WARN, null, format, arg1, arg2)
        }
    }

    override fun warn(format: String?, vararg arguments: Any?) {
        if (isWarnEnabled()) {
            log(LogLevel.WARN, null, format, arguments)
        }
    }

    override fun warn(msg: String?, t: Throwable?) {
        if (isWarnEnabled()) {
            log(LogLevel.WARN, t, msg)
        }
    }

    override fun warn(marker: Marker?, msg: String?) {
        if (isWarnEnabled(marker)) {
            log(LogLevel.WARN, null, msg)
        }
    }

    override fun warn(marker: Marker?, format: String?, arg: Any?) {
        if (isWarnEnabled(marker)) {
            log(LogLevel.WARN, null, format, arg)
        }
    }

    override fun warn(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        if (isWarnEnabled(marker)) {
            log(LogLevel.WARN, null, format, arg1, arg2)
        }
    }

    override fun warn(marker: Marker?, format: String?, vararg argArray: Any?) {
        if (isWarnEnabled(marker)) {
            log(LogLevel.WARN, null, format, argArray)
        }
    }

    override fun warn(marker: Marker?, msg: String?, t: Throwable?) {
        if (isWarnEnabled(marker)) {
            log(LogLevel.WARN, t, msg)
        }
    }

    override fun error(message: String?) {
        if (isErrorEnabled()) {
            log(LogLevel.ERROR, null, message)
        }
    }

    override fun error(format: String?, arg: Any?) {
        if (isErrorEnabled()) {
            log(LogLevel.ERROR, null, format, arg)
        }
    }

    override fun error(format: String?, arg1: Any?, arg2: Any?) {
        if (isErrorEnabled()) {
            log(LogLevel.ERROR, null, format, arg1, arg2)
        }
    }

    override fun error(format: String?, vararg arguments: Any?) {
        if (isErrorEnabled()) {
            log(LogLevel.ERROR, null, format, arguments)
        }
    }

    override fun error(msg: String?, t: Throwable?) {
        if (isErrorEnabled()) {
            log(LogLevel.ERROR, t, msg)
        }
    }

    override fun error(marker: Marker?, msg: String?) {
        if (isErrorEnabled(marker)) {
            log(LogLevel.ERROR, null, msg)
        }
    }

    override fun error(marker: Marker?, format: String?, arg: Any?) {
        if (isErrorEnabled(marker)) {
            log(LogLevel.ERROR, null, format, arg)
        }
    }

    override fun error(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        if (isErrorEnabled(marker)) {
            log(LogLevel.ERROR, null, format, arg1, arg2)
        }
    }

    override fun error(marker: Marker?, format: String?, vararg argArray: Any?) {
        if (isErrorEnabled(marker)) {
            log(LogLevel.ERROR, null, format, argArray)
        }
    }

    override fun error(marker: Marker?, msg: String?, t: Throwable?) {
        if (isErrorEnabled(marker)) {
            log(LogLevel.ERROR, t, msg)
        }
    }

    companion object {
        fun toLogLevel(marker: Marker?): LogLevel {
            if (marker == null) {
                return LogLevel.INFO
            }
            if (marker === Logging.LIFECYCLE) {
                return LogLevel.LIFECYCLE
            }
            if (marker === Logging.QUIET) {
                return LogLevel.QUIET
            }
            return LogLevel.INFO
        }
    }
}
