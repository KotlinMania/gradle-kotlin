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
package org.gradle.internal.nativeintegration.services

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions
import net.rubygrapefruit.platform.Native
import net.rubygrapefruit.platform.NativeException
import net.rubygrapefruit.platform.NativeIntegration
import net.rubygrapefruit.platform.NativeIntegrationUnavailableException
import net.rubygrapefruit.platform.Process
import net.rubygrapefruit.platform.ProcessLauncher
import net.rubygrapefruit.platform.SystemInfo
import net.rubygrapefruit.platform.WindowsRegistry
import net.rubygrapefruit.platform.file.FileSystems
import net.rubygrapefruit.platform.internal.DefaultProcessLauncher
import net.rubygrapefruit.platform.memory.Memory
import net.rubygrapefruit.platform.terminal.Terminals
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider
import org.gradle.fileevents.FileEvents
import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.internal.Cast
import org.gradle.internal.SystemProperties
import org.gradle.internal.file.FileMetadataAccessor
import org.gradle.internal.file.nio.NioFileMetadataAccessor
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.nativeintegration.NativeCapabilities
import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.internal.nativeintegration.console.ConsoleDetector
import org.gradle.internal.nativeintegration.console.FallbackConsoleDetector
import org.gradle.internal.nativeintegration.console.NativePlatformConsoleDetector
import org.gradle.internal.nativeintegration.console.TestOverrideConsoleDetector
import org.gradle.internal.nativeintegration.console.WindowsConsoleDetector
import org.gradle.internal.nativeintegration.filesystem.services.FileSystemServices
import org.gradle.internal.nativeintegration.jansi.JansiBootPathConfigurer
import org.gradle.internal.nativeintegration.jna.UnsupportedEnvironment
import org.gradle.internal.nativeintegration.network.HostnameLookup
import org.gradle.internal.nativeintegration.processenvironment.NativePlatformBackedProcessEnvironment
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceCreationException
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.util.internal.VersionNumber
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.EnumSet

/**
 * Provides various native platform integration services.
 */
class NativeServices private constructor(private val userHomeDir: File, requestedFeatures: EnumSet<NativeFeatures>, mode: NativeServicesMode) : ServiceRegistrationProvider {
    private val useNativeIntegrations: Boolean
    private var nativeIntegration: Native? = null

    @get:Synchronized
    @get:VisibleForTesting
    protected val native: Native
        get() = Preconditions.checkNotNull(nativeIntegration)
    private val enabledFeatures: EnumSet<NativeFeatures> = EnumSet.noneOf<NativeFeatures>(NativeFeatures::class.java)

    private val services: ServiceRegistry

    enum class NativeFeatures {
        FILE_SYSTEM_WATCHING {
            override fun initialize(nativeBaseDir: File, builder: ServiceRegistryBuilder, useNativeIntegrations: Boolean): Boolean {
                if (!useNativeIntegrations) {
                    return false
                }
                val operatingSystem = OperatingSystem.current()
                if (operatingSystem.isMacOsX) {
                    val version = operatingSystem.version
                    if (VersionNumber.parse(version).getMajor() < 12) {
                        LOGGER.info("Disabling file system watching on macOS {}, as it is only supported for macOS 12+", version)
                        return false
                    }
                }
                try {
                    val fileEvents = FileEvents.init(nativeBaseDir)
                    LOGGER.info("Initialized file system watching services in: {}", nativeBaseDir)
                    builder.provider(object : ServiceRegistrationProvider {
                        @Provides
                        fun createFileEventFunctionsProvider(): FileEventFunctionsProvider {
                            return object : FileEventFunctionsProvider {
                                override fun <T : NativeIntegration?> getFunctions(type: Class<T?>): T? {
                                    if (fileEvents != null) {
                                        return fileEvents.get<T?>(type)
                                    } else {
                                        throw NativeIntegrationUnavailableException("File events are not available.")
                                    }
                                }
                            }
                        }
                    })
                    return true
                } catch (ex: NativeIntegrationUnavailableException) {
                    logFileSystemWatchingUnavailable(ex)
                }
                return false
            }

            override fun doWhenDisabled(builder: ServiceRegistryBuilder) {
                // We still need to provide an implementation of FileEventFunctionsProvider,
                // even if file watching is disabled, otherwise the service registry will throw an exception for a missing service.
                builder.provider(object : ServiceRegistrationProvider {
                    @Provides
                    fun createFileEventFunctionsProvider(): FileEventFunctionsProvider {
                        return object : FileEventFunctionsProvider {
                            override fun <T : NativeIntegration?> getFunctions(type: Class<T?>): T? {
                                throw UnsupportedOperationException("File system watching is disabled.")
                            }
                        }
                    }
                })
            }
        },
        JANSI {
            override fun initialize(nativeBaseDir: File, builder: ServiceRegistryBuilder, useNativeIntegrations: Boolean): Boolean {
                JANSI_BOOT_PATH_CONFIGURER.configure(nativeBaseDir)
                LOGGER.info("Initialized jansi services in: {}", nativeBaseDir)
                return true
            }

            override fun doWhenDisabled(builder: ServiceRegistryBuilder) {
            }
        };

