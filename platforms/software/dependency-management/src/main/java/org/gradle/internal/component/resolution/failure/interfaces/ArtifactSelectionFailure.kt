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
package org.gradle.internal.component.resolution.failure.interfaces

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.attributes.ImmutableAttributes

/**
 * Represents a failure selecting an artifact variant for a selected graph variant
 * during the [Artifact Selection][org.gradle.internal.component.resolution.failure.interfaces] part of dependency resolution.
 *
 *
 * When this failure occurs, we have always selected a component and a graph variant,
 * we are now attempting to select an artifact using a possibly different set of
 * attributes from those used during [Variant Selection][org.gradle.internal.component.resolution.failure.interfaces]
 * to select the graph variant.
 */
interface ArtifactSelectionFailure : ResolutionFailure {
    /**
     * Gets the identifier of the component for which an artifact variant could not be selected.
     *
     * @return identifier for the component for which an artifact variant could not be selected
     */
    fun getTargetComponent(): ComponentIdentifier?

    /**
     * Gets the name of the variant for which an artifact could not be selected.
     *
     * @return name of the variant for which an artifact could not be selected
     */
    fun getTargetVariant(): String?

    /**
     * Gets the attributes that were used to attempt to select an artifact.
     *
     *
     * This is the combination of originally requested attributes during graph selection and any potential attribute modifications
     * performed by an [ArtifactView][org.gradle.api.artifacts.ArtifactView] that is being used to select an artifact.
     *
     * @return the attributes that were used to attempt to select an artifact for this variant
     */
    fun getRequestedAttributes(): ImmutableAttributes?
}
