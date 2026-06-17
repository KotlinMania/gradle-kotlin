/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.reporting.dependents.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier
import org.gradle.model.ModelMap
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.model.internal.type.ModelType
import org.gradle.model.internal.type.ModelTypes
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.internal.ComponentSpecIdentifier

object DependentComponentsUtils {
    fun getBuildScopedTerseName(id: ComponentSpecIdentifier): String {
        return getProjectPrefix(id.getProjectPath()) + id.getProjectScopedName()
    }

    fun getBuildScopedTerseName(id: LibraryBinaryIdentifier): String {
        return getProjectPrefix(id.getProjectPath()) + id.getLibraryName() + Project.PATH_SEPARATOR + id.getVariant()
    }

    private fun getProjectPrefix(projectPath: String): String {
        if (Project.PATH_SEPARATOR == projectPath) {
            return ""
        }
        return projectPath + Project.PATH_SEPARATOR
    }


    fun getAllComponents(registry: ModelRegistry): MutableSet<ComponentSpec> {
        val components: MutableSet<ComponentSpec> = LinkedHashSet<ComponentSpec>()
        val componentSpecs = DependentComponentsUtils.modelElement<ComponentSpecContainer>(registry, "components", ComponentSpecContainer::class.java)
        if (componentSpecs != null) {
            components.addAll(componentSpecs.values())
        }
        return components
    }

    fun getAllTestSuites(registry: ModelRegistry): MutableSet<ComponentSpec> {
        val components: MutableSet<ComponentSpec> = LinkedHashSet<ComponentSpec>()
        val testSuites = modelElement<ModelMap<ComponentSpec>>(registry, "testSuites", ModelTypes.modelMap<ComponentSpec>(ComponentSpec::class.java))
        if (testSuites != null) {
            components.addAll(testSuites.values())
        }
        return components
    }

    private fun <T> modelElement(registry: ModelRegistry, path: String, clazz: Class<T?>): T? {
        return registry.find<T?>(path, clazz)
    }

    private fun <T> modelElement(registry: ModelRegistry, path: String, modelType: ModelType<T?>): T? {
        return registry.find<T?>(path, modelType)
    }
}
