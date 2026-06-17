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

import org.gradle.internal.jvm.Jvm
import java.io.File

class CurrentInstallationSupplier : InstallationSupplier {
    override fun getSourceName(): String {
        return "Current JVM"
    }

    override fun get(): MutableSet<InstallationLocation?> {
        return mutableSetOf<InstallationLocation?>(this.installation)
    }

    val installation: InstallationLocation
        get() = asInstallation(Jvm.current().getJavaHome())

    private fun asInstallation(javaHome: File?): InstallationLocation {
        return InstallationLocation.Companion.autoDetected(javaHome, getSourceName())
    }
}
