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

class OsMemoryStatusSnapshot private constructor(private val physicalMemory: OsMemoryStatusAspect.Available, private val virtualMemory: OsMemoryStatusAspect) : OsMemoryStatus {
    /**
     * Create a new snapshot with unknown virtual memory.
     *
     * @param totalPhysicalMemory total physical memory in bytes
     * @param freePhysicalMemory free physical memory in bytes
     */
    constructor(totalPhysicalMemory: Long, freePhysicalMemory: Long) : this(
        DefaultAvailableOsMemoryStatusAspect("physical", totalPhysicalMemory, freePhysicalMemory),
        DefaultUnavailableOsMemoryStatusAspect("virtual")
    )

    /**
     * Create a new snapshot with limited virtual memory.
     *
     * @param totalPhysicalMemory total physical memory in bytes
     * @param freePhysicalMemory free physical memory in bytes
     * @param totalVirtualMemory total virtual memory in bytes
     * @param freeVirtualMemory free virtual memory in bytes
     */
    constructor(
        totalPhysicalMemory: Long, freePhysicalMemory: Long, totalVirtualMemory: Long, freeVirtualMemory: Long
    ) : this(
        DefaultAvailableOsMemoryStatusAspect("physical", totalPhysicalMemory, freePhysicalMemory),
        DefaultAvailableOsMemoryStatusAspect("virtual", totalVirtualMemory, freeVirtualMemory)
    )

    override fun getPhysicalMemory(): OsMemoryStatusAspect.Available {
        return physicalMemory
    }

    override fun getVirtualMemory(): OsMemoryStatusAspect {
        return virtualMemory
    }

    override fun toString(): String {
        return "OS memory {" + physicalMemory + ", " + virtualMemory + '}'
    }
}
