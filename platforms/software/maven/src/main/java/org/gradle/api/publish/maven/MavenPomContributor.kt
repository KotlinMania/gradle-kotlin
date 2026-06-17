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
package org.gradle.api.publish.maven

/**
 * A contributor of a Maven publication.
 *
 * @since 4.8
 * @see MavenPom
 *
 * @see MavenPomContributorSpec
 */
interface MavenPomContributor {
    /**
     * The name of this contributor.
     */
    val name: Property<String?>?

    /**
     * The email
     */
    val email: Property<String?>?

    /**
     * The URL of this contributor.
     */
    val url: Property<String?>?

    /**
     * The organization name of this contributor.
     */
    val organization: Property<String?>?

    /**
     * The organization's URL of this contributor.
     */
    val organizationUrl: Property<String?>?

    /**
     * The roles of this contributor.
     */
    val roles: SetProperty<String?>?

    /**
     * The timezone of this contributor.
     */
    val timezone: Property<String?>?

    /**
     * The properties of this contributor.
     */
    val properties: MapProperty<String?, String?>?
}
