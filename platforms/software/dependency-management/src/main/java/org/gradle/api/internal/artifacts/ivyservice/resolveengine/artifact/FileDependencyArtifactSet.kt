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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.model.CalculatedValueContainerFactory

class FileDependencyArtifactSet(
    private val fileDependency: LocalFileDependencyMetadata,
    private val sourceVariantId: VariantIdentifier,
    private val artifactTypeRegistry: ImmutableArtifactTypeRegistry,
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory
) : ArtifactSet {
    override fun select(
        consumerServices: ArtifactSelectionServices,
        spec: ArtifactSelectionSpec
    ): ResolvedArtifactSet {
        // Select the artifacts later, as this is a function of the file names and these may not be known yet because the producing tasks have not yet executed
        return DefaultLocalFileDependencyBackedArtifactSet(
            fileDependency,
            sourceVariantId,
            spec.componentFilter,
            consumerServices.getArtifactVariantSelector(),
            artifactTypeRegistry,
            calculatedValueContainerFactory,
            consumerServices.transformRegistry,
            spec.requestAttributes,
            spec.allowNoMatchingVariants
        )
    }
}
