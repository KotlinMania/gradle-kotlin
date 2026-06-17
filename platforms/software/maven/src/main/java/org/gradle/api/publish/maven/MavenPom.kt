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
import org.gradle.api.XmlProvider
import org.gradle.internal.HasInternalProtocol
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty

/**
 * The POM for a Maven publication.
 *
 *
 * The [.withXml] method can be used to modify the
 * descriptor after it has been generated according to the publication data.
 * However, the preferred way to customize the project information to be published
 * is to use the dedicated properties exposed by this class, e.g.
 * [.getDescription]. Please refer to the official
 * [POM Reference](https://maven.apache.org/pom.html) for detailed
 * information about the individual properties.
 *
 * @since 1.4
 */
@HasInternalProtocol
interface MavenPom {
    /**
     * Sets the packaging for the publication represented by this POM.
     */
    @get:ToBeReplacedByLazyProperty
    var packaging: String?

    /**
     * The name for the publication represented by this POM.
     *
     * @since 4.8
     */
    val name: Property<String?>?

    /**
     * A short, human-readable description for the publication represented by this POM.
     *
     * @since 4.8
     */
    val description: Property<String?>?

    /**
     * The URL of the home page for the project producing the publication represented by this POM.
     *
     * @since 4.8
     */
    val url: Property<String?>?

    /**
     * The year the project producing the publication represented by this POM was first created.
     *
     * @since 4.8
     */
    val inceptionYear: Property<String?>?

    /**
     * Configures the licenses for the publication represented by this POM.
     *
     * @since 4.8
     */
    fun licenses(action: Action<in MavenPomLicenseSpec?>?)

    /**
     * Configures the organization for the publication represented by this POM.
     *
     * @since 4.8
     */
    fun organization(action: Action<in MavenPomOrganization?>?)

    /**
     * Configures the developers for the publication represented by this POM.
     *
     * @since 4.8
     */
    fun developers(action: Action<in MavenPomDeveloperSpec?>?)

    /**
     * Configures the contributors for the publication represented by this POM.
     *
     * @since 4.8
     */
    fun contributors(action: Action<in MavenPomContributorSpec?>?)

    /**
     * Configures the SCM (source control management) for the publication represented by this POM.
     *
     * @since 4.8
     */
    fun scm(action: Action<in MavenPomScm?>?)

    /**
     * Configures the issue management for the publication represented by this POM.
     *
     * @since 4.8
     */
    fun issueManagement(action: Action<in MavenPomIssueManagement?>?)

    /**
     * Configures the CI management for the publication represented by this POM.
     *
     * @since 4.8
     */
    fun ciManagement(action: Action<in MavenPomCiManagement?>?)

    /**
     * Configures the distribution management for the publication represented by this POM.
     *
     * @since 4.8
     */
    fun distributionManagement(action: Action<in MavenPomDistributionManagement?>?)

    /**
     * Configures the mailing lists for the publication represented by this POM.
     *
     * @since 4.8
     */
    fun mailingLists(action: Action<in MavenPomMailingListSpec?>?)

    /**
     * Returns the properties for the publication represented by this POM.
     *
     * @since 5.3
     */
    val properties: MapProperty<String?, String?>?

    /**
     * Allows configuration of the POM, after it has been generated according to the input data.
     *
     * <pre class='autoTested'>
     * plugins {
     * id 'maven-publish'
     * }
     *
     * publishing {
     * publications {
     * maven(MavenPublication) {
     * pom.withXml {
     * asNode().appendNode('properties').appendNode('my-property', 'my-value')
     * }
     * }
     * }
     * }
    </pre> *
     *
     * Note that due to Gradle's internal type conversion system, you can pass a Groovy closure to this method and
     * it will be automatically converted to an `Action`.
     *
     *
     * Each action/closure passed to this method will be stored as a callback, and executed when the publication
     * that this descriptor is attached to is published.
     *
     *
     * For details on the structure of the XML to be modified, see [the POM reference](http://maven.apache.org/pom.html).
     *
     * @param action The configuration action.
     * @see MavenPublication
     *
     * @see XmlProvider
     */
    fun withXml(action: Action<in XmlProvider?>?)
}
