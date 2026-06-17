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
package org.gradle.internal.jacoco.rules

import org.gradle.testing.jacoco.tasks.rules.JacocoLimit
import java.math.BigDecimal

class JacocoLimitImpl : JacocoLimit {
    private var counter: String? = "INSTRUCTION"
    private var value: String? = "COVEREDRATIO"
    private var minimum: BigDecimal? = null
    private var maximum: BigDecimal? = null

    override fun getCounter(): String? {
        return counter
    }

    override fun setCounter(counter: String?) {
        this.counter = counter
    }

    override fun getValue(): String? {
        return value
    }

    override fun setValue(value: String?) {
        this.value = value
    }

    override fun getMinimum(): BigDecimal? {
        return minimum
    }

    override fun setMinimum(minimum: BigDecimal?) {
        this.minimum = minimum
    }

    override fun getMaximum(): BigDecimal? {
        return maximum
    }

    override fun setMaximum(maximum: BigDecimal?) {
        this.maximum = maximum
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as JacocoLimitImpl

        if (counter !== that.counter) {
            return false
        }
        if (value !== that.value) {
            return false
        }
        if (if (minimum != null) (minimum != that.minimum) else that.minimum != null) {
            return false
        }
        return if (maximum != null) (maximum == that.maximum) else that.maximum == null
    }

    override fun hashCode(): Int {
        var result = if (counter != null) counter.hashCode() else 0
        result = 31 * result + (if (value != null) value.hashCode() else 0)
        result = 31 * result + (if (minimum != null) minimum.hashCode() else 0)
        result = 31 * result + (if (maximum != null) maximum.hashCode() else 0)
        return result
    }
}
