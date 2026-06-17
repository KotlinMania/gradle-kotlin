/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.jvm.toolchain.internal

import org.gradle.jvm.toolchain.JavaToolchainDownload
import org.gradle.jvm.toolchain.JavaToolchainRequest
import org.gradle.jvm.toolchain.internal.install.exceptions.ToolchainProvisioningException
import java.util.Optional
import javax.inject.Inject

class DefaultJavaToolchainResolverService @Inject constructor(private val toolchainResolverRegistry: JavaToolchainResolverRegistryInternal) : JavaToolchainResolverService {
    override fun tryResolve(request: JavaToolchainRequest): Optional<JavaToolchainDownload> {
        val repositories = toolchainResolverRegistry.requestedRepositories()
        if (repositories.isEmpty()) {
            throw ToolchainProvisioningException(
                request.javaToolchainSpec, "Toolchain download repositories have not been configured.",
                ToolchainProvisioningException.AUTO_DETECTION_RESOLUTION,
                ToolchainProvisioningException.DOWNLOAD_REPOSITORIES_RESOLUTION
            )
        }
        for (repository in repositories) {
            val resolver = repository.getResolver()
            val download = resolver.resolve(request)

            if (download.isPresent()) {
                return download
            }
        }
        return Optional.empty<JavaToolchainDownload>()
    }

    override fun hasConfiguredToolchainRepositories(): Boolean {
        return !toolchainResolverRegistry.requestedRepositories().isEmpty()
    }
}
