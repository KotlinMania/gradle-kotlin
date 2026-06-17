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
package org.gradle.process.internal.health.memory

import com.google.common.annotations.VisibleForTesting
import net.rubygrapefruit.platform.NativeException
import net.rubygrapefruit.platform.memory.Memory
import net.rubygrapefruit.platform.memory.MemoryInfo
import net.rubygrapefruit.platform.memory.WindowsMemoryInfo
import org.gradle.internal.nativeintegration.NativeIntegrationException
import org.gradle.internal.nativeintegration.services.NativeServices.Companion.getInstance
import org.jspecify.annotations.NullMarked

@NullMarked
class WindowsOsMemoryInfo : OsMemoryInfo {
    override fun getOsSnapshot(): OsMemoryStatus {
        try {
            val memory = getInstance().get<Memory>(Memory::class.java)
            return snapshotFromMemoryInfo(memory.getMemoryInfo())
        } catch (ex: NativeException) {
            throw UnsupportedOperationException("Unable to get system memory", ex)
        } catch (ex: NativeIntegrationException) {
            throw UnsupportedOperationException("Unable to get system memory", ex)
        }
    }

    companion object {
        @VisibleForTesting
        fun snapshotFromMemoryInfo(memoryInfo: MemoryInfo): OsMemoryStatus {
            if (memoryInfo is WindowsMemoryInfo) {
                val windowsMemoryInfo = memoryInfo
                return OsMemoryStatusSnapshot(
                    memoryInfo.getTotalPhysicalMemory(),
                    memoryInfo.getAvailablePhysicalMemory(),  // Note: the commit limit is usually less than the hard limit of the commit peak, but I think it would be prudent
                    // for us to not force the user's OS to allocate more page file space, so we'll use the commit limit here.
                    windowsMemoryInfo.getCommitLimit(),
                    availableCommitMemory(windowsMemoryInfo)
                )
            } else {
                return OsMemoryStatusSnapshot(
                    memoryInfo.getTotalPhysicalMemory(), memoryInfo.getAvailablePhysicalMemory()
                )
            }
        }

        private fun availableCommitMemory(windowsMemoryInfo: WindowsMemoryInfo): Long {
            return windowsMemoryInfo.getCommitLimit() - windowsMemoryInfo.getCommitTotal()
        }
    }
}
