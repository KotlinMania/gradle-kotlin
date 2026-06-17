/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.publish.maven

import org.gradle.api.Action
import org.gradle.api.DomainObjectSet

/**
 * A Collection of [MavenArtifact]s to be included in a [MavenPublication].
 *
 * Being a [DomainObjectSet], a `MavenArtifactSet` provides convenient methods for querying, filtering, and applying actions to the set of [MavenArtifact]s.
 *
 * <pre class='autoTested'>
 * plugins {
 * id 'maven-publish'
 * }
 *
 * def publication = publishing.publications.create("name", MavenPublication)
 * def artifacts = publication.artifacts
 *
 * artifacts.matching({
 * it.classifier == "classy"
 * }).all({
 * it.extension = "ext"
 * })
</pre> *
 *
 * @see DomainObjectSet
 */
interface MavenArtifactSet : DomainObjectSet<MavenArtifact?> {
    /**
     * Creates and adds a [MavenArtifact] to the set.
     *
     * The semantics of this method are the same as [MavenPublication.artifact].
     *
     * @param source The source of the artifact content.
     */
    fun artifact(source: Any?): MavenArtifact?

    /**
     * Creates and adds a [MavenArtifact] to the set, which is configured by the associated action.
     *
     * The semantics of this method are the same as [MavenPublication.artifact].
     *
     * @param source The source of the artifact.
     * @param config An action or closure to configure the values of the constructed [MavenArtifact].
     */
    fun artifact(source: Any?, config: Action<in MavenArtifact?>?): MavenArtifact?
}
