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

import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.logging.LogLevel
import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.internal.logging.LoggingManagerFactory
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.config.LoggingSourceSystem
import org.gradle.internal.logging.config.LoggingSystemAdapter
import org.gradle.internal.logging.console.DefaultUserInputReceiver
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.sink.OutputEventListenerManager
import org.gradle.internal.logging.sink.OutputEventRenderer
import org.gradle.internal.logging.slf4j.Slf4jLoggingConfigurer
import org.gradle.internal.logging.source.DefaultStdErrLoggingSystem
import org.gradle.internal.logging.source.DefaultStdOutLoggingSystem
import org.gradle.internal.logging.source.JavaUtilLoggingSystem
import org.gradle.internal.logging.source.NoOpLoggingSystem
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.time.Clock
import org.gradle.internal.time.Time
import org.gradle.wrapper.GradleUserHomeLookup

/**
 * A [ServiceRegistry] implementation that provides the logging services. To use this:
 *
 *
 *  1. Create an instance using one of the static factory methods below.
 *  1. Create an instance of [LoggingManagerInternal].
 *  1. Configure the logging manager as appropriate.
 *  1. Start the logging manager using [LoggingManagerInternal.start].
 *  1. When finished, stop the logging manager using [LoggingManagerInternal.stop].
 *
 */
abstract class LoggingServiceRegistry : ServiceRegistrationProvider {
    protected var stdoutListener: TextStreamOutputEventListener? = null
        get() {
            if (field == null) {
                field = TextStreamOutputEventListener(outputEventListenerManager.broadcaster)
            }
            return field
        }
        private set

    private val userInput = DefaultUserInputReceiver()
    protected val renderer: OutputEventRenderer = makeOutputEventRenderer()
    protected val outputEventListenerManager: OutputEventListenerManager = OutputEventListenerManager(renderer)

    @Provides
    protected fun createTimeProvider(): Clock {
        return Time.clock()
    }

    @Provides
    protected fun createStyledTextOutputFactory(clock: Clock): StyledTextOutputFactory {
        return DefaultStyledTextOutputFactory(this.stdoutListener!!, clock)
    }

    @Provides
    protected open fun createLoggingManagerFactory(clock: Clock): LoggingManagerFactory {
        val outputEventBroadcaster = outputEventListenerManager.broadcaster

        val stdout: LoggingSourceSystem = DefaultStdOutLoggingSystem(this.stdoutListener!!, clock)
        stdout.setLevel(LogLevel.QUIET)
        val stderr: LoggingSourceSystem = DefaultStdErrLoggingSystem(TextStreamOutputEventListener(outputEventBroadcaster), clock)
        stderr.setLevel(LogLevel.ERROR)
        return DefaultLoggingManagerFactory(
            renderer,
            LoggingSystemAdapter(Slf4jLoggingConfigurer(outputEventBroadcaster)),
            JavaUtilLoggingSystem(),
            stdout,
            stderr
        )
    }

    @Provides
    protected fun createOutputEventListener(manager: OutputEventListenerManager): OutputEventListener? {
        return manager.broadcaster
    }

    @Provides
    protected fun createOutputEventListenerManager(): OutputEventListenerManager {
        return outputEventListenerManager
    }

    @Provides
    protected fun createUserInput(): DefaultUserInputReceiver {
        return userInput
    }

    // Intentionally not a "create" method as this should not be exposed as a service
    protected fun makeOutputEventRenderer(): OutputEventRenderer {
        val temporaryFileProvider: TemporaryFileProvider = GradleUserHomeTemporaryFileProvider(GradleUserHomeDirProvider { GradleUserHomeLookup.gradleUserHome() })
        val eventRenderer = OutputEventRenderer(Time.clock(), userInput, temporaryFileProvider)
        userInput.attachConsole(eventRenderer)
        return eventRenderer
    }

    private class CommandLineLogging : LoggingServiceRegistry()

    private class NestedLogging : LoggingServiceRegistry() {
        @Provides
        override fun createLoggingManagerFactory(clock: Clock): LoggingManagerFactory {
            // Don't configure anything
            return DefaultLoggingManagerFactory(
                renderer,
                NoOpLoggingSystem(),
                NoOpLoggingSystem(),
                NoOpLoggingSystem(),
                NoOpLoggingSystem()
            )
        }
    }

    companion object {
        val NO_OP: ServiceRegistrationProvider = object : ServiceRegistrationProvider {
            @Provides
            fun createOutputEventListener(): OutputEventListener {
                return OutputEventListener.NO_OP
            }
        }

        /**
         * Creates a set of logging services which are suitable to use globally in a process. In particular:
         *
         *
         *  * Replaces System.out and System.err with implementations that route output through the logging system as per [LoggingManagerInternal.captureSystemSources].
         *  * Configures slf4j, log4j and java util logging to route log messages through the logging system.
         *  * Routes logging output to the original System.out and System.err as per [LoggingManagerInternal.attachSystemOutAndErr].
         *  * Sets log level to [LogLevel.LIFECYCLE].
         *
         *
         *
         * Does nothing until started.
         *
         *
         * Allows dynamic and colored output to be written to the console. Use [LoggingManagerInternal.attachProcessConsole] to enable this.
         */
        @JvmStatic
        fun newCommandLineProcessLogging(): ServiceRegistry {
            val loggingServices: ServiceRegistry = createCommandLineLogging()
        val rootLoggingManager = loggingServices.get(LoggingManagerFactory::class.java as Class<LoggingManagerFactory?>)!!.root!!
        rootLoggingManager.captureSystemSources()
        rootLoggingManager.attachSystemOutAndErr()
            return loggingServices
        }

        /**
         * Creates a set of logging services which are suitable to use embedded in another application. In particular:
         *
         *
         *  * Configures slf4j and log4j to route log messages through the logging system.
         *  * Sets log level to [LogLevel.LIFECYCLE].
         *
         *
         *
         * Does not:
         *
         *
         *  * Replace System.out and System.err to capture output written to these destinations. Use [LoggingManagerInternal.captureSystemSources] to enable this.
         *  * Configure java util logging. Use [LoggingManagerInternal.captureSystemSources] to enable this.
         *  * Route logging output to the original System.out and System.err. Use [LoggingManagerInternal.attachSystemOutAndErr] to enable this.
         *
         *
         *
         * Does nothing until started.
         */
        @JvmStatic
        fun newEmbeddableLogging(): ServiceRegistry {
            return createCommandLineLogging()
        }

        /**
         * Creates a set of logging services to set up a new logging scope that does nothing by default. The methods on [LoggingManagerInternal] can be used to configure the
         * logging services do useful things.
         *
         *
         * Sets log level to [LogLevel.LIFECYCLE].
         */
        @JvmStatic
        fun newNestedLogging(): ServiceRegistry {
            return ServiceRegistryBuilder.builder()
                .displayName("logging services")
                .provider(NestedLogging())
                .build()
        }

        private fun createCommandLineLogging(): ServiceRegistry {
            return ServiceRegistryBuilder.builder()
                .displayName("logging services")
                .provider(CommandLineLogging())
                .build()
        }
    }
}
