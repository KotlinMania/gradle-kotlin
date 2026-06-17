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
package org.gradle.jvm.toolchain.internal

import com.google.common.annotations.VisibleForTesting
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.os.OperatingSystem.Companion.current
import java.io.File
import java.util.Arrays
import java.util.function.BiFunction
import java.util.stream.Collectors
import javax.inject.Inject

class LinuxInstallationSupplier @VisibleForTesting internal constructor(os: OperatingSystem, vararg roots: File?) : InstallationSupplier {
    @VisibleForTesting
    val roots: Array<File?>
    private val os: OperatingSystem

    @Inject
    constructor() : this(current()!!, File("/usr/lib/jvm"), File("/usr/lib64/jvm"), File("/usr/java"), File("/usr/local/java"), File("/opt/java"))

    init {
        this.roots = roots
        this.os = os
    }

    override fun getSourceName(): String {
        return "Common Linux Locations"
    }

    override fun get(): MutableSet<InstallationLocation?> {
        if (os.isLinux) {
            return Arrays.stream<File?>(roots)
                .map<MutableSet<InstallationLocation?>?> { root: File? ->
                    FileBasedInstallationFactory.fromDirectory(
                        root,
                        getSourceName(),
                        BiFunction { location: File?, source: String? -> InstallationLocation.Companion.autoDetected(location, source) })
                }
                .flatMap<InstallationLocation?> { obj: MutableSet<InstallationLocation?>? -> obj!!.stream() }
                .collect(Collectors.toSet())
        }
        return mutableSetOf<InstallationLocation?>()
    }
}
