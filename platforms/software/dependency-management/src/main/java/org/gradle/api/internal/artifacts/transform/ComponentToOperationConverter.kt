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
package org.gradle.api.internal.artifacts.transform

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.operations.dependencies.variants.ComponentIdentifier
import org.gradle.operations.dependencies.variants.OpaqueComponentIdentifier

object ComponentToOperationConverter {
    fun convertComponentIdentifier(componentId: org.gradle.api.artifacts.component.ComponentIdentifier): ComponentIdentifier {
        if (componentId is ProjectComponentIdentifier) {
            val projectComponentIdentifier = componentId
            return object : org.gradle.operations.dependencies.variants.ProjectComponentIdentifier {
                val buildPath: String
                    get() = projectComponentIdentifier.getBuild().getBuildPath()

                val projectPath: String
                    get() = projectComponentIdentifier.getProjectPath()

                override fun toString(): String {
                    return projectComponentIdentifier.getDisplayName()
                }
            }
        } else if (componentId is ModuleComponentIdentifier) {
            val moduleComponentIdentifier = componentId
            return object : org.gradle.operations.dependencies.variants.ModuleComponentIdentifier {
                val group: String
                    get() = moduleComponentIdentifier.getGroup()

                val module: String
                    get() = moduleComponentIdentifier.getModule()

                val version: String
                    get() = moduleComponentIdentifier.getVersion()

                override fun toString(): String {
                    return moduleComponentIdentifier.getDisplayName()
                }
            }
        } else {
            return object : OpaqueComponentIdentifier {
                val displayName: String
                    get() = componentId.getDisplayName()

                val className: String
                    get() = componentId.javaClass.getName()

                override fun toString(): String {
                    return componentId.getDisplayName()
                }
            }
        }
    }
}
