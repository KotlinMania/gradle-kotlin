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
package org.gradle.api.internal.artifacts.result

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.DisplayName
import java.util.Optional

class DefaultResolvedVariantResult(
    private val owner: ComponentIdentifier,
    private val displayName: DisplayName,
    private val attributes: AttributeContainer,
    private val capabilities: ImmutableList<out Capability>,
    private val externalVariant: ResolvedVariantResult?
) : ResolvedVariantResult {
    private val hashCode: Int

    init {
        this.hashCode = computeHashCode()
    }

    override fun getOwner(): ComponentIdentifier {
        return owner
    }

    override fun getAttributes(): AttributeContainer {
        return attributes
    }

    override fun getDisplayName(): String {
        return displayName.getDisplayName()
    }

    override fun getCapabilities(): MutableList<Capability> {
        return uncheckedCast<MutableList<Capability>?>(capabilities)!!
    }

    override fun getExternalVariant(): Optional<ResolvedVariantResult> {
        return Optional.ofNullable<ResolvedVariantResult>(externalVariant)
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultResolvedVariantResult

        if (owner != that.owner) {
            return false
        }
        if (displayName != that.displayName) {
            return false
        }
        if (attributes != that.attributes) {
            return false
        }
        if (capabilities != that.capabilities) {
            return false
        }
        return if (externalVariant == null) that.externalVariant == null else (externalVariant == that.externalVariant)
    }

    override fun hashCode(): Int {
        return hashCode
    }

    private fun computeHashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + attributes.hashCode()
        result = 31 * result + capabilities.hashCode()
        if (externalVariant != null) {
            result = 31 * externalVariant.hashCode()
        }
        return result
    }

    override fun toString(): String {
        return displayName.toString()
    }
}
