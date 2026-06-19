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
package org.gradle.process.internal

import org.apache.commons.lang3.StringUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.cache.internal.HeapProportionalCacheSizer
import org.gradle.process.JavaForkOptions
import org.gradle.util.internal.ArgumentsSplitter
import org.gradle.util.internal.GUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.Charset
import java.util.Arrays
import java.util.Collections
import java.util.Locale
import java.util.TreeMap
import java.util.stream.Collectors

class JvmOptions @JvmOverloads constructor(private val fileCollectionFactory: FileCollectionFactory, @JvmField val debugSpec: JvmDebugSpec = JvmDebugSpec.DefaultJvmDebugSpec()) {
    private val extraJvmArgs: MutableList<Any> = ArrayList<Any>()
    @JvmField
    val mutableSystemProperties: MutableMap<String?, Any?> = TreeMap<String?, Any?>()

    private var bootstrapClasspath: ConfigurableFileCollection? = null
    var minHeapSize: String? = null
    var maxHeapSize: String? = null
    var enableAssertions: Boolean = false

    @JvmField
    val immutableSystemProperties: MutableMap<String?, Any?> = TreeMap<String?, Any?>()

    init {
        immutableSystemProperties.put(FILE_ENCODING_KEY, Charset.defaultCharset().name())
        immutableSystemProperties.put(USER_LANGUAGE_KEY, DEFAULT_LOCALE.getLanguage())
        immutableSystemProperties.put(USER_COUNTRY_KEY, DEFAULT_LOCALE.getCountry())
        immutableSystemProperties.put(USER_VARIANT_KEY, DEFAULT_LOCALE.getVariant())
    }

    var allJvmArgs: MutableList<String?>
        /**
         * @return all jvm args including system properties
         */
        get() {
            val args: MutableList<String?> = ArrayList<String?>()
            formatSystemProperties(this.mutableSystemProperties, args)

            // We have to add these after the system properties so they can override any system properties
            // (identical properties later in the command line override earlier ones)
            args.addAll(this.allImmutableJvmArgs)

            return Collections.unmodifiableList<String?>(args)
        }
        set(arguments) {
            mutableSystemProperties.clear()
            minHeapSize = null
            maxHeapSize = null
            extraJvmArgs.clear()
            this.enableAssertions = false
            debugSpec.isEnabled = false
            jvmArgs(arguments)
        }

    protected fun formatSystemProperties(properties: MutableMap<String?, *>, args: MutableList<String?>) {
        for (entry in properties.entries) {
            if (entry.value != null && entry.value.toString().length > 0) {
                args.add("-D" + entry.key + "=" + entry.value.toString())
            } else {
                args.add("-D" + entry.key)
            }
        }
    }

    val allImmutableJvmArgs: MutableList<String?>
        /**
         * @return all immutable jvm args. It excludes most system properties.
         * Only implicitly immutable system properties like "file.encoding" are included.
         * The result is a subset of options returned by [.getAllJvmArgs]
         */
        get() {
            val args = ArrayList<String?>(this.jvmArgs)
            args.addAll(this.managedJvmArgs)
            return args
        }

    protected val managedJvmArgs: MutableList<String?>
        /**
         * @return the list of jvm args we manage explicitly, for example, max heaps size or file encoding.
         * The result is a subset of options returned by [.getAllImmutableJvmArgs]
         */
        get() {
            val args: MutableList<String?> = ArrayList<String?>()
            if (minHeapSize != null) {
                args.add(XMS_PREFIX + minHeapSize)
            }
            if (maxHeapSize != null) {
                args.add(XMX_PREFIX + maxHeapSize)
            }
            if (bootstrapClasspath != null && !bootstrapClasspath!!.isEmpty()) {
                args.add(BOOTCLASSPATH_PREFIX + bootstrapClasspath!!.getAsPath())
            }

            // These are implemented as a system property, but don't really function like one
            // So we include it in this "no system property" set.
            formatSystemProperties(immutableSystemProperties, args)

            if (this.enableAssertions) {
                args.add("-ea")
            }

            if (debugSpec.isEnabled) {
                args.add(this.debugArgument)
            }
            return args
        }

    private val debugArgument: String
        get() = getDebugArgument(debugSpec)

    var jvmArgs: MutableList<String?>
        get() = extraJvmArgs.stream().map<String?> { obj: Any? -> obj.toString() }.collect(Collectors.toList())
        set(arguments) {
            extraJvmArgs.clear()
            jvmArgs(arguments)
        }

    fun setExtraJvmArgs(arguments: Iterable<*>) {
        extraJvmArgs.clear()
        addExtraJvmArgs(arguments)
    }

    fun getExtraJvmArgs(): MutableList<Any> {
        return extraJvmArgs
    }

