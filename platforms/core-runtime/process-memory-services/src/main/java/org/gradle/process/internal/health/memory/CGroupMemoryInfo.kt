/*
 * Copyright 2022 the original author or authors.
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
import com.google.common.io.Files
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import kotlin.math.max

class CGroupMemoryInfo : OsMemoryInfo {
    override fun getOsSnapshot(): OsMemoryStatus {
        val cg2Usage: File = File(CG2_MEM_USAGE_FILE)
        val cg2Total: File = File(CG2_MEM_TOTAL_FILE)
        if (cg2Usage.exists() && cg2Total.exists()) {
            return getOsSnapshotFromCgroup(
                readStringFromFile(cg2Usage)!!,
                readStringFromFile(cg2Total)!!
            )
        }
        return getOsSnapshotFromCgroup(
            readStringFromFile(File(CG1_MEM_USAGE_FILE))!!,
            readStringFromFile(File(CG1_MEM_TOTAL_FILE))!!
        )
    }

    @VisibleForTesting
    fun getOsSnapshotFromCgroup(memUsageString: String, memTotalString: String): OsMemoryStatusSnapshot {
        val memUsage: Long
        val memTotal: Long
        val memAvailable: Long

        try {
            memUsage = memUsageString.toLong()
            // cgroup v2 unlimited case where memory.max == "max" is also covered by this
            memTotal = memTotalString.toLong()
            memAvailable = max(0, memTotal - memUsage)
        } catch (e: NumberFormatException) {
            throw UnsupportedOperationException("Unable to read system memory", e)
        }

        return OsMemoryStatusSnapshot(memTotal, memAvailable)
    }

    companion object {
        private const val CG1_MEM_USAGE_FILE = "/sys/fs/cgroup/memory/memory.usage_in_bytes"
        private const val CG1_MEM_TOTAL_FILE = "/sys/fs/cgroup/memory/memory.limit_in_bytes"
        private const val CG2_MEM_USAGE_FILE = "/sys/fs/cgroup/memory.current"
        private const val CG2_MEM_TOTAL_FILE = "/sys/fs/cgroup/memory.max"

        private fun readStringFromFile(file: File): String? {
            try {
                return Files.asCharSource(file, Charset.defaultCharset()).readFirstLine()
            } catch (e: IOException) {
                throw UnsupportedOperationException("Unable to read system memory from " + file.getAbsoluteFile(), e)
            }
        }
    }
}
