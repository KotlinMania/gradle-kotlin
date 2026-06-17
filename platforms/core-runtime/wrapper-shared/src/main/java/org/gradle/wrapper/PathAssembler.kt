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

import java.io.File
import java.math.BigInteger
import java.net.URI
import java.security.MessageDigest

class PathAssembler(private val gradleUserHome: File?, private val projectDirectory: File?) {
    /**
     * Determines the local locations for the distribution to use given the supplied configuration.
     */
    fun getDistribution(configuration: WrapperConfiguration): LocalDistribution {
        val baseName = getDistName(configuration.getDistribution())
        val distName = removeExtension(baseName)
        val rootDirName = rootDirName(distName, configuration)
        val distDir = File(getBaseDir(configuration.getDistributionBase()), configuration.getDistributionPath() + "/" + rootDirName)
        val distZip = File(getBaseDir(configuration.getZipBase()), configuration.getZipPath() + "/" + rootDirName + "/" + baseName)
        return LocalDistribution(distDir, distZip)
    }

    private fun rootDirName(distName: String?, configuration: WrapperConfiguration): String {
        val urlHash = getHash(Download.Companion.safeUri(configuration.getDistribution()).toASCIIString())
        return distName + "/" + urlHash
    }

    /**
     * This method computes a hash of the provided `string`.
     *
     *
     * The algorithm in use by this method is as follows:
     *
     *  1. Compute the MD5 value of the UTF-8 `string`.
     *  1. Truncate leading zeros (i.e., treat the MD5 value as a number).
     *  1. Convert to base 36 (the characters `0-9a-z`).
     *
     */
    private fun getHash(string: String): String {
        try {
            val messageDigest = MessageDigest.getInstance("MD5")
            val bytes = string.toByteArray(charset("UTF-8"))
            messageDigest.update(bytes)
            return BigInteger(1, messageDigest.digest()).toString(36)
        } catch (e: Exception) {
            throw RuntimeException("Could not hash input string.", e)
        }
    }

    private fun removeExtension(name: String): String {
        val p = name.lastIndexOf(".")
        if (p < 0) {
            return name
        }
        return name.substring(0, p)
    }

    private fun getDistName(distUrl: URI): String {
        val path = distUrl.getPath()
        val p = path.lastIndexOf("/")
        if (p < 0) {
            return path
        }
        return path.substring(p + 1)
    }

    private fun getBaseDir(base: String): File? {
        if (base == GRADLE_USER_HOME_STRING) {
            return gradleUserHome
        } else if (base == PROJECT_STRING) {
            return projectDirectory
        } else {
            throw RuntimeException("Base: " + base + " is unknown")
        }
    }

    class LocalDistribution(
        /**
         * Returns the location to install the distribution into.
         */
        val distributionDir: File?,
        /**
         * Returns the location to install the distribution ZIP file to.
         */
        val zipFile: File?
    )

    companion object {
        const val GRADLE_USER_HOME_STRING: String = "GRADLE_USER_HOME"
        const val PROJECT_STRING: String = "PROJECT"
    }
}
