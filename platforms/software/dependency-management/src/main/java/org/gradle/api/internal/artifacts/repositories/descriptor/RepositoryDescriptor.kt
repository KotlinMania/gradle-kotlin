/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.descriptor

import com.google.common.collect.ImmutableSortedMap
import org.gradle.internal.scan.UsedByScanPlugin

/**
 * A non-functional, immutable, description of a [ResolutionAwareRepository] at a point in time.
 *
 * See org.gradle.api.internal.artifacts.configurations.ResolveConfigurationResolutionBuildOperationDetails.RepositoryImpl
 */
abstract class RepositoryDescriptor internal constructor(val id: String?, @JvmField val name: String?) {
    @UsedByScanPlugin("doesn't link against this type, but expects these values - See ResolveConfigurationDependenciesBuildOperationType")
    enum class Type {
        MAVEN,
        IVY,
        FLAT_DIR
    }

    var properties: MutableMap<String?, *>? = null
        get() {
            if (field == null) {
                val builder =
                    ImmutableSortedMap.naturalOrder<String?, Any?>()
                addProperties(builder)
                field = builder.build()
            }

            return field
        }
        private set

    @JvmField
    abstract val type: Type?

    protected abstract fun addProperties(builder: ImmutableSortedMap.Builder<String?, Any?>?)
}
