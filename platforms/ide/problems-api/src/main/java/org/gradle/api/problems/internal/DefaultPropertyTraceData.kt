/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.problems.internal

import com.google.common.base.Objects

class DefaultPropertyTraceData(private val trace: String) : PropertyTraceData {
    override fun equals(o: Any?): Boolean {
        if (o !is DefaultPropertyTraceData) {
            return false
        }
        val that = o
        return Objects.equal(trace, that.trace)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(trace)
    }

    override fun getTrace(): String {
        return trace
    }

    private class DefaultPropertyTraceDataBuilder : PropertyTraceDataSpec, AdditionalDataBuilder<PropertyTraceData> {
        private var trace: String? = null

        constructor(from: PropertyTraceData) {
            this.trace = from.getTrace()
        }

        constructor()

        override fun trace(trace: String): PropertyTraceDataSpec {
            this.trace = trace
            return this
        }

        override fun build(): PropertyTraceData {
            return DefaultPropertyTraceData(trace!!)
        }
    }

    companion object {
        fun builder(from: PropertyTraceData?): AdditionalDataBuilder<PropertyTraceData> {
            if (from == null) {
                return DefaultPropertyTraceDataBuilder()
            }
            return DefaultPropertyTraceDataBuilder(from)
        }

        fun builder(): AdditionalDataBuilder<PropertyTraceData> {
            return DefaultPropertyTraceDataBuilder()
        }
    }
}
