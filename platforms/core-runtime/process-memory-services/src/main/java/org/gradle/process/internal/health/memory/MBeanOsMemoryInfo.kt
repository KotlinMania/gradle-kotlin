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

import org.gradle.internal.jvm.Jvm

/**
 * Uses the JVM's own interpretation of total and free memory. Gives accurate values on Solaris.
 * On Linux or MacOs, the free memory metric doesn't include reclaimable file system caches and will thus always report that the system is almost out of memory.
 * Use [MemInfoOsMemoryInfo] and [NativeOsMemoryInfo] instead.
 * On Windows, this doesn't include the secondary commit limit, which is the maximum amount of virtual memory that can be allocated.
 * Use [WindowsOsMemoryInfo] instead.
 */
class MBeanOsMemoryInfo(private val mBeanAttributeProvider: MBeanAttributeProvider) : OsMemoryInfo {
    override fun getOsSnapshot(): OsMemoryStatus {
        val totalMemoryAttribute = if (Jvm.current().isIbmJvm()) "TotalPhysicalMemory" else "TotalPhysicalMemorySize"
        val total: Long = mBeanAttributeProvider.getMbeanAttribute("java.lang:type=OperatingSystem", totalMemoryAttribute, Long::class.javaObjectType)
        val free: Long = mBeanAttributeProvider.getMbeanAttribute("java.lang:type=OperatingSystem", "FreePhysicalMemorySize", Long::class.javaObjectType)
        if (total == -1L) {
            throw UnsupportedOperationException("Unable to retrieve total physical memory from MBean")
        }
        if (free == -1L) {
            throw UnsupportedOperationException("Unable to retrieve free physical memory from MBean")
        }
        return OsMemoryStatusSnapshot(total, free)
    }
}
