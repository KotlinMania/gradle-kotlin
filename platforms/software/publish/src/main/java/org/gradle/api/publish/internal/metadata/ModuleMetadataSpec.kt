/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.publish.internal.metadata

import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleVersionIdentifier
import java.io.File

/**
 * A complete description of a GMM file that can be published without additional context.
 */
class ModuleMetadataSpec internal constructor(
    val identity: Identity?,
    val variants: MutableList<Variant?>?,
    val mustIncludeBuildId: Boolean
) {
    internal class Identity(
        val coordinates: ModuleVersionIdentifier?,
        val attributes: MutableList<Attribute?>?,
        val relativeUrl: String?
    )

    internal class LocalVariant(
        val name: String?,
        val attributes: MutableList<Attribute?>?,
        val capabilities: MutableList<Capability?>?,
        val dependencies: MutableList<Dependency?>?,
        val dependencyConstraints: MutableList<DependencyConstraint?>?,
        val artifacts: MutableList<Artifact?>?
    ) : Variant()

    internal class RemoteVariant(
        val name: String?,
        val attributes: MutableList<Attribute?>?,
        val availableAt: AvailableAt?,
        val capabilities: MutableList<Capability?>?
    ) : Variant()

    internal class Dependency(
        val coordinates: DependencyCoordinates?,
        val excludeRules: MutableSet<ExcludeRule?>?,
        val attributes: MutableList<Attribute?>?,
        val requestedCapabilities: MutableList<Capability?>?,
        val endorseStrictVersions: Boolean,
        val reason: String?,
        val artifactSelector: ArtifactSelector?
    )

    internal abstract class Variant

    internal class Attribute(val name: String?, val value: Any?)

    internal class Capability(val group: String?, val name: String?, val version: String?)

    internal class Version(
        val requires: String?,
        val strictly: String?,
        val preferred: String?,
        val rejectedVersions: MutableList<String?>?
    )

    internal class DependencyCoordinates(
        val group: String?, val name: String?, val version: Version?
    )

    internal class ArtifactSelector(
        val name: String?,
        val type: String?,
        val extension: String?,
        val classifier: String?
    )

    internal class DependencyConstraint(
        val coordinates: DependencyCoordinates?,
        val attributes: MutableList<Attribute?>?,
        val reason: String?
    )

    internal class Artifact(val name: String?, val uri: String?, val file: File?)

    internal class AvailableAt(val url: String?, val coordinates: ModuleVersionIdentifier?)
}
