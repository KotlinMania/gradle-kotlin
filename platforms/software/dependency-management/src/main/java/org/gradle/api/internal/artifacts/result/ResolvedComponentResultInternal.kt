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
package org.gradle.api.internal.artifacts.result

import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal

interface ResolvedComponentResultInternal : ResolvedComponentResult {
    /**
     * Get the index of this component in the underling graph structure.
     */
    fun index(): Int

    /**
     * The underlying resolved graph this component belongs to.
     */
    fun graph(): ResolvedGraphResult?

    @get:Deprecated("")
    val repositoryName: String?

    /**
     *
     * Returns the id of the repository used to source this component, or `null` if this component was not resolved from a repository.
     */
    @JvmField
    val repositoryId: String?

    /**
     * Returns all the variants of this component available for selection. Does not include variants that cannot be consumed, which means this
     * may not include all the variants returned by [.getVariants].
     *
     *
     *
     * Note: for performance reasons,
     * [org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal.setIncludeAllSelectableVariantResults]
     * must be set to `true` for this to actually return all variants in all cases.
     *
     *
     * @return all variants for this component
     * @since 7.5
     */
    @JvmField
    val availableVariants: MutableList<ResolvedVariantResult>?

    override fun getSelectionReason(): ComponentSelectionReasonInternal?
}
