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
package org.gradle.internal.component.external.model.maven

import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.internal.component.external.descriptor.MavenScope
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.IvyArtifactName

/**
 * Represents a dependency as represented in a Maven POM file.
 */
class MavenDependencyDescriptor(
    @JvmField val scope: MavenScope, @JvmField val type: MavenDependencyType, @JvmField val selector: ModuleComponentSelector,
    /**
     * A Maven dependency has a 'dependency artifact' when it specifies a classifier or type attribute.
     */
    // A dependency artifact will be defined if the descriptor specified a classifier or non-default type attribute.
    @JvmField val dependencyArtifact: IvyArtifactName?, excludes: MutableList<ExcludeMetadata>
) : ExternalDependencyDescriptor() {
    private val excludes: ImmutableList<ExcludeMetadata>

    init {
        this.dependencyArtifact = dependencyArtifact
        this.excludes = ImmutableList.copyOf<ExcludeMetadata>(excludes)
    }

    override fun toString(): String {
        return "dependency: " + selector + ", scope: " + scope + ", optional: " + isOptional
    }

    val isChanging: Boolean
        get() = false

    val isTransitive: Boolean
        get() = !(isConstraint || isOptional)

    public override fun withRequested(newRequested: ModuleComponentSelector): MavenDependencyDescriptor {
        return MavenDependencyDescriptor(scope, type, newRequested, dependencyArtifact, excludes)
    }

    val allExcludes: MutableList<ExcludeMetadata>
        get() = excludes

    val configurationExcludes: ImmutableList<ExcludeMetadata>
        get() {
            // Ignore exclusions for dependencies with `<optional>true</optional>`, but not for <dependencyManagement>.
            if (type == MavenDependencyType.OPTIONAL_DEPENDENCY) {
                return ImmutableList.of<ExcludeMetadata>()
            }
            return excludes
        }

    val configurationArtifacts: ImmutableList<IvyArtifactName>
        /**
         * When a Maven dependency declares a classifier or type attribute, this is modelled as a 'dependency artifact'.
         * This means that instead of resolving the default artifacts for the target dependency, we'll use the one defined
         * for the dependency.
         */
        get() {
            // Special handling for artifacts declared for optional dependencies
            if (isOptional) {
                return this.artifactsForOptionalDependency
            }
            return this.dependencyArtifacts
        }

    private val artifactsForOptionalDependency: ImmutableList<IvyArtifactName>
        /**
         * When an optional dependency declares a classifier, that classifier is effectively ignored, and the optional
         * dependency will update the version of any dependency with matching GAV.
         * (Same goes for `<type>` on optional dependencies: they are effectively ignored).
         *
         * Note that this doesn't really match with Maven, where an optional dependency with classifier will
         * provide a version for any other dependency with matching GAV + classifier.
         */
        get() = ImmutableList.of<IvyArtifactName>()

    private val dependencyArtifacts: ImmutableList<IvyArtifactName>
        /**
         * For a Maven dependency, the artifacts list as zero or one Artifact, always with '*' configuration
         */
        get() = if (dependencyArtifact == null) ImmutableList.of<IvyArtifactName>() else ImmutableList.of<IvyArtifactName>(
            dependencyArtifact
        )

    val isOptional: Boolean
        get() = type == MavenDependencyType.OPTIONAL_DEPENDENCY

    val isConstraint: Boolean
        get() = type == MavenDependencyType.DEPENDENCY_MANAGEMENT

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as MavenDependencyDescriptor
        return type == that.type && Objects.equal(selector, that.selector)
                && scope == that.scope && Objects.equal(excludes, that.excludes)
                && Objects.equal(dependencyArtifact, that.dependencyArtifact)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(
            selector,
            scope,
            type,
            excludes,
            dependencyArtifact
        )
    }
}
