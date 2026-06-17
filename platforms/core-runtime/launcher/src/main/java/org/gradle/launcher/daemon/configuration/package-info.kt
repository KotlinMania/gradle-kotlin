/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.launcher.daemon.configuration

import org.gradle.cli.OptionCategory
import org.gradle.internal.buildoption.BooleanBuildOption
import org.gradle.internal.buildoption.BooleanCommandLineOptionConfiguration
import org.gradle.internal.buildoption.BuildOption
import org.gradle.internal.buildoption.BuildOptionSet
import org.gradle.internal.buildoption.CommandLineOptionConfiguration
import org.gradle.internal.buildoption.EnabledOnlyBooleanBuildOption
import org.gradle.internal.buildoption.Origin
import org.gradle.internal.buildoption.StringBuildOption
import org.gradle.internal.jvm.JavaHomeException
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.launcher.daemon.toolchain.DaemonJvmCriteria
import org.gradle.process.internal.JvmOptions
import org.jspecify.annotations.NullMarked
import java.io.File
import java.util.Arrays

class DaemonBuildOptions : BuildOptionSet<DaemonParameters>() {
    override fun getAllOptions(): MutableList<out BuildOption<in DaemonParameters>> {
        return options
    }

    class IdleTimeoutOption : StringBuildOption<DaemonParameters>(GRADLE_PROPERTY) {
        override fun getCategory(): OptionCategory {
            return OptionCategory.PERFORMANCE
        }

        override fun applyTo(value: String, settings: DaemonParameters, origin: Origin) {
            try {
                settings.setIdleTimeout(value.toInt())
            } catch (e: NumberFormatException) {
                origin.handleInvalidValue(value, "the value should be an int")
            }
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.daemon.idletimeout"
        }
    }

    class HealthCheckOption : StringBuildOption<DaemonParameters>(GRADLE_PROPERTY) {
        override fun getCategory(): OptionCategory {
            return OptionCategory.DIAGNOSTICS
        }

        override fun applyTo(value: String, settings: DaemonParameters, origin: Origin) {
            try {
                settings.setPeriodicCheckInterval(value.toInt())
            } catch (e: NumberFormatException) {
                origin.handleInvalidValue(value, "the value should be an int")
            }
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.daemon.healthcheckinterval"
        }
    }

    class BaseDirOption : StringBuildOption<DaemonParameters>(GRADLE_PROPERTY) {
        override fun getCategory(): OptionCategory {
            return OptionCategory.EXECUTION
        }

        override fun applyTo(value: String, settings: DaemonParameters, origin: Origin) {
            settings.setBaseDir(File(value))
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.daemon.registry.base"
        }
    }

    class JvmArgsOption : StringBuildOption<DaemonParameters>(GRADLE_PROPERTY) {
        override fun getCategory(): OptionCategory {
            return OptionCategory.EXECUTION
        }

        override fun applyTo(value: String, settings: DaemonParameters, origin: Origin) {
            settings.setJvmArgs(JvmOptions.fromString(value))
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.jvmargs"
        }
    }

    class JavaHomeOption : StringBuildOption<DaemonParameters>(GRADLE_PROPERTY) {
        override fun applyTo(value: String, settings: DaemonParameters, origin: Origin) {
            val javaHome = File(value)
            if (!javaHome.isDirectory()) {
                origin.handleInvalidValue(value, "Java home supplied is invalid")
            }
            try {
                settings.setRequestedJvmCriteria(DaemonJvmCriteria.JavaHome(DaemonJvmCriteria.JavaHome.Source.ORG_GRADLE_JAVA_HOME, javaHome))
            } catch (e: JavaHomeException) {
                origin.handleInvalidValue(value, "Java home supplied seems to be invalid")
            }
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.EXECUTION
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.java.home"
        }
    }

    class DebugOption : BooleanBuildOption<DaemonParameters>(GRADLE_PROPERTY) {
        override fun applyTo(value: Boolean, settings: DaemonParameters, origin: Origin) {
            settings.setDebug(value)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DIAGNOSTICS
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.debug"
        }
    }

    class DebugHostOption : StringBuildOption<DaemonParameters>(GRADLE_PROPERTY) {
        override fun applyTo(value: String, settings: DaemonParameters, origin: Origin) {
            settings.setDebugHost(value)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DIAGNOSTICS
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.debug.host"
        }
    }

