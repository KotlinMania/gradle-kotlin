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

class DefaultJvmMemoryInfo : JvmMemoryInfo {
    /**
     * Max memory that this process can commit in bytes. Always returns the same value because maximum memory is determined at jvm start.
     */
    val maxMemory: Long //this does not change

    init {
        this.maxMemory = Runtime.getRuntime().maxMemory()
    }

    val committedMemory: Long
        /**
         * Currently committed memory of this process in bytes. May return different value depending on how the heap has expanded. The returned value is less than or equal to [.getMaxMemory]
         */
        get() =//querying runtime for each invocation
            Runtime.getRuntime().totalMemory()

    override fun getJvmSnapshot(): JvmMemoryStatus {
        return JvmMemoryStatusSnapshot(this.maxMemory, this.committedMemory)
    }
}
