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
import org.gradle.internal.jvm.Jvm

/**
 * Helper to compute maximum heap sizes.
 */
open class MaximumHeapHelper {
    /**
     * Get the default maximum heap.
     *
     * Different JVMs on different systems may use a different default for maximum heap when unset.
     * This method implements a best effort approximation, omitting rules for low memory systems (&lt;192MB total RAM).
     *
     * See [Oracle](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/parallel.html#default_heap_size)
     * and [IBM](http://www.ibm.com/support/knowledgecenter/SSYKE2_8.0.0/com.ibm.java.lnx.80.doc/diag/appendixes/defaults.html)
     * corresponding documentation.
     *
     * @param osTotalMemory OS total memory in bytes
     * @return Default maximum heap size for the current JVM
     */
    fun getDefaultMaximumHeapSize(osTotalMemory: Long): Long {
        if (this.isIbmJvm) {
            val totalMemoryHalf = osTotalMemory / 2
            val halfGB: Long = MemoryAmount.Companion.parseNotation("512m")
            return if (totalMemoryHalf > halfGB) halfGB else totalMemoryHalf
        }

        val totalMemoryFourth = osTotalMemory / 4
        val oneGB: Long = MemoryAmount.Companion.parseNotation("1g")
        when (this.jvmBitMode) {
            32 -> return if (totalMemoryFourth > oneGB) oneGB else totalMemoryFourth
            64 -> {
                if (this.isServerJvm) {
                    val thirtyTwoGB: Long = MemoryAmount.Companion.parseNotation("32g")
                    return if (totalMemoryFourth > thirtyTwoGB) thirtyTwoGB else totalMemoryFourth
                }
                return if (totalMemoryFourth > oneGB) oneGB else totalMemoryFourth
            }

            else -> {
                if (this.isServerJvm) {
                    val thirtyTwoGB: Long = MemoryAmount.Companion.parseNotation("32g")
                    return if (totalMemoryFourth > thirtyTwoGB) thirtyTwoGB else totalMemoryFourth
                }
                return if (totalMemoryFourth > oneGB) oneGB else totalMemoryFourth
            }
        }
    }

    @get:VisibleForTesting
    open val isIbmJvm: Boolean
        get() = Jvm.current().isIbmJvm()

    @get:VisibleForTesting
    open val jvmBitMode: Int
        get() {
            for (property in mutableListOf<String?>("sun.arch.data.model", "com.ibm.vm.bitmode", "os.arch")) {
                if (System.getProperty(property, "").contains("64")) {
                    return 64
                }
            }
            return 32
        }

    @get:VisibleForTesting
    open val isServerJvm: Boolean
        get() = !System.getProperty("java.vm.name").lowercase().contains("client")
}
