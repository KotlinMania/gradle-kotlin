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

class DefaultDeprecationData(private val type: DeprecationData.Type) : DeprecationData {
    override fun getType(): DeprecationData.Type {
        return type
    }

    override fun equals(o: Any?): Boolean {
        if (o !is DefaultDeprecationData) {
            return false
        }
        val that = o
        return type == that.type
    }

    override fun hashCode(): Int {
        return Objects.hashCode(type)
    }

    private class DefaultDeprecationDataBuilder : DeprecationDataSpec, AdditionalDataBuilder<DeprecationData> {
        private var type: DeprecationData.Type

        constructor() {
            this.type = DeprecationData.Type.USER_CODE_DIRECT
        }

        constructor(from: DeprecationData) {
            this.type = from.getType()!!
        }

        override fun type(type: DeprecationData.Type): DeprecationDataSpec {
            this.type = type
            return this
        }

        override fun build(): DeprecationData {
            return DefaultDeprecationData(type)
        }
    }

    companion object {
        fun builder(from: DeprecationData?): AdditionalDataBuilder<DeprecationData> {
            if (from == null) {
                return DefaultDeprecationDataBuilder()
            }
            return DefaultDeprecationDataBuilder(from)
        }
    }
}
