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
package org.gradle.api.internal.resolve

import org.gradle.model.ModelMap
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.model.internal.type.ModelType
import org.gradle.model.internal.type.ModelTypes
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.VariantComponent
import org.gradle.platform.base.VariantComponentSpec

class DefaultLocalLibraryResolver : LocalLibraryResolver {
    override fun resolveCandidates(projectModel: ModelRegistry, libraryName: String?): MutableCollection<VariantComponent?> {
        val librarySpecs: MutableList<VariantComponent?> = ArrayList<VariantComponent?>()
        collectLocalComponents(projectModel, "components", librarySpecs)
        collectLocalComponents(projectModel, "testSuites", librarySpecs)
        if (librarySpecs.isEmpty()) {
            return mutableListOf<VariantComponent?>()
        }
        return librarySpecs
    }

    private fun collectLocalComponents(projectModel: ModelRegistry, container: String?, librarySpecs: MutableList<VariantComponent?>) {
        val components: ModelMap<ComponentSpec?>? = projectModel.find<ModelMap<ComponentSpec?>?>(container, COMPONENT_MAP_TYPE)
        if (components != null) {
            val libraries: ModelMap<out VariantComponentSpec?> = components.withType<VariantComponentSpec?>(VariantComponentSpec::class.java)
            librarySpecs.addAll(libraries.values())
        }
    }

    companion object {
        private val COMPONENT_MAP_TYPE: ModelType<ModelMap<ComponentSpec?>?>? = ModelTypes.modelMap<ComponentSpec?>(ComponentSpec::class.java)
    }
}
