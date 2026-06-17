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
package org.gradle.api.reporting.dependents

import com.google.common.base.Joiner
import com.google.common.collect.Lists
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.ConfigurationCacheDegradation
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.reporting.dependents.internal.DependentComponentsUtils
import org.gradle.api.reporting.dependents.internal.TextDependentComponentsReportRenderer
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails
import org.gradle.api.tasks.options.Option
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.internal.logging.text.StyledTextOutputFactory.create
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.internal.dependents.DependentBinariesResolver
import org.gradle.work.DisableCachingByDefault
import java.util.function.Consumer
import javax.inject.Inject

/**
 * Displays dependent components.
 */
@Deprecated("")
@DisableCachingByDefault(because = "Produces only non-cacheable console output")
abstract class DependentComponentsReport @Inject constructor() : DefaultTask() {
    /**
     * Should this include non-buildable components in the report?
     */
    @get:Console
    @set:Option(option = "non-buildable", description = "Show non-buildable components.")
    var isShowNonBuildable: Boolean = false

    /**
     * Should this include test suites in the report?
     */
    @get:Console
    @set:Option(option = "test-suites", description = "Show test suites components.")
    var isShowTestSuites: Boolean = false
    private var components: MutableList<String>? = null

    init {
        ConfigurationCacheDegradation.requireDegradation<DependentComponentsReport>(this, "Task is not compatible with the Configuration Cache")
    }

    @get:Console
    @set:Option(option = "all", description = "Show all components (non-buildable and test suites).")
    var showAll: Boolean
        /**
         * Should this include both non-buildable and test suites in the report?
         */
        get() = this.isShowNonBuildable && this.isShowTestSuites
        /**
         * Set this to include both non buildable components and test suites in the report.
         */
        set(showAll) {
            this.isShowNonBuildable = showAll
            this.isShowTestSuites = showAll
        }

    /**
     * Returns the components to generate the report for.
     * Defaults to all components of this project.
     *
     * @return the components.
     */
    @Console
    fun getComponents(): MutableList<String> {
        return components!!
    }

    /**
     * Sets the components to generate the report for.
     *
     * @param components the components.
     */
    @Option(option = "component", description = "Component to generate the report for (can be specified more than once).")
    fun setComponents(components: MutableList<String>) {
        this.components = components
    }

    @get:Inject
    protected abstract val textOutputFactory: StyledTextOutputFactory?

    @get:Inject
    protected abstract val modelRegistry: ModelRegistry

    @get:Inject
    protected abstract val workerLeaseService: WorkerLeaseService?

    @TaskAction
    fun report() {
        whileDisabled(Runnable { this.doReport() })
    }

    private fun doReport() {
        // Once we are here, the project lock is held. If we synchronize to avoid cross-project operations, we will have a dead lock.
        this.workerLeaseService.runAsIsolatedTask(Runnable {
            // Output reports per execution, not mixed.
            // Cross-project ModelRegistry operations do not happen concurrently.
            synchronized(DependentComponentsReport::class.java) {
                (getProject() as ProjectInternal).getOwner().applyToMutableState(Consumer { project: ProjectInternal? ->
                    val modelRegistry = this.modelRegistry
                    val dependentBinariesResolver = modelRegistry.find<DependentBinariesResolver>("dependentBinariesResolver", DependentBinariesResolver::class.java)

                    val textOutput = this.textOutputFactory.create(DependentComponentsReport::class.java)
                    val reportRenderer = TextDependentComponentsReportRenderer(
                        dependentBinariesResolver,
                        this.isShowNonBuildable,
                        this.isShowTestSuites
                    )

                    reportRenderer.setOutput(textOutput)
                    val projectDetails = ProjectDetails.of(project)
                    reportRenderer.startProject(projectDetails)

                    val allComponents = DependentComponentsUtils.getAllComponents(modelRegistry)
                    if (this.isShowTestSuites) {
                        allComponents.addAll(DependentComponentsUtils.getAllTestSuites(modelRegistry))
                    }
                    reportRenderer.renderComponents(getReportedComponents(allComponents))
                    reportRenderer.renderLegend()

                    reportRenderer.completeProject(projectDetails)
                    reportRenderer.complete()
                })
            }
        })
    }

    private fun getReportedComponents(allComponents: MutableSet<ComponentSpec>): MutableSet<ComponentSpec> {
        if (components == null || components!!.isEmpty()) {
            return allComponents
        }
        val reportedComponents: MutableSet<ComponentSpec> = LinkedHashSet<ComponentSpec>()
        val notFound: MutableList<String> = Lists.newArrayList<String>(components)
        for (candidate in allComponents) {
            val candidateName = candidate.getName()
            if (components!!.contains(candidateName!!)) {
                reportedComponents.add(candidate)
                notFound.remove(candidateName)
            }
        }
        if (!notFound.isEmpty()) {
            onComponentsNotFound(notFound)
        }
        return reportedComponents
    }

    private fun onComponentsNotFound(notFound: MutableList<String>) {
        val error = StringBuilder("Component")
        if (notFound.size == 1) {
            error.append(" '").append(notFound.get(0))
        } else {
            val last = notFound.removeAt(notFound.size - 1)
            error.append("s '").append(Joiner.on("', '").join(notFound)).append("' and '").append(last)
        }
        error.append("' not found.")
        throw InvalidUserDataException(error.toString())
    }
}
