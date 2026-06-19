/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.process.internal.health.memory

import org.gradle.internal.os.OperatingSystem

class DefaultOsMemoryInfo : OsMemoryInfo {
    private val delegate: OsMemoryInfo

    init {
        val operatingSystem = OperatingSystem.current()
        if (operatingSystem.isMacOsX) {
            delegate = NativeOsMemoryInfo()
        } else if (operatingSystem.isLinux) {
            delegate = this.linuxDelegate
        } else if (operatingSystem.isWindows) {
            delegate = WindowsOsMemoryInfo()
        } else {
            delegate = MBeanOsMemoryInfo(DefaultMBeanAttributeProvider())
        }
    }

    private val linuxDelegate: OsMemoryInfo
        get() {
            val cGroupDelegate: OsMemoryInfo = CGroupMemoryInfo()
            val memInfoDelegate: OsMemoryInfo = MemInfoOsMemoryInfo()

            val cGroupSnapshot: OsMemoryStatus
            val memInfoSnapshot: OsMemoryStatus

            try {
                cGroupSnapshot = cGroupDelegate.getOsSnapshot()
            } catch (e: UnsupportedOperationException) {
                return memInfoDelegate
            }

            try {
                memInfoSnapshot = memInfoDelegate.getOsSnapshot()
            } catch (e: UnsupportedOperationException) {
                return cGroupDelegate
            }

            val cGroupFreeMemory = cGroupSnapshot.getPhysicalMemory().getFree()
            val memInfoFreeMemory = memInfoSnapshot.getPhysicalMemory().getFree()

            return if (cGroupFreeMemory > memInfoFreeMemory) memInfoDelegate else cGroupDelegate
        }

    override fun getOsSnapshot(): OsMemoryStatus {
        return delegate.getOsSnapshot()
    }
}
