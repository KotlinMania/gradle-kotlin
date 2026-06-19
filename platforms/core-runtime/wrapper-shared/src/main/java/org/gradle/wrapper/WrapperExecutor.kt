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
package org.gradle.wrapper

import org.gradle.util.internal.WrapperDistributionUrlConverter
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.Properties

class WrapperExecutor internal constructor(propertiesFile: File, private val properties: Properties) {
    private val propertiesFile: File?

    /**
     * Returns the configuration for this wrapper.
     */
    val configuration: WrapperConfiguration = WrapperConfiguration()

    init {
        this.propertiesFile = propertiesFile
        if (propertiesFile.exists()) {
            try {
                loadProperties(propertiesFile, properties)
                configuration.distribution = WrapperDistributionUrlConverter.convertDistributionUrl(readDistroUrl(), propertiesFile.getParentFile())
                configuration.distributionBase = getProperty(DISTRIBUTION_BASE_PROPERTY, configuration.distributionBase)
                configuration.distributionPath = getProperty(DISTRIBUTION_PATH_PROPERTY, configuration.distributionPath)
                configuration.distributionSha256Sum = getProperty(DISTRIBUTION_SHA_256_SUM, configuration.distributionSha256Sum, false)
                configuration.zipBase = getProperty(ZIP_STORE_BASE_PROPERTY, configuration.zipBase)
                configuration.zipPath = getProperty(ZIP_STORE_PATH_PROPERTY, configuration.zipPath)
                configuration.networkTimeout = getProperty(NETWORK_TIMEOUT_PROPERTY, configuration.networkTimeout)
                configuration.validateDistributionUrl = getProperty(VALIDATE_DISTRIBUTION_URL, configuration.validateDistributionUrl)
                configuration.retries = getProperty(RETRIES_PROPERTY, configuration.retries)
                configuration.retryBackOffMs = getProperty(RETRY_BACK_OFF_PROPERTY, configuration.retryBackOffMs)
            } catch (e: Exception) {
                throw RuntimeException(String.format("Could not load wrapper properties from '%s'.", propertiesFile), e)
            }
        }
    }

    private fun readDistroUrl(): String {
        if (properties.getProperty(DISTRIBUTION_URL_PROPERTY) == null) {
            reportMissingProperty(DISTRIBUTION_URL_PROPERTY)
        }
        return getProperty(DISTRIBUTION_URL_PROPERTY)!!
    }

    val distribution: URI?
        /**
         * Returns the distribution which this wrapper will use. Returns null if no wrapper meta-data was found in the specified project directory.
         */
        get() = configuration.distribution

    @Throws(Exception::class)
    fun execute(args: Array<String>, install: Install, bootstrapMainStarter: BootstrapMainStarter) {
        val gradleHome = install.createDist(this.configuration)
        bootstrapMainStarter.start(args, gradleHome)
    }

    private fun getProperty(propertyName: String?): String? {
        return getProperty(propertyName, null, true)
    }

    private fun getProperty(propertyName: String?, defaultValue: String): String {
        return getProperty(propertyName, defaultValue, true)!!
    }

    private fun getProperty(propertyName: String?, defaultValue: Int): Int {
        return getProperty(propertyName, defaultValue.toString())!!.toInt()
    }

    private fun getProperty(propertyName: String?, defaultValue: Boolean): Boolean {
        return getProperty(propertyName, defaultValue.toString()).toBoolean()
    }

    private fun getProperty(propertyName: String?, defaultValue: String?, required: Boolean): String? {
        val value = properties.getProperty(propertyName)
        if (value != null) {
            return value
        }
        if (defaultValue != null) {
            return defaultValue
        }
        if (required) {
            return reportMissingProperty(propertyName)
        } else {
            return null
        }
    }

    private fun reportMissingProperty(propertyName: String?): String? {
        throw RuntimeException(
            String.format(
                "No value with key '%s' specified in wrapper properties file '%s'.", propertyName, propertiesFile
            )
        )
    }

    companion object {
        const val DISTRIBUTION_URL_PROPERTY: String = "distributionUrl"
        const val DISTRIBUTION_BASE_PROPERTY: String = "distributionBase"
        const val DISTRIBUTION_PATH_PROPERTY: String = "distributionPath"
        const val DISTRIBUTION_SHA_256_SUM: String = "distributionSha256Sum"
        const val ZIP_STORE_BASE_PROPERTY: String = "zipStoreBase"
        const val ZIP_STORE_PATH_PROPERTY: String = "zipStorePath"
        const val NETWORK_TIMEOUT_PROPERTY: String = "networkTimeout"
        const val VALIDATE_DISTRIBUTION_URL: String = "validateDistributionUrl"
        const val RETRIES_PROPERTY: String = "retries"
        const val RETRY_BACK_OFF_PROPERTY: String = "retryBackOffMs"

        fun wrapperPropertiesForProjectDirectory(projectDir: File?): File {
            return File(projectDir, "gradle/wrapper/gradle-wrapper.properties")
        }

        @JvmStatic
        fun forProjectDirectory(projectDir: File?): WrapperExecutor {
            return WrapperExecutor(wrapperPropertiesForProjectDirectory(projectDir), Properties())
        }

        @JvmStatic
        fun forWrapperPropertiesFile(propertiesFile: File): WrapperExecutor {
            if (!propertiesFile.exists()) {
                throw RuntimeException(String.format("Wrapper properties file '%s' does not exist.", propertiesFile))
            }
            return WrapperExecutor(propertiesFile, Properties())
        }

        @Throws(IOException::class)
        private fun loadProperties(propertiesFile: File, properties: Properties) {
            val inStream: InputStream = FileInputStream(propertiesFile)
            try {
                properties.load(inStream)
            } finally {
                inStream.close()
            }
        }
    }
}
