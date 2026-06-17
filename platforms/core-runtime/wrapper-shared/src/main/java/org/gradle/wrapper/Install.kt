/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.internal.file.PathTraversalChecker.safePathName
import org.gradle.internal.file.locking.ExclusiveFileAccessManager
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Formatter
import java.util.concurrent.Callable
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

class Install(private val logger: Logger, private val download: IDownload, private val pathAssembler: PathAssembler) {
    private val exclusiveFileAccessManager = ExclusiveFileAccessManager(120000, 200)

    @Throws(Exception::class)
    fun createDist(configuration: WrapperConfiguration): File? {
        val distributionUrl = configuration.getDistribution()

        val localDistribution = pathAssembler.getDistribution(configuration)
        val distDir = localDistribution.getDistributionDir()
        val localZipFile = localDistribution.getZipFile()

        return exclusiveFileAccessManager.access<File?>(localZipFile, Callable {
            val markerFile = File(localZipFile.getParentFile(), localZipFile.getName() + ".ok")
            if (distDir.isDirectory() && markerFile.isFile()) {
                val installCheck = verifyDistributionRoot(distDir, distDir.getAbsolutePath())
                if (installCheck.isVerified) {
                    return@access installCheck.gradleHome
                }
                // Distribution is invalid. Try to reinstall.
                System.err.println(installCheck.failureMessage)
                markerFile.delete()
            }

            fetchDistribution(localZipFile, distributionUrl, distDir, configuration)

            val installCheck = verifyDistributionRoot(distDir, Download.Companion.safeUri(distributionUrl).toASCIIString())
            if (installCheck.isVerified) {
                setExecutablePermissions(installCheck.gradleHome)
                markerFile.createNewFile()
                localZipFile.delete()
                return@access installCheck.gradleHome
            }
            // Distribution couldn't be installed.
            throw RuntimeException(installCheck.failureMessage)
        })
    }

    @Throws(Exception::class)
    private fun fetchDistribution(localZipFile: File, distributionUrl: URI, distDir: File, configuration: WrapperConfiguration) {
        var distributionSha256Sum = configuration.getDistributionSha256Sum()
        var failed = false
        var retries: Int = BROKEN_ZIP_RETRIES
        do {
            try {
                val needsDownload = !localZipFile.isFile() || failed
                if (needsDownload) {
                    forceFetch(localZipFile, distributionUrl, configuration.getRetries(), configuration.getRetryBackOffMs())
                }

                deleteLocalTopLevelDirs(distDir)

                verifyDownloadChecksum(configuration.getDistribution().toASCIIString(), localZipFile, distributionSha256Sum)

                unzipLocal(localZipFile, distDir)
                failed = false
            } catch (e: ZipException) {
                if (retries >= BROKEN_ZIP_RETRIES && distributionSha256Sum == null) {
                    distributionSha256Sum = fetchDistributionSha256Sum(configuration, localZipFile)
                }
                failed = true
                retries--
                if (retries <= 0) {
                    throw RuntimeException("Downloaded distribution file " + localZipFile + " is no valid zip file.")
                }
            }
        } while (failed)
    }

    private fun fetchDistributionSha256Sum(configuration: WrapperConfiguration, localZipFile: File): String? {
        val distribution = configuration.getDistribution()
        try {
            val distributionUrl = distribution.resolve(distribution.getPath() + SHA_256)
            val tmpZipFile = File(localZipFile.getParentFile(), localZipFile.getName() + SHA_256)

            forceFetch(tmpZipFile, distributionUrl, configuration.getRetries(), configuration.getRetryBackOffMs())

            BufferedReader(InputStreamReader(Files.newInputStream(tmpZipFile.toPath()), StandardCharsets.UTF_8)).use { reader ->
                return reader.readLine()
            }
        } catch (e: Exception) {
            logger.log("Could not fetch hash for " + Download.Companion.safeUri(distribution) + ".")
            logger.log("Reason: " + e.message)
            return null
        }
    }

    @Throws(IOException::class)
    private fun unzipLocal(localZipFile: File, distDir: File) {
        try {
            unzip(localZipFile, distDir)
        } catch (e: IOException) {
            logger.log("Could not unzip " + localZipFile.getAbsolutePath() + " to " + distDir.getAbsolutePath() + ".")
            logger.log("Reason: " + e.message)
            throw e
        }
    }

