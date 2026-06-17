/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.internal.resource.transfer

import com.google.common.io.Files
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.ExternalResourceCachePolicy
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.cache.internal.ProducerGuard
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.cached.CachedExternalResource
import org.gradle.internal.resource.cached.CachedExternalResourceIndex
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.internal.resource.local.LocallyAvailableResource
import org.gradle.internal.resource.local.LocallyAvailableResourceCandidates
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.internal.resource.metadata.ExternalResourceMetaDataCompare
import org.gradle.util.internal.BuildCommencedTimeProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.function.Supplier

class DefaultCacheAwareExternalResourceAccessor(
    private val delegate: ExternalResourceRepository,
    private val cachedExternalResourceIndex: CachedExternalResourceIndex<String>,
    private val timeProvider: BuildCommencedTimeProvider,
    private val temporaryFileProvider: TemporaryFileProvider,
    private val cacheAccessCoordinator: ArtifactCacheLockingAccessCoordinator,
    private val externalResourceCachePolicy: ExternalResourceCachePolicy,
    private val producerGuard: ProducerGuard<ExternalResourceName>,
    private val fileResourceRepository: FileResourceRepository,
    private val checksumService: ChecksumService
) : CacheAwareExternalResourceAccessor {
    override fun getResource(
        location: ExternalResourceName,
        baseName: String?,
        fileStore: CacheAwareExternalResourceAccessor.ResourceFileStore,
        additionalCandidates: LocallyAvailableResourceCandidates?
    ): LocallyAvailableExternalResource? {
        return producerGuard.guardByKey<LocallyAvailableExternalResource?>(location, Supplier {
            LOGGER.debug("Constructing external resource: {}", location)
            val cached = cachedExternalResourceIndex.lookup(location.toString())

            // If we have no caching options, just get the thing directly
            if (cached == null && (additionalCandidates == null || additionalCandidates.isNone())) {
                return@guardByKey copyToCache(location, fileStore, delegate.withProgressLogging().resource(location))
            }

            // We might be able to use a cached/locally available version
            if (cached != null && !externalResourceCachePolicy.mustRefreshExternalResource(getAgeMillis(timeProvider, cached))) {
                return@guardByKey fileResourceRepository.resource(cached.getCachedFile(), location.getUri(), cached.getExternalResourceMetaData())
            }

            // We have a cached version, but it might be out of date, so we tell the upstreams to revalidate too
            val revalidate = true

            // Get the metadata first to see if it's there
            val remoteMetaData = delegate.resource(location, revalidate).getMetaData()
            if (remoteMetaData == null) {
                return@guardByKey null
            }

            // Is the cached version still current?
            if (cached != null) {
                val isUnchanged = ExternalResourceMetaDataCompare.isDefinitelyUnchanged(
                    cached.getExternalResourceMetaData(),
                    org.gradle.internal.Factory { remoteMetaData }
                )

                if (isUnchanged) {
                    LOGGER.info("Cached resource {} is up-to-date (lastModified: {}).", location, cached.getExternalLastModified())
                    // Update the cache entry in the index: this resets the age of the cached entry to zero
                    cachedExternalResourceIndex.store(location.toString(), cached.getCachedFile(), cached.getExternalResourceMetaData())
                    return@guardByKey fileResourceRepository.resource(cached.getCachedFile(), location.getUri(), cached.getExternalResourceMetaData())
                }
            }

            // Either no cached, or it's changed. See if we can find something local with the same checksum
            val hasLocalCandidates = additionalCandidates != null && !additionalCandidates.isNone()
            if (hasLocalCandidates) {
                // The "remote" may have already given us the checksum
                var remoteChecksum = remoteMetaData.getSha1()

                if (remoteChecksum == null) {
                    remoteChecksum = getResourceSha1(location, revalidate)
                }

                if (remoteChecksum != null) {
                    val local = additionalCandidates.findByHashValue(remoteChecksum)
                    if (local != null) {
                        LOGGER.info("Found locally available resource with matching checksum: [{}, {}]", location, local.getFile())
                        // TODO - should iterate over each candidate until we successfully copy into the cache
                        val resource: LocallyAvailableExternalResource?
                        try {
                            resource = copyCandidateToCache(location, fileStore, remoteMetaData, remoteChecksum, local)
                        } catch (e: IOException) {
                            throw throwAsUncheckedException(e)
                        }
                        if (resource != null) {
                            return@guardByKey resource
                        }
                    }
                }
            }
            copyToCache(location, fileStore, delegate.withProgressLogging().resource(location, revalidate))
        })
    }

    private fun getResourceSha1(location: ExternalResourceName, revalidate: Boolean): HashCode? {
        try {
            val sha1Location = location.append(".sha1")
            val resource = delegate.resource(sha1Location, revalidate)
            val result = resource.withContentIfPresent<HashCode>(ExternalResource.ContentAction { inputStream: InputStream? ->
                var sha = IOUtils.toString(inputStream, StandardCharsets.US_ASCII)
                // Servers may return SHA-1 with leading zeros stripped
                sha = StringUtils.leftPad(sha, Hashing.sha1().getHexDigits(), '0')
                HashCode.fromString(sha)
            })
            return if (result == null) null else result.getResult()
        } catch (e: Exception) {
            LOGGER.debug(String.format("Failed to download SHA1 for resource '%s'.", location), e)
            return null
        }
    }

    @Throws(IOException::class)
    private fun copyCandidateToCache(
        source: ExternalResourceName,
        fileStore: CacheAwareExternalResourceAccessor.ResourceFileStore,
        remoteMetaData: ExternalResourceMetaData,
        remoteChecksum: HashCode,
        local: LocallyAvailableResource
    ): LocallyAvailableExternalResource? {
        val destination = temporaryFileProvider.createTemporaryFile("gradle_download", "bin")
        try {
            Files.copy(local.getFile(), destination)
            val localChecksum = checksumService.sha1(destination)
            if (localChecksum != remoteChecksum) {
                return null
            }
            return moveIntoCache(source, destination, fileStore, remoteMetaData)
        } finally {
            destination.delete()
        }
    }

    private fun copyToCache(source: ExternalResourceName, fileStore: CacheAwareExternalResourceAccessor.ResourceFileStore, resource: ExternalResource): LocallyAvailableExternalResource? {
        // Download to temporary location
        val downloadAction = DownloadAction(source, temporaryFileProvider, LOGGER)
        resource.withContentIfPresent<Any>(downloadAction)
        if (downloadAction.getMetaData() == null) {
            return null
        }

        // Move into cache
        try {
            return moveIntoCache(source, downloadAction.getDestination(), fileStore, downloadAction.getMetaData()!!)
        } finally {
            downloadAction.getDestination().delete()
        }
    }

    private fun moveIntoCache(
        source: ExternalResourceName,
        destination: File,
        fileStore: CacheAwareExternalResourceAccessor.ResourceFileStore,
        metaData: ExternalResourceMetaData
    ): LocallyAvailableExternalResource {
        return cacheAccessCoordinator.useCache<LocallyAvailableExternalResource>(Supplier {
            val cachedResource = fileStore.moveIntoCache(destination)
            val fileInFileStore = cachedResource.getFile()
            cachedExternalResourceIndex.store(source.toString(), fileInFileStore, metaData)
            fileResourceRepository.resource(fileInFileStore, source.getUri(), metaData)
        })
    }

    private fun getAgeMillis(timeProvider: BuildCommencedTimeProvider, cached: CachedExternalResource): Long {
        return timeProvider.getCurrentTime() - cached.getCachedAt()
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultCacheAwareExternalResourceAccessor::class.java)
    }
}
