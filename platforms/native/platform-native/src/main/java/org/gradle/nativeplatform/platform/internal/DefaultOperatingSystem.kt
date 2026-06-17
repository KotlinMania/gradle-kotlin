/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativeplatform.platform.internal

import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.internal.os.OperatingSystem.Companion.forName
import org.gradle.nativeplatform.OperatingSystemFamily

class DefaultOperatingSystem @JvmOverloads constructor(
    private val name: String, val internalOs: OperatingSystem = forName(
        name
    )
) : OperatingSystemInternal {
    override fun getName(): String {
        return name
    }

    val displayName: String?
        get() = "operating system '" + name + "'"

    override fun toString(): String {
        return displayName!!
    }

    override fun toFamilyName(): String? {
        if (this.isWindows) {
            return OperatingSystemFamily.WINDOWS
        } else if (this.isLinux) {
            return OperatingSystemFamily.LINUX
        } else if (this.isMacOsX) {
            return OperatingSystemFamily.MACOS
        } else {
            throw UnsupportedOperationException("Unsupported operating system family of name '" + name + "'")
        }
    }

    val isCurrent: Boolean
        get() = internalOs === CURRENT_OS

    val isWindows: Boolean
        get() = internalOs.isWindows

    val isLinux: Boolean
        get() = internalOs.isLinux

    val isMacOsX: Boolean
        get() = internalOs.isMacOsX

    val isSolaris: Boolean
        get() = internalOs === OperatingSystem.SOLARIS

    val isFreeBSD: Boolean
        get() = internalOs === OperatingSystem.FREE_BSD

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultOperatingSystem
        return name == that.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    companion object {
        private val CURRENT_OS = current()
    }
}
