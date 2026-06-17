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
package org.gradle.api.publish.maven.internal.dependencies

/**
 * Represents a dependency within the `<dependencies>` or `<dependencyManagement>`
 * blocks of a Maven POM. This is the "Maven view" of the dependency, after being converted
 * from Gradle's dependency model.
 */
interface MavenDependency {
    /**
     * The group ID of this dependency.
     */
    val groupId: String?

    /**
     * The artifact ID of this dependency.
     */
    val artifactId: String?

    /**
     * The version of this dependency.
     */
    val version: String?

    /**
     * The type of this dependency.
     */
    val type: String?

    /**
     * The classifier of this dependency.
     */
    val classifier: String?

    /**
     * The scope of this dependency.
     */
    val scope: String?

    /**
     * The exclude rules of this dependency.
     */
    val excludeRules: MutableSet<ExcludeRule?>?

    /**
     * If this dependency is marked optional.
     */
    val isOptional: Boolean
}
