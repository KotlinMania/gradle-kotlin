/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.tasks.TaskDependency
import java.util.Optional

/**
 * Meta-data for an artifact that belongs to some component.
 */
interface ComponentArtifactMetadata {
    /**
     * Returns the identifier for this artifact.
     */
    fun getId(): ComponentArtifactIdentifier?

    /**
     * Returns the identifier for the component that this artifact belongs to.
     */
    fun getComponentId(): ComponentIdentifier?

    /**
     * Returns this artifact as an Ivy artifact. This method is here to allow the artifact to be exposed in a backward-compatible way.
     */
    fun getName(): IvyArtifactName?

    /**
     * Collects the build dependencies of this artifact, which are required to build this artifact
     */
    fun getBuildDependencies(): TaskDependency?

    /**
     * Allows metadata with non-standard packaging to add a "fallback" artifact, to be resolved only when resolution fails.
     *
     * Typical use-cases are:
     *
     *  1. Maven POM declares `pom` packaging, but actually the artifact is a `jar`.
     *  1. Maven POM declares an atypical packaging which does not match the artifact's type/extension property.  See [hk2-jar example](https://repo1.maven.org/maven2/org/glassfish/ha/ha-api/3.1.7/).
     *
     *
     * In these cases, supplying the alternative artifact metadata is a way to allow a re-fetch a different artifact file for the same component.
     *
     *
     * Defaults to [Optional.empty]
     *
     * @return an optional artifact metadata, which if present will be resolved if this artifact's resolution fails
     * @see DefaultModuleComponentArtifactMetadata.DefaultModuleComponentArtifactMetadata
     */
    fun getAlternativeArtifact(): Optional<ComponentArtifactMetadata> {
        return Optional.empty<ComponentArtifactMetadata>()
    }

    fun isOptionalArtifact(): Boolean {
        return false
    }
}
