/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.internal.resource.transport

import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.ExternalResourceCachePolicy
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.cache.internal.ProducerGuard
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.cached.CachedExternalResourceIndex
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor
import org.gradle.internal.resource.transfer.DefaultCacheAwareExternalResourceAccessor
import org.gradle.internal.resource.transfer.DefaultExternalResourceRepository
import org.gradle.internal.resource.transfer.ExternalResourceConnector
import org.gradle.internal.resource.transfer.ProgressLoggingExternalResourceAccessor
import org.gradle.internal.resource.transfer.ProgressLoggingExternalResourceLister
import org.gradle.internal.resource.transfer.ProgressLoggingExternalResourceUploader
import org.gradle.util.internal.BuildCommencedTimeProvider

class ResourceConnectorRepositoryTransport(
    name: String?,
    temporaryFileProvider: TemporaryFileProvider,
    cachedExternalResourceIndex: CachedExternalResourceIndex<String?>,
    timeProvider: BuildCommencedTimeProvider,
    cacheAccessCoordinator: ArtifactCacheLockingAccessCoordinator,
    connector: ExternalResourceConnector,
    buildOperationRunner: BuildOperationRunner,
    cachePolicy: ExternalResourceCachePolicy,
    producerGuard: ProducerGuard<ExternalResourceName?>,
    fileResourceRepository: FileResourceRepository,
    checksumService: ChecksumService
) : AbstractRepositoryTransport(name) {
    private val repository: ExternalResourceRepository
    private val resourceAccessor: DefaultCacheAwareExternalResourceAccessor

    init {
        val loggingUploader = ProgressLoggingExternalResourceUploader(connector, buildOperationRunner)
        val loggingAccessor = ProgressLoggingExternalResourceAccessor(connector, buildOperationRunner)
        val loggingLister = ProgressLoggingExternalResourceLister(connector, buildOperationRunner)
        repository = DefaultExternalResourceRepository(name, loggingAccessor, loggingUploader, loggingLister)
        resourceAccessor = DefaultCacheAwareExternalResourceAccessor(
            repository,
            cachedExternalResourceIndex,
            timeProvider,
            temporaryFileProvider,
            cacheAccessCoordinator,
            cachePolicy,
            producerGuard,
            fileResourceRepository,
            checksumService
        )
    }

    override fun getRepository(): ExternalResourceRepository {
        return repository
    }

    override fun getResourceAccessor(): CacheAwareExternalResourceAccessor {
        return resourceAccessor
    }

    override fun isLocal(): Boolean {
        return false
    }
}
