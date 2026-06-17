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
package org.gradle.internal.component.model

import org.gradle.api.internal.attributes.CompatibilityCheckResult

class DefaultCompatibilityCheckResult<T>(consumerValue: T?, producerValue: T?) : CompatibilityCheckResult<T?> {
    private val consumerValue: T?
    private val producerValue: T?
    private var compatible = false
    private var done = false

    init {
        checkNotNull(producerValue) { "Internal contract of the current implementation, can be changed with a motivation" }
        assert(consumerValue != producerValue)
        this.consumerValue = consumerValue
        this.producerValue = producerValue
    }

    override fun isCompatible(): Boolean {
        assert(done)
        return compatible
    }

    override fun hasResult(): Boolean {
        return done
    }

    override fun getConsumerValue(): T? {
        return consumerValue
    }

    override fun getProducerValue(): T? {
        return producerValue
    }

    override fun compatible() {
        done = true
        compatible = true
    }

    override fun incompatible() {
        done = true
        compatible = false
    }
}
