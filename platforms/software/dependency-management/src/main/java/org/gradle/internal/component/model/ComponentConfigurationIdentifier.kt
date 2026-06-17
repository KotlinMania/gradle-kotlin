/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.artifacts.component.ComponentIdentifier

/**
 * Identifies the "implicit" artifact variant for a graph variant.
 */
class ComponentConfigurationIdentifier(private val component: ComponentIdentifier, private val configurationName: String) : VariantResolveMetadata.Identifier {
    private val hashCode: Int

    init {
        this.hashCode = computeHashCode(component, configurationName)
    }

    override fun equals(obj: Any): Boolean {
        if (obj === this) {
            return true
        }
        if (obj == null || obj.javaClass != javaClass) {
            return false
        }
        val other = obj as ComponentConfigurationIdentifier
        return component == other.component && configurationName == other.configurationName
    }

    override fun hashCode(): Int {
        return hashCode
    }

    companion object {
        private fun computeHashCode(component: ComponentIdentifier, configurationName: String): Int {
            return 31 * component.hashCode() + configurationName.hashCode()
        }
    }
}