        abstract fun initialize(nativeBaseDir: File, builder: ServiceRegistryBuilder, canUseNativeIntegrations: Boolean): Boolean
        abstract fun doWhenDisabled(builder: ServiceRegistryBuilder)
    }

    enum class NativeServicesMode {
        ENABLED {
            override val isEnabled: Boolean
                get() = true

            override val isPotentiallyEnabled: Boolean
                get() = true
        },
        DISABLED {
            override val isEnabled: Boolean
                get() = false

            override val isPotentiallyEnabled: Boolean
                get() = false
        },
        NOT_SET {
            override val isEnabled: Boolean
                get() = throw UnsupportedOperationException("Cannot determine if native services are enabled or not for " + this + " mode.")

            override val isPotentiallyEnabled: Boolean
                get() = true
        };

        abstract val isEnabled: Boolean

        /**
         * Check if the native services might be enabled. This is used to determine if a process needs to be started with flags that allow native access.
         *
         *
         *
         * This is used instead of looking at all possible sources of system properties to determine if the native services would be used, as that would be expensive and complicated.
         * This could result in a process being started with flags that allow native access when it's not needed by Gradle.
         * As it's likely that the native services are enabled, this trade-off is acceptable.
         *
         *
         * @return `true` if the native services might be enabled, `false` otherwise
         */
        abstract val isPotentiallyEnabled: Boolean

        companion object {
            fun from(isEnabled: Boolean): NativeServicesMode {
                return if (isEnabled) NativeServicesMode.ENABLED else NativeServicesMode.DISABLED
            }

            @JvmStatic
            fun fromSystemProperties(): NativeServicesMode {
                return fromString(System.getProperty(NATIVE_SERVICES_OPTION))
            }

            fun fromProperties(properties: MutableMap<String, String>): NativeServicesMode {
                return fromString(properties.get(NATIVE_SERVICES_OPTION))
            }

            fun fromString(value: String?): NativeServicesMode {
                // Default to enabled, make it disabled only if explicitly set to "false"
                var value = value
                value = (if (value == null) "true" else value).trim { it <= ' ' }
                return from(!"false".equals(value, ignoreCase = true))
            }
        }
    }

