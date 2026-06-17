/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.file

import java.text.MessageFormat
import java.util.concurrent.atomic.AtomicLong

interface StatStatistics {
    /**
     * Number of times [Stat.stat] was called.
     */
    val statCount: Long

    /**
     * Number of times [Stat.getUnixMode] was called.
     */
    val unixModeCount: Long

    class Collector {
        private val statCount = AtomicLong()
        private val unixModeCount = AtomicLong()

        fun reportFileStated() {
            statCount.incrementAndGet()
        }

        fun reportUnixModeQueried() {
            unixModeCount.incrementAndGet()
        }

        fun collect(): StatStatistics {
            val unixModeCount = this.unixModeCount.getAndSet(0)
            val statCount = this.statCount.getAndSet(0)

            return object : StatStatistics {
                override fun getStatCount(): Long {
                    return statCount
                }

                override fun getUnixModeCount(): Long {
                    return unixModeCount
                }

                override fun toString(): String {
                    return MessageFormat.format(
                        "Executed stat() x {0,number,integer}. getUnixMode() x {1,number,integer}",
                        statCount, unixModeCount
                    )
                }
            }
        }
    }
}
