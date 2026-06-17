/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.jvm.inspection

import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.internal.InstallationLocation
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.function.Predicate

class CachingJvmMetadataDetector(private val delegate: JvmMetadataDetector) : JvmMetadataDetector, ConditionalInvalidation<JvmInstallationMetadata?> {
    private val javaMetadata: MutableMap<File?, JvmInstallationMetadata?> = Collections.synchronizedMap<File?, JvmInstallationMetadata?>(HashMap<File?, JvmInstallationMetadata?>())

    init {
        getMetadata(InstallationLocation.Companion.autoDetected(Jvm.current().getJavaHome(), "current Java home"))
    }

    override fun getMetadata(javaInstallationLocation: InstallationLocation): JvmInstallationMetadata? {
        val javaHome = resolveSymlink(javaInstallationLocation.getLocation())
        return javaMetadata.computeIfAbsent(javaHome) { file: File? -> delegate.getMetadata(javaInstallationLocation) }
    }

    private fun resolveSymlink(jdkPath: File): File {
        try {
            return jdkPath.getCanonicalFile()
        } catch (e: IOException) {
            return jdkPath
        }
    }

    override fun invalidateItemsMatching(predicate: Predicate<JvmInstallationMetadata?>) {
        synchronized(javaMetadata) {
            javaMetadata.entries.removeIf { it: MutableMap.MutableEntry<File?, JvmInstallationMetadata?>? -> predicate.test(it!!.value) }
        }
    }
}