    init {
        var useNativeIntegrations = mode.isEnabled
        var loadedNative: Native? = null
        val nativeBaseDir: File = getNativeServicesDir(userHomeDir).getAbsoluteFile()
        if (useNativeIntegrations) {
            try {
                loadedNative = Native.init(nativeBaseDir)
            } catch (ex: NativeIntegrationUnavailableException) {
                LOGGER.debug("Native-platform is not available.", ex)
                useNativeIntegrations = false
            } catch (ex: NativeException) {
                if (ex.cause is UnsatisfiedLinkError && ex.cause!!.message!!.lowercase().contains("already loaded in another classloader")) {
                    LOGGER.debug("Unable to initialize native-platform. Failure: {}", format(ex))
                    useNativeIntegrations = false
                } else if (ex.message == "Could not extract native JNI library."
                    && ex.cause!!.message!!.contains("native-platform.dll (The process cannot access the file because it is being used by another process)")
                ) {
                    //triggered through tooling API of Gradle <2.3 - native-platform.dll is shared by tooling client (<2.3) and daemon (current) and it is locked by the client (<2.3 issue)
                    LOGGER.debug("Unable to initialize native-platform. Failure: {}", format(ex))
                    useNativeIntegrations = false
                } else {
                    throw ex
                }
            }
            LOGGER.info("Initialized native services in: {}", nativeBaseDir)
        }
        this.useNativeIntegrations = useNativeIntegrations
        this.nativeIntegration = loadedNative

        val builder = ServiceRegistryBuilder.builder()
            .displayName("native services")
            .provider(FileSystemServices())
            .provider(this)
            .provider(object : ServiceRegistrationProvider {
                @Suppress("unused")
                fun configure(registration: ServiceRegistration) {
                    registration.add(GradleUserHomeTemporaryFileProvider::class.java)
                }
            })

        for (nativeFeature in NativeFeatures.entries) {
            if (requestedFeatures.contains(nativeFeature) && nativeFeature.initialize(nativeBaseDir, builder, useNativeIntegrations)) {
                enabledFeatures.add(nativeFeature)
            } else {
                nativeFeature.doWhenDisabled(builder)
            }
        }

        this.services = builder.build()
    }

    private fun isFeatureEnabled(feature: NativeFeatures): Boolean {
        return enabledFeatures.contains(feature)
    }

    @Provides
    protected fun createGradleUserHomeDirProvider(): GradleUserHomeDirProvider {
        return object : GradleUserHomeDirProvider {
            override fun getGradleUserHomeDirectory(): File {
                return userHomeDir
            }
        }
    }

    @Provides
    protected fun createOperatingSystem(): OperatingSystem {
        return OperatingSystem.current()
    }

    @Provides
    protected fun createJvm(): Jvm {
        return Jvm.current()
    }

    @Provides
    protected fun createProcessEnvironment(operatingSystem: OperatingSystem): ProcessEnvironment {
        if (useNativeIntegrations) {
            try {
                val process: Process = native.get<Process>(Process::class.java)
                return NativePlatformBackedProcessEnvironment(process)
            } catch (ex: NativeIntegrationUnavailableException) {
                LOGGER.debug("Native-platform process integration is not available. Continuing with fallback.")
            }
        }

        return UnsupportedEnvironment()
    }

    @Provides
    protected fun createConsoleDetector(operatingSystem: OperatingSystem): ConsoleDetector {
        return TestOverrideConsoleDetector(backingConsoleDetector(operatingSystem))
    }

    private fun backingConsoleDetector(operatingSystem: OperatingSystem): ConsoleDetector {
        if (useNativeIntegrations) {
            try {
                val terminals: Terminals = native.get<Terminals>(Terminals::class.java)
                return NativePlatformConsoleDetector(terminals)
            } catch (ex: NativeIntegrationUnavailableException) {
                LOGGER.debug("Native-platform terminal integration is not available. Continuing with fallback.")
            } catch (ex: NativeException) {
                LOGGER.debug("Unable to load from native-platform backed ConsoleDetector. Continuing with fallback. Failure: {}", format(ex))
            }

            try {
                if (operatingSystem.isWindows) {
                    return WindowsConsoleDetector()
                }
            } catch (e: LinkageError) {
                // Thrown when jna cannot initialize the native stuff
                LOGGER.debug("Unable to load native library. Continuing with fallback. Failure: {}", format(e))
            }
        }

        return FallbackConsoleDetector()
    }