    fun checkDebugConfiguration(arguments: Iterable<*>) {
        val debugArgs: MutableList<String?> = collectDebugArgs(arguments)
        if (!debugArgs.isEmpty() && debugSpec.isEnabled) {
            LOGGER.warn("Debug configuration ignored in favor of the supplied JVM arguments: " + debugArgs)
            debugSpec.isEnabled = false
        }
    }

    /**
     * Adds extra JVM args and implicitly converts given arguments to just Strings
     */
    fun jvmArgs(arguments: Iterable<*>) {
        addExtraJvmArgs(arguments)
        checkDebugConfiguration(extraJvmArgs)
    }

    fun jvmArgs(vararg arguments: Any?) {
        jvmArgs(Arrays.asList<Any?>(*arguments))
    }

    private fun addExtraJvmArgs(arguments: Iterable<*>) {
        for (argument in arguments) {
            val argStr = argument.toString()

            if (argStr == "-ea" || argStr == "-enableassertions") {
                this.enableAssertions = true
            } else if (argStr == "-da" || argStr == "-disableassertions") {
                this.enableAssertions = false
            } else if (argStr.startsWith(XMS_PREFIX)) {
                minHeapSize = argStr.substring(XMS_PREFIX.length)
            } else if (argStr.startsWith(XMX_PREFIX)) {
                maxHeapSize = argStr.substring(XMX_PREFIX.length)
            } else if (argStr.startsWith(BOOTCLASSPATH_PREFIX)) {
                val bootClasspath = StringUtils.split(argStr.substring(BOOTCLASSPATH_PREFIX.length), File.pathSeparatorChar)
                setBootstrapClasspath(*(bootClasspath as kotlin.Array<kotlin.Any?>?)!!)
            } else if (argStr.startsWith("-D")) {
                val keyValue = argStr.substring(2)
                val equalsIndex = keyValue.indexOf("=")
                if (equalsIndex == -1) {
                    systemProperty(keyValue, "")
                } else {
                    systemProperty(keyValue.substring(0, equalsIndex), keyValue.substring(equalsIndex + 1))
                }
            } else {
                extraJvmArgs.add(argStr)
            }
        }
    }

    fun setSystemProperties(properties: MutableMap<String?, *>) {
        mutableSystemProperties.clear()
        systemProperties(properties)
    }

    fun systemProperties(properties: MutableMap<String?, *>) {
        for (entry in properties.entries) {
            systemProperty(entry.key, entry.value)
        }
    }

    fun systemProperty(name: String?, value: Any?) {
        if (IMMUTABLE_SYSTEM_PROPERTIES.contains(name)) {
            immutableSystemProperties.put(name, value)
        } else {
            mutableSystemProperties.put(name, value)
        }
    }

    fun getBootstrapClasspath(): FileCollection {
        return internalGetBootstrapClasspath()
    }

    private fun internalGetBootstrapClasspath(): ConfigurableFileCollection {
        if (bootstrapClasspath == null) {
            bootstrapClasspath = fileCollectionFactory.configurableFiles("bootstrap classpath")
        }
        return bootstrapClasspath!!
    }

    fun setBootstrapClasspath(classpath: FileCollection) {
        internalGetBootstrapClasspath().setFrom(classpath)
    }

    fun setBootstrapClasspath(vararg classpath: Any?) {
        internalGetBootstrapClasspath().setFrom(*classpath)
    }

    fun bootstrapClasspath(vararg classpath: Any?) {
        internalGetBootstrapClasspath().from(*classpath)
    }

    var defaultCharacterEncoding: String?
        get() = immutableSystemProperties.get(FILE_ENCODING_KEY).toString()
        set(defaultCharacterEncoding) {
            immutableSystemProperties.put(
                FILE_ENCODING_KEY,
                if (GUtil.isTrue(defaultCharacterEncoding)) defaultCharacterEncoding else Charset.defaultCharset().name()
            )
        }

    var debug: Boolean
        get() = debugSpec.isEnabled
        set(enabled) {
            debugSpec.isEnabled = enabled
        }

    fun copyTo(target: JavaForkOptions) {
        target.setJvmArgs(extraJvmArgs)
        target.setSystemProperties(mutableSystemProperties)
        target.setMinHeapSize(minHeapSize)
        target.setMaxHeapSize(maxHeapSize)
        target.bootstrapClasspath(getBootstrapClasspath().getFiles())
        target.setEnableAssertions(this.enableAssertions)
        copyDebugOptionsTo(JvmDebugSpec.JavaDebugOptionsBackedSpec(target.getDebugOptions()))
        target.systemProperties(immutableSystemProperties)
    }

    fun createCopy(fileCollectionFactory: FileCollectionFactory): JvmOptions {
        val target = JvmOptions(fileCollectionFactory)
        target.setExtraJvmArgs(extraJvmArgs)
        target.setSystemProperties(mutableSystemProperties)
        target.minHeapSize = minHeapSize
        target.maxHeapSize = maxHeapSize
        if (bootstrapClasspath != null) {
            target.setBootstrapClasspath(getBootstrapClasspath().getFiles())
        }
        target.enableAssertions = this.enableAssertions
        copyDebugOptionsTo(target.debugSpec)
        target.systemProperties(immutableSystemProperties)
        return target
    }

