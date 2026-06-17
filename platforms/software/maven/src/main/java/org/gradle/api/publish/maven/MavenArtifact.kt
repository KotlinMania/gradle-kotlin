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
package org.gradle.api.publish.maven

import org.gradle.api.publish.PublicationArtifact
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty

/**
 * An artifact published as part of a [MavenPublication].
 */
interface MavenArtifact : PublicationArtifact {
    /**
     * Sets the extension used to publish the artifact file.
     * @param extension The extension.
     */
    @get:ToBeReplacedByLazyProperty
    var extension: String?

    /**
     * Sets the classifier used to publish the artifact file.
     * @param classifier The classifier.
     */
    @get:ToBeReplacedByLazyProperty
    var classifier: String?
}
