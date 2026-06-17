/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.publish.internal.validation

import org.gradle.api.artifacts.ModuleVersionIdentifier
import java.util.Objects

/**
 * A publication associated with a project's display text, for better error feedback.
 */
internal class PublicationWithProject(private val projectDisplayText: String?, private val publicationName: String, val coordinates: ModuleVersionIdentifier?) {
    override fun toString(): String {
        return "'" + publicationName + "' in " + projectDisplayText
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as PublicationWithProject
        return projectDisplayText == that.projectDisplayText && publicationName == that.publicationName && coordinates == that.coordinates
    }

    override fun hashCode(): Int {
        return Objects.hash(projectDisplayText, publicationName, coordinates)
    }
}
