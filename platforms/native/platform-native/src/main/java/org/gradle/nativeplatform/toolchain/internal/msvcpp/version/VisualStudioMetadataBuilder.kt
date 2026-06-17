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

import org.gradle.internal.FileUtils
import org.gradle.util.internal.VersionNumber
import java.io.File

class VisualStudioMetadataBuilder {
    private var installDir: File? = null
    private var visualCppDir: File? = null
    private var version: VersionNumber? = VersionNumber.UNKNOWN
    private var visualCppVersion: VersionNumber? = VersionNumber.UNKNOWN
    private var compatibility: VisualStudioInstallCandidate.Compatibility? = null

    fun installDir(installDir: File): VisualStudioMetadataBuilder {
        this.installDir = FileUtils.canonicalize(installDir)
        return this
    }

    fun visualCppDir(visualCppDir: File): VisualStudioMetadataBuilder {
        this.visualCppDir = FileUtils.canonicalize(visualCppDir)
        return this
    }

    fun version(version: VersionNumber?): VisualStudioMetadataBuilder {
        this.version = version
        return this
    }

    fun visualCppVersion(version: VersionNumber?): VisualStudioMetadataBuilder {
        this.visualCppVersion = version
        return this
    }

    fun compatibility(compatibility: VisualStudioInstallCandidate.Compatibility?): VisualStudioMetadataBuilder {
        this.compatibility = compatibility
        return this
    }

    fun build(): VisualStudioInstallCandidate {
        return DefaultVisualStudioMetadata(installDir, visualCppDir, version, visualCppVersion, compatibility)
    }
}
