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
package org.gradle.internal.resource.transport.gcp.gcs

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.storage.Storage
import com.google.api.services.storage.model.Objects
import com.google.api.services.storage.model.StorageObject
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import org.gradle.api.resources.ResourceException
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.resource.ResourceExceptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.io.UncheckedIOException
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLDecoder
import java.security.GeneralSecurityException

class GcsClient @VisibleForTesting internal constructor(private val storage: Storage) {
    @Throws(ResourceException::class)
    fun put(inputStream: InputStream, contentLength: Long, destination: URI) {
        try {
            val contentStream = InputStreamContent(null, inputStream)
            // Setting the length improves upload performance
            contentStream.setLength(contentLength)

            // TODO - set ACL here if necessary
            val bucket = destination.getHost()
            val path: String = cleanResourcePath(destination)
            val objectMetadata = StorageObject().setName(path)

            val putRequest = storage.objects().insert(bucket, objectMetadata, contentStream)

            LOGGER.debug("Attempting to put resource:[{}] into gcs bucket [{}]", putRequest.getName(), putRequest.getBucket())
            putRequest.execute()
        } catch (e: IOException) {
            throw ResourceExceptions.putFailed(destination, e)
        }
    }

    @Throws(ResourceException::class)
    fun getResource(uri: URI): StorageObject? {
        LOGGER.debug("Attempting to get gcs resource: [{}]", uri.toString())

        val path: String = cleanResourcePath(uri)
        try {
            val getRequest = storage.objects().get(uri.getHost(), path)
            return getRequest.execute()
        } catch (e: GoogleJsonResponseException) {
            // When an artifact is being published it is first checked whether it is available.
            // If a transport returns `null` then it is assumed that artifact does not exist.
            // If we throw, an attempt to publish will fail altogether even if we use ResourceExceptions#getMissing(uri).
            if (e.getStatusCode() == 404) {
                return null
            }
            throw ResourceExceptions.getFailed(uri, e)
        } catch (e: IOException) {
            throw ResourceExceptions.getFailed(uri, e)
        }
    }

    @VisibleForTesting
    @Throws(IOException::class)
    fun getResourceStream(uri: URI): InputStream? {
        val path: String = cleanResourcePath(uri)
        val getObject = storage.objects().get(uri.getHost(), path)
        getObject.getMediaHttpDownloader().setDirectDownloadEnabled(false)
        return getObject.executeMediaAsInputStream()
    }

    @Throws(ResourceException::class)
    fun list(uri: URI): MutableList<String?>? {
        val results: MutableList<StorageObject> = ArrayList<StorageObject>()

        val path: String = cleanResourcePath(uri)
        try {
            val listRequest = storage.objects().list(uri.getHost()).setPrefix(path)
            var objects: Objects

            // Iterate through each page of results, and add them to our results list.
            do {
                objects = listRequest.execute()
                // Add the items in this page of results to the list we'll return.
                // GCS API will return null on an empty list.
                if (objects.getItems() != null) {
                    results.addAll(objects.getItems())
                }

                // Get the next page, in the next iteration of this loop.
                listRequest.setPageToken(objects.getNextPageToken())
            } while (null != objects.getNextPageToken())
        } catch (e: IOException) {
            throw ResourceExceptions.getFailed(uri, e)
        }

        val resultStrings: MutableList<String?> = ArrayList<String?>()
        for (result in results) {
            resultStrings.add(result.getName())
        }

        return resultStrings
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(GcsClient::class.java)

        @Throws(GeneralSecurityException::class, IOException::class)
        fun create(gcsConnectionProperties: GcsConnectionProperties): GcsClient {
            val transport: HttpTransport = GoogleNetHttpTransport.newTrustedTransport()
            val jsonFactory: JsonFactory = GsonFactory()
            val builder = Storage.Builder(transport, jsonFactory, null)
            if (gcsConnectionProperties.requiresAuthentication()) {
                val credentialSupplier: Supplier<Credential?> = getCredentialSupplier(transport, jsonFactory)
                builder.setHttpRequestInitializer(RetryHttpInitializerWrapper(credentialSupplier))
            }
            if (gcsConnectionProperties.getEndpoint().isPresent()) {
                builder.setRootUrl(gcsConnectionProperties.getEndpoint().get().toString())
            }
            if (gcsConnectionProperties.getServicePath().isPresent()) {
                builder.setServicePath(gcsConnectionProperties.getServicePath().get())
            }
            builder.setApplicationName("gradle")
            return GcsClient(builder.build())
        }

        private fun cleanResourcePath(uri: URI): String {
            var path: String
            try {
                path = URLDecoder.decode(uri.getPath(), "UTF-8")
            } catch (e: UnsupportedEncodingException) {
                throw throwAsUncheckedException(e) // fail fast, this should not happen
            }
            while (path.startsWith("/")) {
                path = path.substring(1)
            }
            return path
        }

        private fun getCredentialSupplier(transport: HttpTransport, jsonFactory: JsonFactory): Supplier<Credential?> {
            return Suppliers.memoize<Credential?>(object : Supplier<Credential?> {
                @Suppress("deprecation")
                override fun get(): Credential? {
                    try {
                        val googleCredential = GoogleCredential.getApplicationDefault(transport, jsonFactory)
                        // Ensure we have a scope
                        return googleCredential.createScoped(mutableListOf<String?>("https://www.googleapis.com/auth/devstorage.read_write"))
                    } catch (e: IOException) {
                        throw UncheckedIOException("Failed to get Google credentials for GCS connection", e)
                    }
                }
            })
        }
    }
}
