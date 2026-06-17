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
package org.gradle.launcher.bootstrap

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.Action
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.configuration.DefaultBuildClientMetaData
import org.gradle.configuration.GradleLauncherMetaData
import org.gradle.internal.buildevents.BuildExceptionReporter
import org.gradle.internal.logging.DefaultLoggingConfiguration
import org.gradle.internal.logging.text.StreamingStyledTextOutputFactory
import org.gradle.internal.problems.failure.DefaultFailureFactory
import java.io.PrintStream

/**
 * An entry point is the point at which execution will never return from.
 *
 *
 * Its purpose is to consistently apply our completion logic of forcing the JVM
 * to exit at a certain point instead of waiting for all threads to die, and to provide
 * some consistent unhandled exception catching.
 *
 *
 * Entry points may be nested, as is the case when a foreground daemon is started.
 *
 *
 * The createCompleter() and createErrorHandler() are not really intended to be overridden
 * by subclasses as they define our entry point behaviour, but they are protected to enable
 * testing as it's difficult to test something that will call System.exit().
 */
abstract class EntryPoint {
    private val originalStdErr: PrintStream = System.err

    /**
     * Unless the createCompleter() method is overridden, the JVM will exit before returning from this method.
     */
    fun run(args: Array<String>) {
        val listener = RecordingExecutionListener()
        try {
            doAction(args, listener)
        } catch (e: Throwable) {
            createErrorHandler().execute(e)
            listener.onFailure(e)
        }

        val failure: Throwable? = listener.failure
        val completer = createCompleter()
        if (failure == null) {
            completer.complete()
        } else {
            completer.completeWithFailure(failure)
        }
    }

    @VisibleForTesting
    protected open fun createCompleter(): ExecutionCompleter {
        return ProcessCompleter()
    }

    @VisibleForTesting
    protected fun createErrorHandler(): Action<Throwable> {
        val loggingConfiguration = DefaultLoggingConfiguration()
        loggingConfiguration.setShowStacktrace(ShowStacktrace.ALWAYS_FULL)
        return BuildExceptionReporter(
            StreamingStyledTextOutputFactory(originalStdErr),
            loggingConfiguration,
            DefaultBuildClientMetaData(GradleLauncherMetaData()),
            DefaultFailureFactory.withDefaultClassifier()
        )
    }

    protected abstract fun doAction(args: Array<String>, listener: ExecutionListener)

    private class RecordingExecutionListener : ExecutionListener {
        var failure: Throwable? = null
            private set

        override fun onFailure(failure: Throwable) {
            this.failure = failure
        }
    }
}
