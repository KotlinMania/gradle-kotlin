/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.LibraryComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import org.gradle.util.internal.GUtil

class DefaultComponentSelectorConverter(private val localComponentRegistry: LocalComponentRegistry) : ComponentSelectorConverter {
    override fun getModuleVersionId(selector: ComponentSelector?): ModuleVersionIdentifier {
        if (selector is ModuleComponentSelector) {
            val moduleSelector = selector
            return DefaultModuleVersionIdentifier.Companion.newId(moduleSelector.getModuleIdentifier(), moduleSelector.getVersion())
        }
        if (selector is DefaultProjectComponentSelector) {
            val projectSelector = selector
            val projectId = projectSelector.toIdentifier()
            val projectComponent = localComponentRegistry.getComponent(projectId)
            val moduleVersionId: ModuleVersionIdentifier = projectComponent.moduleVersionId
            return DefaultModuleVersionIdentifier.Companion.newId(moduleVersionId.getModule(), moduleVersionId.getVersion())
        }
        if (selector is LibraryComponentSelector) {
            val libraryComponentSelector = selector
            val libraryName = GUtil.elvis<String?>(libraryComponentSelector.getLibraryName(), "")
            return DefaultModuleVersionIdentifier.Companion.newId(DefaultModuleIdentifier.Companion.newId(libraryComponentSelector.getProjectPath(), libraryName), "undefined")
        }
        throw IllegalArgumentException("Unrecognized component selector: " + selector)
    }
}