    private fun deleteLocalTopLevelDirs(distDir: File) {
        val topLevelDirs = listDirs(distDir)
        for (dir in topLevelDirs) {
            logger.log("Deleting directory " + dir.getAbsolutePath())
            deleteDir(dir)
        }
    }

    @Throws(Exception::class)
    private fun forceFetch(localTargetFile: File, distributionUrl: URI, networkRetries: Int, networkRetryBackOffMs: Int) {
        // negative retry parameter values will be handled as the defaults

        var networkRetries = networkRetries
        var networkRetryBackOffMs = networkRetryBackOffMs
        networkRetries = if (networkRetries >= 0) networkRetries else DEFAULT_NETWORK_RETRIES
        networkRetryBackOffMs = if (networkRetryBackOffMs >= 0) networkRetryBackOffMs else DEFAULT_NETWORK_RETRY_BACK_OFF_MS

        logger.log(
            String.format(
                "Fetching distribution%s.",
                if (networkRetries <= 0) "" else String.format(" (retrying %d times, with an initial back off of %d ms)", networkRetries, networkRetryBackOffMs)
            )
        )

        val attempts = networkRetries + 1
        var currentBackOffMs = networkRetryBackOffMs.toLong()
        var lastException: Exception? = null
        for (attempt in 1..attempts) {
            try {
                val tempDownloadFile = File(localTargetFile.getParentFile(), localTargetFile.getName() + ".part")
                tempDownloadFile.delete()

                logger.log("Downloading " + Download.Companion.safeUri(distributionUrl))
                download.download(distributionUrl, tempDownloadFile)
                if (localTargetFile.exists()) {
                    localTargetFile.delete()
                }
                tempDownloadFile.renameTo(localTargetFile)

                return
            } catch (ioException: IOException) {
                lastException = ioException

                logger.log(
                    String.format(
                        "Attempt %d/%d failed. Reason: %s",
                        attempt,
                        attempts,
                        ioException.message
                    )
                )

                if (attempt < attempts) {
                    Thread.sleep(currentBackOffMs)
                    currentBackOffMs *= 2
                }
            }
        }

        throw lastException
    }

    private fun verifyDistributionRoot(distDir: File, distributionDescription: String?): InstallCheck {
        val dirs = listDirs(distDir)
        if (dirs.isEmpty()) {
            return InstallCheck.Companion.failure(String.format("Gradle distribution '%s' does not contain any directories. Expected to find exactly 1 directory.", distributionDescription))
        }
        if (dirs.size != 1) {
            return InstallCheck.Companion.failure(String.format("Gradle distribution '%s' contains too many directories. Expected to find exactly 1 directory.", distributionDescription))
        }

        val gradleHome: File? = dirs.get(0)
        if (BootstrapMainStarter.Companion.findLauncherJar(gradleHome) == null) {
            return InstallCheck.Companion.failure(String.format("Gradle distribution '%s' does not appear to contain a Gradle distribution.", distributionDescription))
        }
        return InstallCheck.Companion.success(gradleHome)
    }

    @Throws(Exception::class)
    private fun verifyDownloadChecksum(sourceUrl: String?, localZipFile: File, expectedSum: String?) {
        if (expectedSum == null) {
            return
        }
        // if a SHA-256 hash sum has been defined in gradle-wrapper.properties, verify it here
        val actualSum: String = calculateSha256Sum(localZipFile)
        if (expectedSum == actualSum) {
            return
        }

        localZipFile.delete()
        val message = String.format(
            "Verification of Gradle distribution failed!%n" +
                    "%n" +
                    "Your Gradle distribution may have been tampered with.%n" +
                    "Confirm that the 'distributionSha256Sum' property in your gradle-wrapper.properties file is correct and you are downloading the wrapper from a trusted source.%n" +
                    "%n" +
                    "Distribution Url: %s%n" +
                    "Download Location: %s%n" +
                    "Expected checksum: '%s'%n" +
                    "Actual checksum:   '%s'%n" +
                    "Visit https://gradle.org/release-checksums/ to verify the checksums of official distributions. If your build uses a custom distribution, see with its provider.",
            sourceUrl, localZipFile.getAbsolutePath(), expectedSum, actualSum
        )
        throw RuntimeException(message)
    }

