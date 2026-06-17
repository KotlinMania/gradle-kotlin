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

import com.google.common.collect.ImmutableMap
import java.io.Serializable

class DefaultGeneralData(map: MutableMap<String, String>) : GeneralData, Serializable {
    private val map: MutableMap<String, String>

    init {
        this.map = ImmutableMap.copyOf<String, String>(map)
    }

    override fun getAsMap(): MutableMap<String, String> {
        return map
    }

    private class DefaultGeneralDataBuilder : GeneralDataSpec, AdditionalDataBuilder<GeneralData> {
        private val mapBuilder = ImmutableMap.builder<String, String>()

        private constructor()

        private constructor(from: GeneralData) {
            mapBuilder.putAll(from.getAsMap())
        }

        override fun put(key: String, value: String): GeneralDataSpec {
            mapBuilder.put(key, value)
            return this
        }

        override fun build(): GeneralData {
            return DefaultGeneralData(mapBuilder.build())
        }
    }

    companion object {
        fun builder(from: GeneralData?): AdditionalDataBuilder<GeneralData> {
            if (from == null) {
                return DefaultGeneralData.DefaultGeneralDataBuilder()
            }
            return DefaultGeneralData.DefaultGeneralDataBuilder(from)
        }
    }
}
