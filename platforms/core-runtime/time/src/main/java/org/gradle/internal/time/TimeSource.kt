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
package org.gradle.internal.time

internal interface TimeSource {
    fun currentTimeMillis(): Long

    fun nanoTime(): Long

    companion object {
        val SYSTEM: TimeSource = object : TimeSource {
            override fun currentTimeMillis(): Long {
                return System.currentTimeMillis()
            }

            override fun nanoTime(): Long {
                return System.nanoTime()
            }
        }
    }
}
