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
package org.gradle.internal.component.model

import org.gradle.api.artifacts.component.ComponentIdentifier

/**
 * State for a component instance that is used to perform artifact resolution.
 *
 *
 * Resolution happens in multiple steps. The first is to calculate the dependency graph, and the subsequent steps select artifacts. Artifact resolution is broken down into 3 main steps:
 *
 *  * Select a variant of the component instance. The variant selected for artifact resolution may be different to that used for graph resolution,
 * for example when using an [org.gradle.api.artifacts.ArtifactView] to select different variants.
 *  * Determine how to produce the artifacts of the variant, for example by running a chain of transformers.
 *  * Produce the artifacts, for example by running the transforms or downloading files.
 *
 *
 *
 * This interface says nothing about thread safety, however some subtypes may be required to be thread safe.
 *
 *
 * Instances of this type are created using [ComponentGraphResolveState.prepareForArtifactResolution].
 */
interface ComponentArtifactResolveState {
    fun getId(): ComponentIdentifier?

    /**
     * Metadata for this component's artifacts.
     */
    fun getArtifactMetadata(): ComponentArtifactResolveMetadata?
}
