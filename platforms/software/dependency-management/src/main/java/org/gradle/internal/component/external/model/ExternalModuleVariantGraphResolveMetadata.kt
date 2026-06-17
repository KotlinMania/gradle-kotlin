/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.component.external.model

import org.gradle.internal.component.model.VariantGraphResolveMetadata

/**
 * A variant of an external module component.
 *
 *
 * Unlike local variants, the dependencies of external variants are known statically,
 * and are available as part of the metadata.
 */
interface ExternalModuleVariantGraphResolveMetadata : VariantGraphResolveMetadata {
    /**
     * Get the dependencies for this variant.
     */
    val dependencies: MutableList<out DependencyMetadata?>?

    /**
     * Get exclusions to apply to the dependencies and artifacts of this variant.
     */
    val excludes: MutableList<out ExcludeMetadata?>?
}
