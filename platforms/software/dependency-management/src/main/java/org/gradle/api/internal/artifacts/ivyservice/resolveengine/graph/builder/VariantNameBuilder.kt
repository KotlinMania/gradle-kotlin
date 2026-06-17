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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder

import org.apache.commons.lang3.StringUtils
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName

/**
 * A memory-efficient builder for variant names.
 *
 * When a component has multiple selected nodes (or multiple selected configurations), we build a name for the variant
 * from the names of the configurations. The set of names for these configurations is relatively limited, so we cache
 * the generated names to avoid recreating multiple times.
 */
class VariantNameBuilder {
    private val names: MutableMap<MutableList<String>, DisplayName> = HashMap<MutableList<String>, DisplayName>()

    fun getVariantName(parts: MutableList<String>?): DisplayName? {
        if (parts == null) {
            return null
        }

        var displayName = names.get(parts)
        if (displayName == null) {
            displayName = variantName(parts)
            names.put(parts, displayName)
        }

        return displayName
    }

    private class MultipleVariantName(private val parts: MutableList<String>) : DisplayName {
        override fun getCapitalizedDisplayName(): String {
            return StringUtils.capitalize(getDisplayName())
        }

        override fun getDisplayName(): String {
            val sb = StringBuilder(16 * parts.size)
            var appendPlus = false
            for (part in parts) {
                if (appendPlus) {
                    sb.append("+")
                }
                sb.append(part)
                appendPlus = true
            }
            return sb.toString()
        }
    }

    companion object {
        private fun variantName(parts: MutableList<String>): DisplayName {
            if (parts.size == 1) {
                return Describables.of(parts.get(0))
            }
            return MultipleVariantName(parts)
        }
    }
}