    private fun copyDebugOptionsTo(otherOptions: JvmDebugSpec) {
        copyDebugOptions(debugSpec, otherOptions)
    }

    companion object {
        private const val XMS_PREFIX = "-Xms"
        private const val XMX_PREFIX = "-Xmx"
        private const val BOOTCLASSPATH_PREFIX = "-Xbootclasspath:"

        const val FILE_ENCODING_KEY: String = "file.encoding"
        const val USER_LANGUAGE_KEY: String = "user.language"
        const val USER_COUNTRY_KEY: String = "user.country"
        const val USER_VARIANT_KEY: String = "user.variant"
        const val JMX_REMOTE_KEY: String = "com.sun.management.jmxremote"
        const val JAVA_IO_TMPDIR_KEY: String = "java.io.tmpdir"
        const val JAVA_SECURITY_PROPERTIES_KEY: String = "java.security.properties"
        const val JDK_ENABLE_ADS_KEY: String = "jdk.io.File.enableADS"

        const val SSL_KEYSTORE_KEY: String = "javax.net.ssl.keyStore"
        const val SSL_KEYSTOREPASSWORD_KEY: String = "javax.net.ssl.keyStorePassword"
        const val SSL_KEYSTORETYPE_KEY: String = "javax.net.ssl.keyStoreType"
        const val SSL_TRUSTSTORE_KEY: String = "javax.net.ssl.trustStore"
        const val SSL_TRUSTPASSWORD_KEY: String = "javax.net.ssl.trustStorePassword"
        const val SSL_TRUSTSTORETYPE_KEY: String = "javax.net.ssl.trustStoreType"

        private val LOGGER: Logger = LoggerFactory.getLogger(JvmOptions::class.java)

        val IMMUTABLE_SYSTEM_PROPERTIES: MutableCollection<String?> = Arrays.asList<String?>(
            FILE_ENCODING_KEY, USER_LANGUAGE_KEY, USER_COUNTRY_KEY, USER_VARIANT_KEY, JMX_REMOTE_KEY, JAVA_IO_TMPDIR_KEY, JAVA_SECURITY_PROPERTIES_KEY, JDK_ENABLE_ADS_KEY,
            SSL_KEYSTORE_KEY, SSL_KEYSTOREPASSWORD_KEY, SSL_KEYSTORETYPE_KEY, SSL_TRUSTPASSWORD_KEY, SSL_TRUSTSTORE_KEY, SSL_TRUSTSTORETYPE_KEY,  // Gradle specific
            HeapProportionalCacheSizer.CACHE_RESERVED_SYSTEM_PROPERTY
        )

        // Store this because Locale.default is mutable and we want the unchanged default
        // We are assuming this class will be initialized before any code has a chance to change the default
        private val DEFAULT_LOCALE: Locale = Locale.getDefault()

        fun getDebugArgument(options: JvmDebugSpec): String {
            val server = options.isServer
            val suspend = options.isSuspend
            val port = options.port
            val host = if (options.host == null) "" else options.host + ":"
            val address = host + port
            return getDebugArgument(server, suspend, address)
        }

        @JvmStatic
        fun getDebugArgument(server: Boolean, suspend: Boolean, address: String?): String {
            return "-agentlib:jdwp=transport=dt_socket," +
                    "server=" + (if (server) 'y' else 'n') +
                    ",suspend=" + (if (suspend) 'y' else 'n') +
                    ",address=" + address
        }

        private fun collectDebugArgs(arguments: Iterable<*>): MutableList<String?> {
            val debugArgs: MutableList<String?> = ArrayList<String?>()
            for (extraJvmArg in arguments) {
                val extraJvmArgString = extraJvmArg.toString()
                if (isDebugArg(extraJvmArgString)) {
                    debugArgs.add(extraJvmArgString)
                }
            }
            return debugArgs
        }

        private fun isDebugArg(extraJvmArgString: String): Boolean {
            return extraJvmArgString == "-Xdebug"
                    || extraJvmArgString.startsWith("-Xrunjdwp")
                    || extraJvmArgString.startsWith("-agentlib:jdwp")
        }

        private fun copyDebugOptions(from: JvmDebugSpec, to: JvmDebugSpec) {
            // This severs the connection between from this debugOptions to the other debugOptions
            to.isEnabled = from.isEnabled
            to.host = from.host
            to.port = from.port
            to.isServer = from.isServer
            to.isSuspend = from.isSuspend
        }

        fun fromString(input: String): MutableList<String> {
            return ArgumentsSplitter.split(input)
        }
    }
}
