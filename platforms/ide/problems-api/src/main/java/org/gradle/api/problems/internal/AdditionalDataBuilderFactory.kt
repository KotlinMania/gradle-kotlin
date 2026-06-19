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

import com.google.common.base.Function
import com.google.common.base.Preconditions
import org.gradle.api.problems.AdditionalData
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

@ServiceScope(Scope.Build::class)
class AdditionalDataBuilderFactory {
    private val additionalDataProviders: MutableMap<Class<*>, DataTypeAndProvider> = HashMap<Class<*>, DataTypeAndProvider>()

    init {
        additionalDataProviders.put(
            GeneralDataSpec::class.java, DataTypeAndProvider(
                GeneralData::class.java,
                object : Function<AdditionalData?, AdditionalDataBuilder<out AdditionalData>> {
                    override fun apply(instance: AdditionalData?): AdditionalDataBuilder<out AdditionalData> {
                        return DefaultGeneralData.Companion.builder(instance as GeneralData?)
                    }
                })
        )
        additionalDataProviders.put(
            DeprecationDataSpec::class.java, DataTypeAndProvider(
                DeprecationData::class.java,
                object : Function<AdditionalData?, AdditionalDataBuilder<out AdditionalData>> {
                    override fun apply(instance: AdditionalData?): AdditionalDataBuilder<out AdditionalData> {
                        return DefaultDeprecationData.Companion.builder(instance as DeprecationData?)
                    }
                })
        )
        additionalDataProviders.put(
            TypeValidationDataSpec::class.java, DataTypeAndProvider(
                TypeValidationData::class.java,
                object : Function<AdditionalData?, AdditionalDataBuilder<out AdditionalData>> {
                    override fun apply(instance: AdditionalData?): AdditionalDataBuilder<out AdditionalData> {
                        return DefaultTypeValidationData.Companion.builder(instance as TypeValidationData?)
                    }
                })
        )
        additionalDataProviders.put(
            PropertyTraceDataSpec::class.java, DataTypeAndProvider(
                PropertyTraceData::class.java,
                object : Function<AdditionalData?, AdditionalDataBuilder<out AdditionalData>> {
                    override fun apply(instance: AdditionalData?): AdditionalDataBuilder<out AdditionalData> {
                        return DefaultPropertyTraceData.Companion.builder(instance as PropertyTraceData?)
                    }
                })
        )
    }

    /**
     * Registers a provider for additional data of the given type.
     *
     * @param dataType The type of additional data to provide
     * @param provider The builder function, which will be called to create a builder for the given additional data type
     */
    fun registerAdditionalDataProvider(dataType: Class<*>, provider: Function<AdditionalData?, AdditionalDataBuilder<out AdditionalData>>) {
        require(additionalDataProviders.put(dataType, DataTypeAndProvider(dataType, provider)) == null) { "Data type: '" + dataType + "' already has an additional data provider registered!" }
    }

    val supportedTypes: String
        get() {
            val result = StringBuilder()
            for (key in additionalDataProviders.keys) {
                if (result.length > 0) {
                    result.append(", ")
                }
                result.append(key.getName())
            }
            return result.toString()
        }

    private fun <U : AdditionalDataSpec?> builderFor(specType: Class<out U>, instance: AdditionalData?, illegalArgumentMessage: String): AdditionalDataBuilder<out AdditionalData> {
        Preconditions.checkNotNull(specType)
        val dataTypeAndProvider = additionalDataProviders.get(specType)
        if (dataTypeAndProvider != null) {
            return dataTypeAndProvider.builderProvider.apply(instance)
        }
        throw IllegalArgumentException(illegalArgumentMessage)
    }

    fun <U : AdditionalDataSpec?> createAdditionalDataBuilder(specType: Class<out U>, additionalData: AdditionalData?): AdditionalDataBuilder<out AdditionalData> {
        if (additionalData == null) {
            return builderFor(specType, null, "Unsupported type: " + specType)
        }
        if (isCompatible(specType, additionalData)) {
            return builderFor(specType, additionalData, "Unsupported instance: " + additionalData)
        }
        throw IllegalArgumentException("Additional data of type " + additionalData.javaClass + " is already set")
    }

    fun <U : AdditionalDataSpec?> hasProviderForSpec(specType: Class<out U>): Boolean {
        return additionalDataProviders.containsKey(specType)
    }

    private fun <U : AdditionalDataSpec?> isCompatible(specType: Class<out U>, additionalData: AdditionalData): Boolean {
        val dataTypeAndProvider = additionalDataProviders.get(specType)
        return dataTypeAndProvider != null && dataTypeAndProvider.dataType.isInstance(additionalData)
    }

    private class DataTypeAndProvider(val dataType: Class<*>, val builderProvider: Function<AdditionalData?, AdditionalDataBuilder<out AdditionalData>>)
}
