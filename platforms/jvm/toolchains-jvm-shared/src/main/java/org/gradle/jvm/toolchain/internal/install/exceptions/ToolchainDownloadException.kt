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
package org.gradle.jvm.toolchain.internal.install.exceptions

import org.gradle.api.GradleException
import org.gradle.internal.exceptions.Contextual
import org.gradle.internal.exceptions.ResolutionProvider
import org.gradle.jvm.toolchain.JavaToolchainSpec
import java.net.URI
import java.util.Arrays

@Contextual
class ToolchainDownloadException : GradleException, ResolutionProvider {
    val resolutions: MutableList<String>

    constructor(spec: JavaToolchainSpec, url: String, cause: String?) : super(getMessage(spec, url, cause)) {
        this.resolutions = Arrays.asList<String>(ToolchainProvisioningException.Companion.AUTO_DETECTION_RESOLUTION, ToolchainProvisioningException.Companion.DOWNLOAD_REPOSITORIES_RESOLUTION)
    }

    constructor(spec: JavaToolchainSpec, uri: URI, cause: Throwable) : super(getMessage(spec, uri.toString(), cause.message), cause) {
        resolutions = mutableListOf<String>()
    }

    companion object {
        private fun getMessage(spec: JavaToolchainSpec, url: String, cause: String?): String {
            return "Unable to download toolchain matching the requirements (" + spec.getDisplayName() + ") from '" + url + "'" + (if (cause != null && !cause.isEmpty()) ", due to: " + cause else ".")
        }
    }
}