    @Provides
    protected fun createWindowsRegistry(operatingSystem: OperatingSystem): WindowsRegistry {
        if (useNativeIntegrations && operatingSystem.isWindows) {
            return native.get<WindowsRegistry>(WindowsRegistry::class.java)
        }
        return notAvailable<WindowsRegistry>(WindowsRegistry::class.java, operatingSystem)
    }

    @Provides
    fun createSystemInfo(operatingSystem: OperatingSystem): SystemInfo {
        if (useNativeIntegrations) {
            try {
                return native.get<SystemInfo>(SystemInfo::class.java)
            } catch (e: NativeIntegrationUnavailableException) {
                LOGGER.debug("Native-platform system info is not available. Continuing with fallback.")
            }
        }
        return notAvailable<SystemInfo>(SystemInfo::class.java, operatingSystem)
    }

    @Provides
    protected fun createMemory(operatingSystem: OperatingSystem): Memory {
        if (useNativeIntegrations) {
            try {
                return native.get<Memory>(Memory::class.java)
            } catch (e: NativeIntegrationUnavailableException) {
                LOGGER.debug("Native-platform memory integration is not available. Continuing with fallback.")
            }
        }
        return notAvailable<Memory>(Memory::class.java, operatingSystem)
    }

    @Provides
    protected fun createProcessLauncher(): ProcessLauncher {
        if (useNativeIntegrations) {
            try {
                return native.get<ProcessLauncher>(ProcessLauncher::class.java)
            } catch (e: NativeIntegrationUnavailableException) {
                LOGGER.debug("Native-platform process launcher is not available. Continuing with fallback.")
            }
        }
        return DefaultProcessLauncher()
    }

    @Provides
    protected fun createHostnameLookup(): HostnameLookup {
        if (useNativeIntegrations) {
            try {
                val hostname: String = native.get<SystemInfo>(SystemInfo::class.java).getHostname()
                return FixedHostname(hostname)
            } catch (e: NativeIntegrationUnavailableException) {
                LOGGER.debug("Native-platform posix files integration is not available. Continuing with fallback.")
            }
        }
        var hostname: String
        try {
            hostname = InetAddress.getLocalHost().getHostName()
        } catch (e: UnknownHostException) {
            hostname = InetAddress.getLoopbackAddress().getHostAddress()
        }
        return FixedHostname(hostname)
    }

    @Provides
    protected fun createFileMetadataAccessor(operatingSystem: OperatingSystem): FileMetadataAccessor {
        return NioFileMetadataAccessor()
    }

    @Provides
    fun createNativeCapabilities(): NativeCapabilities {
        return object : NativeCapabilities {
            override fun useNativeIntegrations(): Boolean {
                return useNativeIntegrations
            }

            override fun useFileSystemWatching(): Boolean {
                return isFeatureEnabled(NativeFeatures.FILE_SYSTEM_WATCHING)
            }
        }
    }

    @Provides
    protected fun createFileSystems(operatingSystem: OperatingSystem): FileSystems {
        if (useNativeIntegrations) {
            try {
                return native.get<FileSystems>(FileSystems::class.java)
            } catch (e: NativeIntegrationUnavailableException) {
                LOGGER.debug("Native-platform file systems information is not available. Continuing with fallback.")
            }
        }
        return notAvailable<FileSystems>(FileSystems::class.java, operatingSystem)
    }

    private fun <T : Any> notAvailable(type: Class<T>, operatingSystem: OperatingSystem): T {
        return Cast.uncheckedNonnullCast(Proxy.newProxyInstance(type.getClassLoader(), arrayOf<Class<*>>(type), BrokenService(type.getSimpleName(), useNativeIntegrations, operatingSystem)))
    }

