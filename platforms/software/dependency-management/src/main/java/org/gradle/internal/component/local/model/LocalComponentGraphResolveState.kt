/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.GraphSelectionCandidates
import org.gradle.internal.component.model.VariantGraphResolveState
import javax.annotation.concurrent.ThreadSafe

/**
 * A specialized [ComponentGraphResolveState] for local components (ie project dependencies).
 *
 *
 * Instances of this type are cached and reused for multiple graph resolutions, possibly in parallel. This means that the implementation must be thread-safe.
 */
@ThreadSafe
interface LocalComponentGraphResolveState : ComponentGraphResolveState {
    val moduleVersionId: ModuleVersionIdentifier?

    override fun getMetadata(): LocalComponentGraphResolveMetadata?

    override fun getCandidatesForGraphVariantSelection(): LocalComponentGraphSelectionCandidates?

    interface LocalComponentGraphSelectionCandidates : GraphSelectionCandidates {
        /**
         * Get all variants that can be selected for this component. This includes both:
         *
         *  * Variant with attributes: those which can be selected through attribute matching
         *  * Variant without attributes: those which can be selected by configuration name
         *
         */
        val allSelectableVariants: MutableList<LocalVariantGraphResolveState>?

        /**
         * Returns the variant that is identified by the given configuration name.
         */
        fun getVariantByConfigurationName(name: String): VariantGraphResolveState?
    }
}