    private fun listDirs(distDir: File): MutableList<File> {
        if (!distDir.exists()) {
            return mutableListOf<File?>()
        }
        val files = distDir.listFiles()
        if (files == null) {
            return mutableListOf<File?>()
        }

        val dirs: MutableList<File> = ArrayList<File>()
        for (file in files) {
            if (file.isDirectory()) {
                dirs.add(file)
            }
        }
        return dirs
    }

    private fun setExecutablePermissions(gradleHome: File?) {
        if (this.isWindows) {
            return
        }
        val gradleCommand = File(gradleHome, "bin/gradle")
        var errorMessage: String? = null
        try {
            val pb = ProcessBuilder("chmod", "755", gradleCommand.getCanonicalPath())
            val p = pb.start()
            if (p.waitFor() != 0) {
                val `is` = BufferedReader(InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))
                val stdout = Formatter()
                var line: String?
                while ((`is`.readLine().also { line = it }) != null) {
                    stdout.format("%s%n", line)
                }
                errorMessage = stdout.toString()
            }
        } catch (e: IOException) {
            errorMessage = e.message
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            errorMessage = e.message
        }
        if (errorMessage != null) {
            logger.log("Could not set executable permissions for: " + gradleCommand.getAbsolutePath())
        }
    }

    private val isWindows: Boolean
        get() {
            val osName = System.getProperty("os.name").lowercase()
            return osName.contains("windows")
        }

    private fun deleteDir(dir: File): Boolean {
        if (dir.isDirectory()) {
            val children = dir.list()
            if (children != null) {
                for (child in children) {
                    val success = deleteDir(File(dir, child))
                    if (!success) {
                        return false
                    }
                }
            }
        }

        // The directory is now empty so delete it
        return dir.delete()
    }

    @Throws(IOException::class)
    private fun unzip(zip: File, dest: File?) {
        ZipFile(zip).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry: ZipEntry = entries.nextElement()

                val destFile = File(dest, safePathName(entry.getName()))
                if (entry.isDirectory()) {
                    destFile.mkdirs()
                    continue
                }

                BufferedOutputStream(Files.newOutputStream(destFile.toPath())).use { outputStream ->
                    copyInputStream(zipFile.getInputStream(entry), outputStream)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun copyInputStream(`in`: InputStream, out: OutputStream) {
        val buffer = ByteArray(1024)
        var len: Int

        while ((`in`.read(buffer).also { len = it }) >= 0) {
            out.write(buffer, 0, len)
        }

        `in`.close()
        out.close()
    }

    private class InstallCheck(private val gradleHome: File?, private val failureMessage: String?) {
        val isVerified: Boolean
            get() = gradleHome != null

        companion object {
            private fun failure(message: String?): InstallCheck {
                return InstallCheck(null, message)
            }

            private fun success(gradleHome: File?): InstallCheck {
                return InstallCheck(gradleHome, null)
            }
        }
    }

    companion object {
        const val DEFAULT_DISTRIBUTION_PATH: String = "wrapper/dists"
        const val SHA_256: String = ".sha256"

        const val DEFAULT_NETWORK_RETRIES: Int = 0
        const val DEFAULT_NETWORK_RETRY_BACK_OFF_MS: Int = 500

        private const val BROKEN_ZIP_RETRIES = 3

        @Throws(Exception::class)
        fun calculateSha256Sum(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(file.toPath()).use { fis ->
                var n = 0
                val buffer = ByteArray(4096)
                while (n != -1) {
                    n = fis.read(buffer)
                    if (n > 0) {
                        md.update(buffer, 0, n)
                    }
                }
            }
            val byteData = md.digest()
            val hexString = StringBuilder()
            for (byteDatum in byteData) {
                val hex = Integer.toHexString(0xff and byteDatum.toInt())
                if (hex.length == 1) {
                    hexString.append('0')
                }
                hexString.append(hex)
            }

            return hexString.toString()
        }
    }
}
