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
package org.gradle.api.publish.ivy

import org.gradle.api.publish.PublicationArtifact
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty

/**
 * An artifact published as part of a [IvyPublication].
 */
interface IvyArtifact : PublicationArtifact {
    /**
     * Sets the name used to publish the artifact file.
     * @param name The name.
     */
    @JvmField
    @get:ToBeReplacedByLazyProperty
    var name: String?

    /**
     * Sets the type used to publish the artifact file.
     * @param type The type.
     */
    @JvmField
    @get:ToBeReplacedByLazyProperty
    var type: String?

    /**
     * Sets the extension used to publish the artifact file.
     * @param extension The extension.
     */
    @JvmField
    @get:ToBeReplacedByLazyProperty
    var extension: String?

    /**
     * Sets the classifier used to publish the artifact file.
     * @param classifier The classifier.
     */
    @JvmField
    @get:ToBeReplacedByLazyProperty
    var classifier: String?

    /**
     * Sets a comma separated list of public configurations in which this artifact is published.
     * The '*' wildcard can be used to designate that the artifact is published in all public configurations.
     * @param conf The value of 'conf' for this artifact.
     */
    @JvmField
    @get:ToBeReplacedByLazyProperty
    var conf: String?
}
