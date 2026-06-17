/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.jvm.toolchain.internal.install

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.authentication.Authentication
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.internal.resource.ExternalResource
import org.gradle.jvm.toolchain.JavaToolchainDownload
import org.gradle.jvm.toolchain.JavaToolchainResolverRegistry
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainRequest
import org.gradle.jvm.toolchain.internal.JavaToolchainResolverRegistryInternal
import org.gradle.jvm.toolchain.internal.JdkCacheDirectory
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration
import org.gradle.jvm.toolchain.internal.install.exceptions.ToolchainDownloadException
import org.gradle.jvm.toolchain.internal.install.exceptions.ToolchainProvisioningException
import org.gradle.platform.internal.CurrentBuildPlatform
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.Optional
import java.util.TreeMap
import java.util.concurrent.Callable
import java.util.stream.Collectors
import javax.inject.Inject

class DefaultJavaToolchainProvisioningService @Inject constructor(
    toolchainResolverRegistry: JavaToolchainResolverRegistry,
    private val downloader: SecureFileDownloader,
    cacheDirProvider: JdkCacheDirectory,
    providerFactory: ProviderFactory,
    toolchainConfiguration: ToolchainConfiguration,
    private val buildOperationRunner: BuildOperationRunner,
    private val currentBuildPlatform: CurrentBuildPlatform
) : JavaToolchainProvisioningService {
    private val toolchainResolverRegistry: JavaToolchainResolverRegistryInternal
    private val cacheDirProvider: DefaultJdkCacheDirectory
    private val downloadEnabled: Provider<Boolean>

    init {
        this.toolchainResolverRegistry = toolchainResolverRegistry as JavaToolchainResolverRegistryInternal
        this.cacheDirProvider = cacheDirProvider as DefaultJdkCacheDirectory
        this.downloadEnabled = providerFactory.provider<Boolean>(toolchainConfiguration::isDownloadEnabled)
    }

    override fun isAutoDownloadEnabled(): Boolean {
        return downloadEnabled.get()
    }

    override fun hasConfiguredToolchainRepositories(): Boolean {
        return !toolchainResolverRegistry.requestedRepositories().isEmpty()
    }

    override fun tryInstall(spec: JavaToolchainSpec): File {
        if (!isAutoDownloadEnabled()) {
            throw ToolchainProvisioningException(
                spec, "Toolchain auto-provisioning is not enabled.",
                ToolchainProvisioningException.AUTO_DETECTION_RESOLUTION
            )
        }

        val repositories = toolchainResolverRegistry.requestedRepositories()
        if (repositories.isEmpty()) {
            throw ToolchainProvisioningException(
                spec, "Toolchain download repositories have not been configured.",
                ToolchainProvisioningException.AUTO_DETECTION_RESOLUTION,
                ToolchainProvisioningException.DOWNLOAD_REPOSITORIES_RESOLUTION
            )
        }

        // TODO: This should be refactored to leverage the new JavaToolchainResolverService but the current error handling makes it hard
        // However, this exception handling is wrong as it may cause unreproducible behaviors since we can query a later resolver when a previous one fails.
        val downloadFailureTracker = ToolchainDownloadFailureTracker()
        var successfulProvisioning: File? = null
        for (repository in repositories) {
            val resolver = repository.getResolver()
            val download: Optional<JavaToolchainDownload>?
            try {
                download = resolver.resolve(DefaultJavaToolchainRequest(spec, currentBuildPlatform.toBuildPlatform()))
            } catch (e: Exception) {
                downloadFailureTracker.addResolveFailure(repository.getRepositoryName(), e)
                continue
            }
            try {
                if (download.isPresent()) {
                    val authentications = repository.getAuthentications(download.get().uri)
                    successfulProvisioning = provisionInstallation(spec, download.get().uri, authentications)
                    break
                }
            } catch (e: Exception) {
                downloadFailureTracker.addProvisioningFailure(repository.getRepositoryName(), e)
                // continue
            }
        }

        if (successfulProvisioning == null) {
            throw downloadFailureTracker.buildFailureException(spec)
        } else {
            downloadFailureTracker.logFailuresIfAny()
            return successfulProvisioning
        }
    }

    private fun provisionInstallation(spec: JavaToolchainSpec, uri: URI, authentications: MutableCollection<Authentication>): File {
        synchronized(PROVISIONING_PROCESS_LOCK) {
            try {
                val downloadFolder = cacheDirProvider.getDownloadLocation()
                val resource = wrapInOperation<ExternalResource>("Examining toolchain URI " + uri, java.util.concurrent.Callable { downloader.getResourceFor(uri, authentications) })!!
                val archiveFile = File(downloadFolder, buildFileNameWithDetails(uri, resource, spec))
                val fileLock = cacheDirProvider.acquireWriteLock(archiveFile, "Downloading toolchain")
                val archiveAlreadyExists = archiveFile.exists()
                try {
                    if (!archiveAlreadyExists) {
                        wrapInOperation<Any>("Downloading toolchain from URI " + uri, Callable {
                            downloader.download(uri, archiveFile, resource)
                            null
                        })
                    }
                    try {
                        return wrapInOperation<File>(
                            "Unpacking toolchain archive " + archiveFile.getName(),
                            java.util.concurrent.Callable { cacheDirProvider.provisionFromArchive(spec, archiveFile, uri) })!!
                    } catch (e: Exception) {
                        if (archiveAlreadyExists) { // re-download and retry in case the archive is corrupted
                            LOGGER.info("Re-downloading toolchain from URI {} because unpacking the existing archive {} failed with an exception", uri, archiveFile.getName(), e)
                            wrapInOperation<Any>("Re-downloading toolchain from URI " + uri, Callable {
                                downloader.download(uri, archiveFile, resource)
                                null
                            })
                            return wrapInOperation<File>(
                                "Unpacking toolchain archive " + archiveFile.getName(),
                                java.util.concurrent.Callable { cacheDirProvider.provisionFromArchive(spec, archiveFile, uri) })!!
                        } else {
                            throw e
                        }
                    }
                } finally {
                    fileLock.close()
                }
            } catch (e: Exception) {
                throw ToolchainDownloadException(spec, uri, e)
            }
        }
    }

    private fun <T> wrapInOperation(displayName: String, provisioningStep: Callable<T?>): T? {
        return buildOperationRunner.call<T?>(ToolchainProvisioningBuildOperation<T?>(displayName, provisioningStep))
    }

    private class ToolchainProvisioningBuildOperation<T>(private val displayName: String, private val provisioningStep: Callable<T?>) : CallableBuildOperation<T?> {
        @Throws(Exception::class)
        override fun call(context: BuildOperationContext): T? {
            return provisioningStep.call()
        }

        override fun description(): BuildOperationDescriptor.Builder {
            return BuildOperationDescriptor
                .displayName(displayName)
                .progressDisplayName(displayName)
        }
    }

    private class ToolchainDownloadFailureTracker {
        private val resolveFailures: MutableMap<String, Exception> = TreeMap<String, Exception>()
        private val provisioningFailures: MutableMap<String, Exception> = TreeMap<String, Exception>()

        fun addResolveFailure(repositoryName: String, failure: Exception) {
            resolveFailures.put(repositoryName, failure)
        }

        fun addProvisioningFailure(repositoryName: String, failure: Exception) {
            provisioningFailures.put(repositoryName, failure)
        }

        fun buildFailureException(spec: JavaToolchainSpec): ToolchainProvisioningException {
            var cause = "No matching toolchain could be found in the configured toolchain download repositories."
            if (hasFailures()) {
                cause = failureMessage()
            }

            val resolutions = arrayOf<String>(
                ToolchainProvisioningException.AUTO_DETECTION_RESOLUTION,
                ToolchainProvisioningException.DOWNLOAD_REPOSITORIES_RESOLUTION
            )
            val exception = ToolchainProvisioningException(spec, cause, *resolutions)

            return addFailuresAsSuppressed<ToolchainProvisioningException>(exception)!!
        }

        fun <T : Exception?> addFailuresAsSuppressed(exception: T?): T? {
            for (resolveFailure in resolveFailures.values) {
                exception!!.addSuppressed(resolveFailure)
            }

            for (provisionFailure in provisioningFailures.values) {
                exception!!.addSuppressed(provisionFailure)
            }

            return exception
        }

        fun logFailuresIfAny() {
            if (hasFailures()) {
                LOGGER.warn(failureMessage() + " Switch logging level to DEBUG (--debug) for further information.")
                if (LOGGER.isDebugEnabled()) {
                    val failureMessage = failureMessage()
                    LOGGER.debug(failureMessage, addFailuresAsSuppressed<Exception>(Exception(failureMessage)))
                }
            }
        }

        fun hasFailures(): Boolean {
            return !resolveFailures.isEmpty() || !provisioningFailures.isEmpty()
        }

        fun failureMessage(): String {
            val sb = StringBuilder()
            if (!resolveFailures.isEmpty()) {
                sb.append("Some toolchain resolvers had internal failures: ")
                    .append(failureMessage(resolveFailures))
                    .append(".")
            }
            if (!provisioningFailures.isEmpty()) {
                sb.append(if (resolveFailures.isEmpty()) "" else " ")
                sb.append("Some toolchain resolvers had provisioning failures: ")
                    .append(failureMessage(provisioningFailures))
                    .append(".")
            }
            return sb.toString()
        }

        companion object {
            private fun failureMessage(failures: MutableMap<String, Exception>): String {
                return failures.entries.stream().map<String> { e: MutableMap.MutableEntry<String?, Exception?>? -> e!!.key + " (" + e.value!!.message + ")" }.collect(Collectors.joining(", "))
            }
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultJavaToolchainProvisioningService::class.java)
        private val PROVISIONING_PROCESS_LOCK = Any()
    }
}
