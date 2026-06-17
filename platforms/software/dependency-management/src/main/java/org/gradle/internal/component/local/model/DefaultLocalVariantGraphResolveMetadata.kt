/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.internal.component.local.model

import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.VariantIdentifier

/**
 * Default implementation of [LocalVariantGraphResolveMetadata] used to represent a single local variant.
 */
class DefaultLocalVariantGraphResolveMetadata(
    private val id: VariantIdentifier,
    private val name: String,
    private val transitive: Boolean,
    private val attributes: ImmutableAttributes,
    private val capabilities: ImmutableCapabilities,
    private val deprecatedForConsumption: Boolean
) : LocalVariantGraphResolveMetadata {
    override fun getId(): VariantIdentifier {
        return id
    }

    override fun getName(): String {
        return name
    }

    override fun getConfigurationName(): String {
        return name
    }

    override fun isTransitive(): Boolean {
        return transitive
    }

    override fun getAttributes(): ImmutableAttributes {
        return attributes
    }

    override fun isDeprecated(): Boolean {
        return deprecatedForConsumption
    }

    override fun getCapabilities(): ImmutableCapabilities {
        return capabilities
    }

    override fun isExternalVariant(): Boolean {
        return false
    }

    override fun toString(): String {
        return "variant " + name
    }
}
