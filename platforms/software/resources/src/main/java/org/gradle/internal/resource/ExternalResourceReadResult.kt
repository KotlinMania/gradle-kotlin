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
package org.gradle.internal.resource

/**
 * @since 4.0
 */
class ExternalResourceReadResult<T> private constructor(
    /**
     * The number of **content** bytes read.
     *
     *
     * This is not guaranteed to be the number of bytes **transferred**.
     * For example, this resource may be content encoded (e.g. compression, fewer bytes transferred).
     * Or, it might be transfer encoded (e.g. HTTP chunked transfer, more bytes transferred).
     * Or, both.
     * Therefore, it is not necessarily an accurate input into transfer rate (a.k.a. throughput) calculations.
     *
     *
     * Moreover, it represents the content bytes **read**, not transferred.
     * If the read operation only reads a subset of what was transmitted, this number will be the read byte count.
     */
    val bytesRead: Long,
    /**
     * Any final result of the read operation.
     */
    val result: T?
) {
    companion object {
        fun of(bytesRead: Long): ExternalResourceReadResult<Void?> {
            return ExternalResourceReadResult<Void?>(bytesRead, null)
        }

        fun <T> of(bytesRead: Long, t: T?): ExternalResourceReadResult<T?> {
            return ExternalResourceReadResult<T?>(bytesRead, t)
        }
    }
}