    private class BrokenService(private val type: String, private val useNativeIntegrations: Boolean, private val operatingSystem: OperatingSystem) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<Any>): Any? {
            throw org.gradle.internal.nativeintegration.NativeIntegrationUnavailableException(
                String.format(
                    "Service '%s' is not available (os=%s, enabled=%s).",
                    type,
                    operatingSystem,
                    useNativeIntegrations
                )
            )
        }
    }

    private class FixedHostname(override val hostname: String) : HostnameLookup

    interface FileEventFunctionsProvider {
        fun <T : NativeIntegration?> getFunctions(type: Class<T?>): T?
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(NativeServices::class.java)
        private var instance: NativeServices? = null

        // TODO All this should be static
        private val JANSI_BOOT_PATH_CONFIGURER = JansiBootPathConfigurer()

        const val NATIVE_SERVICES_OPTION: String = "org.gradle.native"
        const val NATIVE_DIR_OVERRIDE: String = "org.gradle.native.dir"

        /**
         * Initializes the native services to use the given user home directory to store native libs and other resources. Does nothing if already initialized.
         *
         * Initializes all the services needed for the Gradle daemon.
         */
        @JvmStatic
        fun initializeOnDaemon(userHomeDir: File, mode: NativeServicesMode) {
            initialize(userHomeDir, EnumSet.allOf<NativeFeatures>(NativeFeatures::class.java), mode)
        }

        /**
         * Initializes the native services to use the given user home directory to store native libs and other resources. Does nothing if already initialized.
         *
         * Initializes all the services needed for the CLI or the Tooling API.
         */
        @JvmStatic
        fun initializeOnClient(userHomeDir: File, mode: NativeServicesMode) {
            initialize(userHomeDir, EnumSet.of<NativeFeatures>(NativeFeatures.JANSI), mode)
        }

        /**
         * Initializes the native services to use the given user home directory to store native libs and other resources. Does nothing if already initialized.
         *
         * Initializes all the services needed for the CLI or the Tooling API.
         */
        @JvmStatic
        fun initializeOnWorker(userHomeDir: File, mode: NativeServicesMode) {
            initialize(userHomeDir, EnumSet.noneOf<NativeFeatures>(NativeFeatures::class.java), mode)
        }

        @JvmStatic
        fun logFileSystemWatchingUnavailable(ex: NativeIntegrationUnavailableException) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("File system watching is not available", ex)
            } else {
                LOGGER.info("File system watching is not available: {}", ex.message)
            }
        }

        /**
         * Initializes the native services to use the given user home directory to store native libs and other resources. Does nothing if already initialized.
         *
         * @param requestedFeatures Whether to initialize additional native libraries like jansi and file-events.
         */
        private fun initialize(userHomeDir: File, requestedFeatures: EnumSet<NativeFeatures>, mode: NativeServicesMode) {
            checkNativeServicesMode(mode)
            if (instance == null) {
                try {
                    instance = NativeServices(userHomeDir, requestedFeatures, mode)
                } catch (e: RuntimeException) {
                    throw ServiceCreationException("Could not initialize native services.", e)
                }
            }
        }

        private fun checkNativeServicesMode(mode: NativeServicesMode) {
            require(!(mode !== NativeServicesMode.ENABLED && mode !== NativeServicesMode.DISABLED)) { "Only explicit ENABLED or DISABLED mode is allowed for the NativeServices initialization, but was: " + mode }
        }

        private fun getNativeServicesDir(userHomeDir: File): File {
            val overrideProperty: String? = nativeDirOverride
            if (overrideProperty == null) {
                return File(userHomeDir, "native")
            } else {
                return File(overrideProperty)
            }
        }

        private val nativeDirOverride: String?
            get() = System.getProperty(
                NATIVE_DIR_OVERRIDE,
                System.getenv(NATIVE_DIR_OVERRIDE)
            )

        @JvmStatic
        @Synchronized
        fun getInstance(): ServiceRegistry {
            checkNotNull(instance) { "Cannot get an instance of NativeServices without first calling initialize()." }
            return instance!!.services
        }

        private fun format(throwable: Throwable): String {
            val builder = StringBuilder()
            builder.append(throwable)
            var current = throwable.cause
            while (current != null) {
                builder.append(SystemProperties.getInstance().getLineSeparator())
                builder.append("caused by: ")
                builder.append(current)
                current = current.cause
            }
            return builder.toString()
        }
    }
}
