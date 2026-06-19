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

import org.gradle.api.Describable
import java.io.File

class InstallationLocation private constructor(
    val location: File, val source: String?,
    /**
     * Flag for if this location was auto-detected, i.e. not explicitly defined by the user.
     *
     * This is used to lower the severity of issues related to this location.
     */
    val isAutoDetected: Boolean, val isAutoProvisioned: Boolean
) : Describable {
    override fun getDisplayName(): String {
        return "'" + location.getAbsolutePath() + "' (" + source + ")" + (if (this.isAutoDetected) " auto-detected" else "") + (if (this.isAutoProvisioned) " auto-provisioned" else "")
    }

    override fun toString(): String {
        return getDisplayName()
    }

    fun withLocation(location: File): InstallationLocation {
        return InstallationLocation(location, source, this.isAutoDetected, this.isAutoProvisioned)
    }

    companion object {
        @JvmStatic
        fun userDefined(location: File, source: String?): InstallationLocation {
            return InstallationLocation(location, source, false, false)
        }

        @JvmStatic
        fun autoDetected(location: File, source: String?): InstallationLocation {
            return InstallationLocation(location, source, true, false)
        }

        @JvmStatic
        fun autoProvisioned(location: File, source: String?): InstallationLocation {
            return InstallationLocation(location, source, true, true)
        }
    }
}
