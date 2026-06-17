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

import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope


@ServiceScope(Scope.BuildSession::class)
class SystemPathVersionLocator(private val os: OperatingSystem, private val versionDeterminer: VisualStudioMetaDataProvider) : VisualStudioVersionLocator {
    override fun getVisualStudioInstalls(): MutableList<VisualStudioInstallCandidate?> {
        val installs: MutableList<VisualStudioInstallCandidate?> = ArrayList<VisualStudioInstallCandidate?>()

        val compilerInPath = os.findInPath(LEGACY_COMPILER_FILENAME)
        if (compilerInPath == null) {
            LOGGER!!.debug("No visual c++ compiler found in system path.")
        } else {
            val install = versionDeterminer.getVisualStudioMetadataFromCompiler(compilerInPath)
            if (install != null) {
                installs.add(install)
            }
        }

        return installs
    }

    override fun getSource(): String {
        return "system path"
    }

    companion object {
        private const val LEGACY_COMPILER_FILENAME = "cl.exe"

        private val LOGGER = getLogger(SystemPathVersionLocator::class.java)
    }
}
