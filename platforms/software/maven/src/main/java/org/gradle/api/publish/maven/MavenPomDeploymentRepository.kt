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
package org.gradle.api.publish.maven

import org.gradle.api.Incubating
import org.jspecify.annotations.NullMarked

/**
 * The repository information of the Maven `distributionManagement` node.
 *
 * @see MavenPom
 *
 * @see MavenPomDistributionManagement
 *
 * @since 9.1.0
 */
@Incubating
@NullMarked
interface MavenPomDeploymentRepository {
    /**
     * A unique identifier for a repository.
     *
     * @since 9.1.0
     */
    val id: Property<String>?

    /**
     * Human readable name of the repository.
     *
     * @since 9.1.0
     */
    val name: Property<String>?

    /**
     * Whether to assign snapshots a unique version comprised of the timestamp and build number, or to use the same version each time.
     *
     * Default value: `true`
     *
     * @since 9.1.0
     */
    val uniqueVersion: Property<Boolean>?

    /**
     * The url of the repository, in the form `protocol://hostname/path`.
     *
     * @since 9.1.0
     */
    val url: Property<String>?

    /**
     * The type of layout this repository uses for locating and storing artifacts - can be `legacy` or `default`.
     *
     * Default value: `default`
     *
     * @since 9.1.0
     */
    val layout: Property<String>?
}