    class DebugPortOption : StringBuildOption<DaemonParameters>(GRADLE_PROPERTY) {
        override fun applyTo(value: String, settings: DaemonParameters, origin: Origin) {
            val hint = "must be a number between 1 and 65535"
            var port = 0
            try {
                port = value.toInt()
            } catch (e: NumberFormatException) {
                origin.handleInvalidValue(value, hint)
            }
            if (port < 1 || port > 65535) {
                origin.handleInvalidValue(value, hint)
            } else {
                settings.setDebugPort(port)
            }
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DIAGNOSTICS
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.debug.port"
        }
    }

    class DebugSuspendOption : BooleanBuildOption<DaemonParameters>(GRADLE_PROPERTY) {
        override fun applyTo(value: Boolean, settings: DaemonParameters, origin: Origin) {
            settings.setDebugSuspend(value)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DIAGNOSTICS
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.debug.suspend"
        }
    }

    class DebugServerOption : BooleanBuildOption<DaemonParameters>(GRADLE_PROPERTY) {
        override fun applyTo(value: Boolean, settings: DaemonParameters, origin: Origin) {
            settings.setDebugServer(value)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DIAGNOSTICS
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.debug.server"
        }
    }

    /**
     * This is a feature flag that enables the instrumentation Java agent for the daemon.
     */
    class ApplyInstrumentationAgentOption : BooleanBuildOption<DaemonParameters>(GRADLE_PROPERTY) {
        override fun applyTo(value: Boolean, settings: DaemonParameters, origin: Origin) {
            settings.setApplyInstrumentationAgent(value)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DIAGNOSTICS
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.internal.instrumentation.agent"
        }
    }

    @NullMarked
    class NativeServicesOption : StringBuildOption<DaemonParameters>(NativeServices.NATIVE_SERVICES_OPTION) {
        override fun applyTo(value: String, settings: DaemonParameters, origin: Origin) {
            settings.setNativeServicesMode(NativeServices.NativeServicesMode.fromString(value))
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DIAGNOSTICS
        }
    }

    class DaemonOption : BooleanBuildOption<DaemonParameters>(
        GRADLE_PROPERTY,
        BooleanCommandLineOptionConfiguration.create(
            "daemon",
            "Uses the Gradle daemon to run the build. Starts the daemon if it is not running.",
            "Runs the build without the Gradle daemon. Useful occasionally if you have configured Gradle to always run with the daemon by default."
        )
    ) {
        override fun applyTo(value: Boolean, settings: DaemonParameters, origin: Origin) {
            settings.setEnabled(value)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DAEMON
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.daemon"
        }
    }

    class ForegroundOption : EnabledOnlyBooleanBuildOption<DaemonParameters>(null, CommandLineOptionConfiguration.create("foreground", "Starts the Gradle daemon in the foreground.")) {
        override fun applyTo(settings: DaemonParameters, origin: Origin) {
            settings.setForeground(true)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DAEMON
        }
    }

    class StopOption : EnabledOnlyBooleanBuildOption<DaemonParameters>(null, CommandLineOptionConfiguration.create("stop", "Stops the Gradle daemon if it is running.")) {
        override fun applyTo(settings: DaemonParameters, origin: Origin) {
            settings.setStop(true)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DAEMON
        }
    }

    class StatusOption : EnabledOnlyBooleanBuildOption<DaemonParameters>(null, CommandLineOptionConfiguration.create("status", "Shows the status of running and recently stopped Gradle daemons.")) {
        override fun applyTo(settings: DaemonParameters, origin: Origin) {
            settings.setStatus(true)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DAEMON
        }
    }

    class PriorityOption : StringBuildOption<DaemonParameters>(
        GRADLE_PROPERTY,
        CommandLineOptionConfiguration.create("priority", "Specifies the scheduling priority for the Gradle daemon and all processes launched by it. Supported values are 'normal' (default) or 'low'.")
    ) {
        override fun applyTo(value: String, settings: DaemonParameters, origin: Origin) {
            try {
                settings.setPriority(DaemonPriority.valueOf(value.uppercase()))
            } catch (e: IllegalArgumentException) {
                origin.handleInvalidValue(value)
            }
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.PERFORMANCE
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.priority"
        }
    }

    companion object {
        private val options: MutableList<BuildOption<DaemonParameters>> = Arrays.asList<BuildOption<DaemonParameters>>(
            IdleTimeoutOption(),
            HealthCheckOption(),
            BaseDirOption(),
            JvmArgsOption(),
            JavaHomeOption(),
            DebugOption(),
            DebugHostOption(),
            DebugPortOption(),
            DebugServerOption(),
            DebugSuspendOption(),
            ApplyInstrumentationAgentOption(),
            DaemonOption(),
            ForegroundOption(),
            StopOption(),
            StatusOption(),
            PriorityOption(),
            NativeServicesOption()
        )
    }
}
