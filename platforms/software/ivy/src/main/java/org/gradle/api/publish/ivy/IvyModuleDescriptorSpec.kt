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
package org.gradle.api.publish.ivy

import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.api.tasks.Nested
import org.gradle.internal.HasInternalProtocol
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty

/**
 * The descriptor of any Ivy publication.
 *
 *
 * Corresponds to the [XML version of the Ivy Module Descriptor](http://ant.apache.org/ivy/history/latest-milestone/ivyfile.html).
 *
 *
 * The [.withXml] method can be used to modify the descriptor after it has been generated according to the publication data.
 * However, the preferred way to customize the project information to be published is to use the dedicated configuration methods exposed by this class, e.g.
 * [.description].
 *
 * @since 1.3
 */
@HasInternalProtocol
interface IvyModuleDescriptorSpec {
    /**
     * Allow configuration of the descriptor, after it has been generated according to the input data.
     *
     * <pre class='autoTested'>
     * plugins {
     * id 'ivy-publish'
     * }
     *
     * publishing {
     * publications {
     * ivy(IvyPublication) {
     * descriptor {
     * withXml {
     * asNode().dependencies.dependency.find { it.@org == "junit" }.@rev = "4.10"
     * }
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
     * For details on the structure of the XML to be modified, see [the
     * Ivy Module Descriptor reference](http://ant.apache.org/ivy/history/latest-milestone/ivyfile.html).
     *
     *
     * @param action The configuration action.
     * @see IvyPublication
     *
     * @see XmlProvider
     */
    fun withXml(action: Action<in XmlProvider?>?)

    /**
     * Sets the status for this publication.
     */
    @JvmField
    @get:ToBeReplacedByLazyProperty
    var status: String?

    /**
     * Sets the branch for this publication
     */
    @JvmField
    @get:ToBeReplacedByLazyProperty
    var branch: String?

    @JvmField
    @get:Nested
    val extraInfo: IvyExtraInfoSpec?

    /**
     * Adds a new extra info element to the publication
     */
    fun extraInfo(namespace: String?, elementName: String?, value: String?)

    /**
     * Creates, configures and adds a license to this publication.
     *
     * @since 4.8
     */
    fun license(action: Action<in IvyModuleDescriptorLicense?>?)

    /**
     * Creates, configures and adds an author to this publication.
     *
     * @since 4.8
     */
    fun author(action: Action<in IvyModuleDescriptorAuthor?>?)

    /**
     * Configures the description for this publication.
     *
     * @since 4.8
     */
    fun description(action: Action<in IvyModuleDescriptorDescription?>?)
}
