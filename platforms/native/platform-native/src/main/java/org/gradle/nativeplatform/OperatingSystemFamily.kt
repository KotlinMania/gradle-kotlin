/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.nativeplatform

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.tasks.Input

/**
 * Represents the operating system of a configuration. Typical operating system include Windows, Linux, and macOS.
 * This interface allows the user to customize operating systems by implementing this interface.
 *
 * @since 5.1
 */
abstract class OperatingSystemFamily : Named {
    /**
     * {@inheritDoc}
     */
    @Input
    abstract override fun getName(): String?

    val isWindows: Boolean
        /**
         * Is this the Windows operating system family?
         *
         */
        get() = `is`(WINDOWS)

    val isLinux: Boolean
        /**
         * Is this the Linux operating system family?
         *
         */
        get() = `is`(LINUX)

    val isMacOs: Boolean
        /**
         * Is this the macOS operating system family?
         *
         */
        get() = `is`(MACOS)

    private fun `is`(osFamily: String?): Boolean {
        return getName() == osFamily
    }

    companion object {
        val OPERATING_SYSTEM_ATTRIBUTE: Attribute<OperatingSystemFamily?> = Attribute.of<OperatingSystemFamily?>("org.gradle.native.operatingSystem", OperatingSystemFamily::class.java)

        /**
         * The Windows operating system family.
         *
         */
        const val WINDOWS: String = "windows"

        /**
         * The Linux operating system family.
         *
         */
        const val LINUX: String = "linux"

        /**
         * The macOS operating system family.
         *
         */
        const val MACOS: String = "macos"
    }
}
