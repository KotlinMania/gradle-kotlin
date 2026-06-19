/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.tooling.model.gradle

import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.BuildModel
import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.Model

/**
 * Provides information about the structure of a Gradle build.
 *
 * @since 1.8
 */
interface GradleBuild : Model, BuildModel {
    /**
     * Returns the identifier for this Gradle build.
     *
     * @since 2.13
     */
    override val buildIdentifier: BuildIdentifier?

    /**
     * Returns the root project for this build.
     *
     * @return The root project
     */
    val rootProject: BasicGradleProject?

    /**
     * Returns the set of all projects for this build.
     *
     * @return The set of all projects.
     */
    val projects: DomainObjectSet<out BasicGradleProject?>?

    /**
     * Returns the included builds that were referenced by this build. This is the set of builds that were directly included by this build via its [org.gradle.api.initialization.Settings] instance.
     *
     *
     * Note that this set does not include builds that are added in other ways, such as a `buildSrc` build.
     * Also note that a build may be included by multiple builds, so that the inclusions form a graph of builds rather than a tree of builds. There may be cycles in this graph.
     *
     *
     * In general, it is better to use [.getEditableBuilds] instead of this method.
     *
     * @since 3.3
     */
    val includedBuilds: DomainObjectSet<out GradleBuild?>?

    /**
     * Returns all builds contained in this build that should be imported into an IDE.
     *
     *
     * This is not always the same the builds returned by [.getIncludedBuilds]. For the root build, the set of importable builds contains all builds that participate in the composite build,
     * including those directly included by the root build plus all builds included transitively. For Gradle 7.2 and later, this set also includes any `buildSrc` builds that may be present.
     * For all other builds, this set is empty.
     *
     *
     * Note that this set does not include the root build itself.
     *
     * @since 4.10
     */
    val editableBuilds: DomainObjectSet<out GradleBuild?>?
}
