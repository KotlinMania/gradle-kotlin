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
package org.gradle.internal.component.model

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.DisplayName
import org.gradle.internal.component.external.model.ImmutableCapabilities

open class DefaultVariantMetadata(
    private val name: String,
    private val identifier: VariantResolveMetadata.Identifier?,
    private val displayName: DisplayName,
    private val attributes: ImmutableAttributes,
    private val artifacts: ImmutableList<out ComponentArtifactMetadata>,
    private val capabilitiesMetadata: ImmutableCapabilities
) : VariantResolveMetadata {
    override fun getName(): String {
        return name
    }

    override fun getIdentifier(): VariantResolveMetadata.Identifier {
        return identifier!!
    }

    override fun asDescribable(): DisplayName {
        return displayName
    }

    override fun getAttributes(): ImmutableAttributes {
        return attributes
    }

    override fun getArtifacts(): ImmutableList<out ComponentArtifactMetadata> {
        return artifacts
    }

    override fun getCapabilities(): ImmutableCapabilities {
        return capabilitiesMetadata
    }

    override fun isExternalVariant(): Boolean {
        return false
    }
}
