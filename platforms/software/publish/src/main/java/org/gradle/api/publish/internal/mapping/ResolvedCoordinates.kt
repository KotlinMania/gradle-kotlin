/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.publish.internal.mapping

import org.gradle.api.artifacts.ModuleVersionIdentifier

/**
 * Represents the coordinates that a declared dependency should be published to.
 * Similar to [ModuleVersionIdentifier], but allows a null version.
 */
interface ResolvedCoordinates {
    /**
     * The group of the dependency to publish.
     */
    val group: String?

    /**
     * The name of the dependency to publish.
     */
    val name: String?

    /**
     * The version of the dependency to publish.
     */
    val version: String?

    companion object {
        fun create(group: String, name: String, version: String?): ResolvedCoordinates {
            return object : ResolvedCoordinates {
                override fun getGroup(): String {
                    return group
                }

                override fun getName(): String {
                    return name
                }

                override fun getVersion(): String {
                    return version!!
                }
            }
        }

        // Returns a separate implementation than `create` to avoid deconstructing the identifier.
        fun create(identifier: ModuleVersionIdentifier): ResolvedCoordinates {
            return object : ResolvedCoordinates {
                override fun getGroup(): String {
                    return identifier.getGroup()
                }

                override fun getName(): String {
                    return identifier.getName()
                }

                override fun getVersion(): String {
                    return identifier.getVersion()
                }
            }
        }
    }
}
