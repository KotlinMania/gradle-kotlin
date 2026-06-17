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
package org.gradle.buildinit.plugins.internal

import org.gradle.api.GradleException
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType
import org.gradle.buildinit.plugins.internal.modifiers.Language
import org.gradle.internal.logging.text.TreeFormatter
import java.util.Arrays
import java.util.TreeMap

class ProjectLayoutSetupRegistry(
    val default: BuildGenerator, // This should turn into a set of converters at some point
    val buildConverter: BuildConverter, // Currently used by `build-logic/build-init-samples/src/main/kotlin/gradlebuild/samples/SamplesGenerator.kt`
    @get:Suppress("unused") val templateOperationFactory: TemplateOperationFactory
) {
    private val registeredProjectDescriptors: MutableMap<String, BuildInitializer> = TreeMap<String, BuildInitializer>()

    init {
        add(default)
        add(buildConverter)
    }

    fun add(descriptor: BuildInitializer) {
        if (registeredProjectDescriptors.containsKey(descriptor.getId())) {
            throw GradleException(String.format("ProjectDescriptor with ID '%s' already registered.", descriptor.getId()))
        }

        registeredProjectDescriptors.put(descriptor.getId(), descriptor)
    }

    // Currently used by `build-logic/build-init-samples/src/main/kotlin/gradlebuild/samples/SamplesGenerator.kt`
    @Suppress("unused")
    fun getLanguagesFor(componentType: ComponentType): MutableList<Language> {
        val generators = getGeneratorsFor(componentType)

        val result: MutableList<Language> = ArrayList<Language>()
        for (language in Language.entries) {
            for (generator in generators) {
                if (generator.productionCodeUses(language)) {
                    result.add(language)
                    break
                }
            }
        }
        return result
    }

    val componentTypes: MutableList<ComponentType>
        /**
         * Returns the component types, in display order.
         */
        get() = Arrays.asList<ComponentType>(*ComponentType.entries.toTypedArray())

    val defaultComponentType: ComponentType
        /**
         * Returns the default component type to use for interactive initialization.
         */
        get() = ComponentType.APPLICATION

    /**
     * Locates the [BuildInitializer] with the given type.
     */
    fun get(type: String): BuildInitializer {
        if (!registeredProjectDescriptors.containsKey(type)) {
            val formatter = TreeFormatter()
            formatter.node("The requested build type '" + type + "' is not supported. Supported types")
            formatter.startChildren()
            for (candidate in this.allTypes) {
                formatter.node("'" + candidate + "'")
            }
            formatter.endChildren()
            throw GradleException(formatter.toString())
        }
        return registeredProjectDescriptors.get(type)!!
    }

    private val generators: MutableList<BuildGenerator>
        get() {
            val result: MutableList<BuildGenerator> =
                ArrayList<BuildGenerator>(registeredProjectDescriptors.size)
            for (initializer in registeredProjectDescriptors.values) {
                if (initializer is BuildGenerator) {
                    result.add(initializer)
                }
            }
            return result
        }

    fun getGeneratorsFor(componentType: ComponentType): MutableList<BuildGenerator> {
        val generators = this.generators
        val result: MutableList<BuildGenerator> = ArrayList<BuildGenerator>(generators.size)
        for (generator in generators) {
            if (generator.getComponentType() == componentType) {
                result.add(generator)
            }
        }
        return result
    }

    val allTypes: MutableList<String>
        get() {
            val result: MutableList<String> = ArrayList<String>(registeredProjectDescriptors.size)
            for (initDescriptor in registeredProjectDescriptors.values) {
                result.add(initDescriptor.getId())
            }
            return result
        }
}
