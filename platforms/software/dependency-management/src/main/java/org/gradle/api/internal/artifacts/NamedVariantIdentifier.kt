/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.api.internal.artifacts

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.internal.component.model.VariantIdentifier

/**
 * Identifier for a variant of a component, identified by its name.
 */
class NamedVariantIdentifier(
    val componentId: ComponentIdentifier,
    val name: String
) : VariantIdentifier {
    private val hashCode: Int

    init {
        this.hashCode = computeHashCode(componentId, name)
    }

    val displayName: String
        get() = name + "(" + componentId.getDisplayName() + ")"

    override fun equals(o: Any): Boolean {
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as NamedVariantIdentifier
        return this.componentId == that.componentId && name == that.name
    }

    override fun hashCode(): Int {
        return hashCode
    }

    companion object {
        private fun computeHashCode(componentIdentifier: ComponentIdentifier, name: String): Int {
            var result = componentIdentifier.hashCode()
            result = 31 * result + name.hashCode()
            return result
        }
    }
}
