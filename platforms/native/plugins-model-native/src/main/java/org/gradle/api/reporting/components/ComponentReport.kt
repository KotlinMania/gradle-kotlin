/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.reporting.components

import org.gradle.api.DefaultTask
import org.gradle.api.internal.ConfigurationCacheDegradation
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.reporting.components.internal.ComponentReportRenderer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.internal.logging.text.StyledTextOutputFactory.create
import org.gradle.language.base.ProjectSourceSet
import org.gradle.model.ModelMap
import org.gradle.model.internal.type.ModelType
import org.gradle.model.internal.type.ModelTypes
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Displays some details about the software components produced by the project.
 */
@Deprecated("")
@DisableCachingByDefault(because = "Produces only non-cacheable console output")
abstract class ComponentReport @Inject constructor() : DefaultTask() {
    @get:Inject
    protected abstract val textOutputFactory: StyledTextOutputFactory?

    @get:Inject
    protected abstract val fileResolver: FileResolver?

    @get:Inject
    protected abstract val modelRegistry: ModelRegistry?

    @get:Inject
    protected abstract val binaryRenderer: TypeAwareBinaryRenderer?

    init {
        ConfigurationCacheDegradation.requireDegradation<ComponentReport>(this, "Task is not compatible with the Configuration Cache")
    }

    @TaskAction
    fun report() {
        whileDisabled(Runnable { this.doReport() })
    }

    private fun doReport() {
        val project = getProject() as ProjectInternal
        project.prepareForRuleBasedPlugins()

        val textOutput = this.textOutputFactory.create(ComponentReport::class.java)
        val renderer = ComponentReportRenderer(
            this.fileResolver,
            this.binaryRenderer
        )
        renderer.setOutput(textOutput)

        val projectDetails = ProjectDetails.of(project)
        renderer.startProject(projectDetails)

        val components: MutableCollection<ComponentSpec> = ArrayList<ComponentSpec>()
        val componentSpecs = modelElement<ComponentSpecContainer>("components", ComponentSpecContainer::class.java)
        if (componentSpecs != null) {
            components.addAll(componentSpecs.values())
        }

        val testSuites = modelElement<ModelMap<ComponentSpec>>("testSuites", ModelTypes.modelMap<ComponentSpec>(ComponentSpec::class.java))!!
        if (testSuites != null) {
            components.addAll(testSuites.values())
        }

        renderer.renderComponents(components)

        val sourceSets = modelElement<ProjectSourceSet>("sources", ProjectSourceSet::class.java)
        if (sourceSets != null) {
            renderer.renderSourceSets(sourceSets)
        }
        val binaries = modelElement<BinaryContainer>("binaries", BinaryContainer::class.java)
        if (binaries != null) {
            renderer.renderBinaries(binaries.values())
        }

        renderer.completeProject(projectDetails)
        renderer.complete()
    }

    private fun <T> modelElement(path: String, clazz: Class<T?>): T? {
        return this.modelRegistry.find<T?>(path, clazz)
    }

    private fun <T> modelElement(path: String, modelType: ModelType<T?>): T? {
        return this.modelRegistry.find<T?>(path, modelType)
    }
}
