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

import com.google.common.annotations.VisibleForTesting
import com.google.common.io.Files
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.regex.Matcher
import java.util.regex.Pattern

class MemInfoOsMemoryInfo : OsMemoryInfo {
    private val meminfoMatcher: Matcher

    init {
        // Initialize Matchers once and then reset them for performance
        meminfoMatcher = MEMINFO_LINE_PATTERN.matcher("")
    }

    @Synchronized
    override fun getOsSnapshot(): OsMemoryStatus {
        // NOTE: meminfoMatcher is _not_ thread safe and access needs to be limited to a single thread.
        val meminfoOutputLines: MutableList<String>?
        try {
            meminfoOutputLines = Files.readLines(File(MEMINFO_FILE_PATH), Charset.defaultCharset())
        } catch (e: IOException) {
            throw UnsupportedOperationException("Unable to read system memory from " + MEMINFO_FILE_PATH, e)
        }

        return getOsSnapshotFromMemInfo(meminfoOutputLines)
    }


    /**
     * Given output from /proc/meminfo, return a system memory snapshot.
     */
    @VisibleForTesting
    fun getOsSnapshotFromMemInfo(meminfoLines: MutableList<String>): OsMemoryStatusSnapshot {
        val meminfo = Meminfo()

        for (line in meminfoLines) {
            if (line.startsWith("MemAvailable")) {
                meminfo.available = parseMeminfoBytes(line)
            } else if (line.startsWith("MemFree")) {
                meminfo.setFree(parseMeminfoBytes(line))
            } else if (line.startsWith("Buffers")) {
                meminfo.setBuffers(parseMeminfoBytes(line))
            } else if (line.startsWith("Cached")) {
                meminfo.setCached(parseMeminfoBytes(line))
            } else if (line.startsWith("SReclaimable")) {
                meminfo.setReclaimable(parseMeminfoBytes(line))
            } else if (line.startsWith("Mapped")) {
                meminfo.setMapped(parseMeminfoBytes(line))
            } else if (line.startsWith("MemTotal")) {
                meminfo.total = parseMeminfoBytes(line)
            }
        }

        if (meminfo.available < 0 || meminfo.total < 0) {
            throw UnsupportedOperationException("Unable to read system memory from " + MEMINFO_FILE_PATH)
        }

        return OsMemoryStatusSnapshot(meminfo.total, meminfo.available)
    }

    /**
     * Given a line from /proc/meminfo, return number value representing number of bytes.
     *
     * @param line String line from /proc/meminfo. Example: "MemAvailable:    2109560 kB"
     * @return number from value transformed to bytes or -1 if unparsable. Example: 2_160_189_440
     */
    private fun parseMeminfoBytes(line: String?): Long {
        val matcher = meminfoMatcher.reset(line)
        if (matcher.matches()) {
            return matcher.group(1).toLong() * 1024
        }
        throw UnsupportedOperationException("Unable to parse /proc/meminfo output to get system memory")
    }

    private class Meminfo {
        var total: Long = -1
        var available: Long = -1
            /*
                     * Linux 4.x: MemAvailable
                     * Linux 3.x: MemFree + Buffers + Cached + SReclaimable - Mapped
                     */
            get() {
                if (field != -1L) {
                    return field
                }
                if (free != -1L && buffers != -1L && cached != -1L && reclaimable != -1L && mapped != -1L) {
                    return free + buffers + cached + reclaimable - mapped
                }
                return -1
            }
        private var free: Long = -1
        private var buffers: Long = -1
        private var cached: Long = -1
        private var reclaimable: Long = -1
        private var mapped: Long = -1

        fun setFree(memFree: Long) {
            this.free = memFree
        }

        fun setBuffers(buffers: Long) {
            this.buffers = buffers
        }

        fun setCached(cached: Long) {
            this.cached = cached
        }

        fun setReclaimable(reclaimable: Long) {
            this.reclaimable = reclaimable
        }

        fun setMapped(mapped: Long) {
            this.mapped = mapped
        }
    }

    companion object {
        // /proc/meminfo is in kB since Linux 4.0, see https://git.kernel.org/cgit/linux/kernel/git/torvalds/linux.git/tree/fs/proc/task_mmu.c?id=39a8804455fb23f09157341d3ba7db6d7ae6ee76#n22
        private val MEMINFO_LINE_PATTERN: Pattern = Pattern.compile("^\\D+(\\d+) kB$")
        private const val MEMINFO_FILE_PATH = "/proc/meminfo"
    }
}
