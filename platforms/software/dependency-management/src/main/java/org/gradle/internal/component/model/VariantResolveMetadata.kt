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

import org.gradle.internal.DisplayName

/**
 * Would be better named `VariantArtifactMetadata`.
 *
 *
 * Describes the artifacts of a [VariantGraphResolveMetadata]. Graph variants may have multiple
 * artifact variants, where each artifact variant may have different artifacts, but inherit the dependencies
 * of its graph variant.
 */
interface VariantResolveMetadata {
    @JvmField
    val name: String?

    /**
     * An identifier for this artifact variant.
     *
     *
     * May be null for adhoc variants.
     */
    @JvmField
    val identifier: Identifier?

    fun asDescribable(): DisplayName?

    @JvmField
    val attributes: ImmutableAttributes?

    @JvmField
    val artifacts: ImmutableList<out ComponentArtifactMetadata>?

    // TODO: This type should not expose capabilities, as all artifact variants within a single graph variant
    // should have the same capability.
    val capabilities: ImmutableCapabilities?
        // TODO: This type should not expose capabilities, as all artifact variants within a single graph variant
        get

    fun isExternalVariant(): Boolean

    /**
     * Is this variant eligible for caching?
     *
     * Only variants from a project component are eligible for caching.
     *
     * @see [Context](https://github.com/gradle/gradle/pull/23500.discussion_r1073224819)
     */
    fun isEligibleForCaching(): Boolean {
        return false
    }

    /**
     * An opaque identifier for a an artifact variant.
     *
     *
     * Implementations must implement equals and hashCode.
     */
    interface Identifier
}
