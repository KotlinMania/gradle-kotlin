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
import org.gradle.internal.deprecation.Documentation.Companion.userManual
import org.gradle.internal.exceptions.Contextual
import org.gradle.internal.exceptions.ResolutionProvider
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.jvm.toolchain.JavaToolchainSpec
import java.util.Arrays

@Contextual
class ToolchainProvisioningException(
    specification: JavaToolchainSpec,
    cause: String,
    vararg resolutions: String
) : GradleException(
    String.format(
        "Cannot find a Java installation on your machine (%s) matching: %s. %s",
        current(),
        specification,
        cause
    )
), ResolutionProvider {
    private val resolutions: MutableList<String?>

    init {
        this.resolutions = Arrays.asList<String?>(*resolutions)
    }

    override fun getResolutions(): MutableList<String?> {
        return resolutions
    }

    companion object {
        @JvmField
        val AUTO_DETECTION_RESOLUTION: String = "Learn more about toolchain auto-detection and auto-provisioning at " + userManual("toolchains", "sec:auto_detection").getUrl() + "."
        val DOWNLOAD_REPOSITORIES_RESOLUTION: String = "Learn more about toolchain repositories at " + userManual("toolchains", "sub:download_repositories").getUrl() + "."
    }
}
