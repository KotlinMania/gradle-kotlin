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
package org.gradle.internal.concurrent

import org.apache.commons.lang3.builder.EqualsBuilder
import org.apache.commons.lang3.builder.HashCodeBuilder
import org.gradle.concurrent.ParallelismConfiguration
import java.io.Serializable

class DefaultParallelismConfiguration : Serializable, ParallelismConfiguration {
    override var isParallelProjectExecutionEnabled = false
    override var maxWorkerCount: Int = defaultMaxWorkerCount
        set(maxWorkerCount) {
            require(maxWorkerCount >= 1) { "Max worker count must be > 0" }
            field = maxWorkerCount
        }

    constructor() {
    }

    constructor(parallelProjectExecution: Boolean, maxWorkerCount: Int) {
        this.isParallelProjectExecutionEnabled = parallelProjectExecution
        this.maxWorkerCount = maxWorkerCount
    }

    override fun equals(obj: Any?): Boolean {
        return EqualsBuilder.reflectionEquals(this, obj)
    }

    override fun hashCode(): Int {
        return HashCodeBuilder.reflectionHashCode(this)
    }

    companion object {
        val defaultMaxWorkerCount: Int
            get() = Runtime.getRuntime().availableProcessors()
    }
}
