/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.tooling.events.problems.internal

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import org.gradle.tooling.events.problems.Severity

class DefaultSeverity(private val severity: Int, private val known: Boolean) : Severity {
    override fun getSeverity(): Int {
        return severity
    }

    override fun isKnown(): Boolean {
        return known
    }

    companion object {
        // Using the loading cache ensures that there's only one object in memory per severity level even when the level is unknown by the client
        private val UNKNOWN_ENTRIES = CacheBuilder.newBuilder().build<Int, Severity>(object : CacheLoader<Int, Severity>() {
            override fun load(key: Int): Severity {
                return DefaultSeverity(key, false)
            }
        })

        @JvmStatic
        fun from(severity: Int): Severity {
            if (severity == Severity.ADVICE.severity) {
                return Severity.ADVICE
            } else if (severity == Severity.WARNING.severity) {
                return Severity.WARNING
            } else if (severity == Severity.ERROR.severity) {
                return Severity.ERROR
            } else {
                return UNKNOWN_ENTRIES.getUnchecked(severity)
            }
        }
    }
}
