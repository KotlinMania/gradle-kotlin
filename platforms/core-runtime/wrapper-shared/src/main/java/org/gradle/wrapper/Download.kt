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

import org.gradle.util.internal.WrapperCredentials
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.PasswordAuthentication
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.net.URLConnection
import java.util.Properties
import java.util.function.Function
import kotlin.math.max
import kotlin.math.min

class Download @JvmOverloads constructor(
    private val logger: Logger,
    progressListener: DownloadProgressListener?,
    private val appName: String?,
    private val appVersion: String?,
    private val systemProperties: MutableMap<String?, String?>,
    private val networkTimeout: Int = DEFAULT_NETWORK_TIMEOUT_MILLISECONDS
) : IDownload {
    private val progressListener: DownloadProgressListener

    constructor(logger: Logger, appName: String?, appVersion: String?) : this(logger, null, appName, appVersion, convertSystemProperties(System.getProperties()))

    constructor(logger: Logger, appName: String?, appVersion: String?, networkTimeout: Int) : this(logger, null, appName, appVersion, convertSystemProperties(System.getProperties()), networkTimeout)

    init {
        this.progressListener = DefaultDownloadProgressListener(logger, progressListener)
        configureProxyAuthentication()
    }

    private fun configureProxyAuthentication() {
        if (systemProperties.get("http.proxyUser") != null || systemProperties.get("https.proxyUser") != null) {
            // Only an authenticator for proxies needs to be set. Basic authentication is supported by directly setting the request header field.
            Authenticator.setDefault(ProxyAuthenticator(systemProperties))
        }
    }

    @Throws(Exception::class)
    fun sendHeadRequest(uri: URI) {
        val safeUrl: URL = safeUri(uri).toURL()
        var responseCode = -1
        try {
            val conn = safeUrl.openConnection() as HttpURLConnection
            conn.setRequestMethod("HEAD")
            addAuthentication(uri, conn)
            conn.setRequestProperty("User-Agent", calculateUserAgent())
            conn.setConnectTimeout(networkTimeout)
            conn.setReadTimeout(networkTimeout)
            conn.connect()
            responseCode = conn.getResponseCode()
            if (responseCode != 200) {
                throw RuntimeException("HEAD request to " + safeUrl + " failed: response code (" + responseCode + ")")
            }
        } catch (e: IOException) {
            throw RuntimeException("HEAD request to " + safeUrl + " failed: response code (" + responseCode + "), timeout (" + networkTimeout + "ms)", e)
        }
    }

    @Throws(Exception::class)
    override fun download(address: URI, destination: File) {
        destination.getParentFile().mkdirs()
        downloadInternal(address, destination)
    }

    @Throws(Exception::class)
    private fun downloadInternal(address: URI, destination: File) {
        var out: OutputStream? = null
        val conn: URLConnection
        var `in`: InputStream? = null
        val safeUrl: URL = safeUri(address).toURL()
        try {
            out = BufferedOutputStream(FileOutputStream(destination))

            // No proxy is passed here as proxies are set globally using the HTTP(S) proxy system properties. The respective protocol handler implementation then makes use of these properties.
            conn = safeUrl.openConnection()

            addAuthentication(address, conn)
            val userAgentValue = calculateUserAgent()
            conn.setRequestProperty("User-Agent", userAgentValue)
            conn.setConnectTimeout(networkTimeout)
            conn.setReadTimeout(networkTimeout)

            // Check HTTP response code before downloading
            if (conn is HttpURLConnection) {
                val httpConn = conn
                val responseCode = httpConn.getResponseCode()
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Server returned HTTP response code: " + responseCode + " for URL: " + safeUrl)
                }
            }

            `in` = conn.getInputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var numRead: Int
            val totalLength = conn.getContentLength()
            var downloadedLength: Long = 0
            var progressCounter: Long = 0
            while ((`in`.read(buffer).also { numRead = it }) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw IOException("Download was interrupted.")
                }

                downloadedLength += numRead.toLong()
                progressCounter += numRead.toLong()

                if (progressCounter / PROGRESS_CHUNK > 0 || downloadedLength == totalLength.toLong()) {
                    progressCounter = progressCounter - PROGRESS_CHUNK
                    progressListener.downloadStatusChanged(address, totalLength.toLong(), downloadedLength)
                }

                out.write(buffer, 0, numRead)
            }
        } catch (e: SocketTimeoutException) {
            throw IOException("Downloading from " + safeUrl + " failed: timeout (" + networkTimeout + "ms)", e)
        } finally {
            logger.log("")
            if (`in` != null) {
                `in`.close()
            }
            if (out != null) {
                out.close()
            }
        }
    }

    @Throws(IOException::class)
    private fun addAuthentication(address: URI, connection: URLConnection) {
        val credentials: WrapperCredentials? = WrapperCredentials.Companion.findCredentials(address, Function { key: String? -> systemProperties.get(key) })
        if (credentials == null) {
            return
        }

        if ("https" != address.getScheme()) {
            logger.log("WARNING Using HTTP " + credentials.authorizationTypeDisplayName() + " Authentication over an insecure connection to download the Gradle distribution. Please consider using HTTPS.")
        }

        val authHeader: MutableMap.MutableEntry<String?, String?> = credentials.authorizationHeader()
        connection.setRequestProperty(authHeader.key, authHeader.value)
    }

    private fun calculateUserAgent(): String {
        val javaVendor = systemProperties.get("java.vendor")
        val javaVersion = systemProperties.get("java.version")
        val javaVendorVersion = systemProperties.get("java.vm.version")
        val osName = systemProperties.get("os.name")
        val osVersion = systemProperties.get("os.version")
        val osArch = systemProperties.get("os.arch")
        return String.format("%s/%s (%s;%s;%s) (%s;%s;%s)", appName, appVersion, osName, osVersion, osArch, javaVendor, javaVersion, javaVendorVersion)
    }

    private class ProxyAuthenticator(private val systemProperties: MutableMap<String?, String?>) : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication? {
            if (getRequestorType() == RequestorType.PROXY) {
                // Note: Do not use getRequestingProtocol() here, which is "http" even for HTTPS proxies.
                val protocol = getRequestingURL().getProtocol()
                val proxyUser = systemProperties.get(protocol + ".proxyUser")
                if (proxyUser != null) {
                    var proxyPassword = systemProperties.get(protocol + ".proxyPassword")
                    if (proxyPassword == null) {
                        proxyPassword = ""
                    }
                    return PasswordAuthentication(proxyUser, proxyPassword.toCharArray())
                }
            }

            return super.getPasswordAuthentication()
        }
    }

    private class DefaultDownloadProgressListener(private val logger: Logger, private val delegate: DownloadProgressListener?) : DownloadProgressListener {
        private var previousDownloadPercent = 0

        override fun downloadStatusChanged(address: URI?, contentLength: Long, downloaded: Long) {
            // If the total size of distribution is known, but there's no advanced progress listener, provide extra progress information
            if (contentLength > 0 && delegate == null) {
                appendPercentageSoFar(contentLength, downloaded)
            }

            if (contentLength != downloaded) {
                logger.append(".")
            }

            if (delegate != null) {
                delegate.downloadStatusChanged(address, contentLength, downloaded)
            }
        }

        fun appendPercentageSoFar(contentLength: Long, downloaded: Long) {
            try {
                val currentDownloadPercent = 10 * (calculateDownloadPercent(contentLength, downloaded) / 10)
                if (currentDownloadPercent != 0 && previousDownloadPercent != currentDownloadPercent) {
                    logger.append(currentDownloadPercent.toString()).append('%')
                    previousDownloadPercent = currentDownloadPercent
                }
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }

        fun calculateDownloadPercent(totalLength: Long, downloadedLength: Long): Int {
            return min(100, max(0, ((downloadedLength / totalLength.toDouble()) * 100).toInt()))
        }
    }

    companion object {
        const val UNKNOWN_VERSION: String = "0"
        val DEFAULT_NETWORK_TIMEOUT_MILLISECONDS: Int = 10 * 1000

        private val BUFFER_SIZE = 10 * 1024
        private val PROGRESS_CHUNK = 1024 * 1024
        private fun convertSystemProperties(properties: Properties): MutableMap<String?, String?> {
            val result: MutableMap<String?, String?> = HashMap<String?, String?>()
            for (entry in properties.entries) {
                result.put(entry.key.toString(), if (entry.value == null) null else entry.value.toString())
            }
            return result
        }

        /**
         * Create a safe URI from the given one by stripping out user info.
         *
         * @param uri Original URI
         * @return a new URI with no user info
         */
        fun safeUri(uri: URI): URI {
            try {
                return URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment())
            } catch (e: URISyntaxException) {
                throw RuntimeException("Failed to parse URI", e)
            }
        }
    }
}
