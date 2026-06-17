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
package org.gradle.nativeplatform.toolchain.internal.msvcpp.version

import org.gradle.util.internal.VersionNumber
import java.io.File

class DefaultVisualStudioMetadata internal constructor(
    private val installDir: File?,
    private val visualCppDir: File?,
    private val version: VersionNumber,
    private val visualCppVersion: VersionNumber?,
    private val compatibility: VisualStudioInstallCandidate.Compatibility?
) : VisualStudioInstallCandidate {
    override fun getInstallDir(): File? {
        return installDir
    }

    override fun getVisualCppDir(): File? {
        return visualCppDir
    }

    override fun getVersion(): VersionNumber {
        return version
    }

    override fun getVisualCppVersion(): VersionNumber? {
        return visualCppVersion
    }

    override fun getCompatibility(): VisualStudioInstallCandidate.Compatibility {
        if (compatibility != null) {
            return compatibility
        } else {
            if (version.getMajor() >= 15) {
                return VisualStudioInstallCandidate.Compatibility.VS2017_OR_LATER
            } else {
                return VisualStudioInstallCandidate.Compatibility.LEGACY
            }
        }
    }
}
