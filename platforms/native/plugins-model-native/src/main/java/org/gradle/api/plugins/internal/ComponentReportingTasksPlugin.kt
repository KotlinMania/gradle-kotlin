/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.api.plugins.internal

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.reporting.components.ComponentReport
import org.gradle.api.reporting.dependents.DependentComponentsReport
import org.gradle.api.tasks.diagnostics.internal.DiagnosticsTaskNames
import org.jspecify.annotations.NullMarked

/**
 * Plugin that adds tasks to report on the deprecated software model components configured for the project.
 *
 * @since 9.0.0
 */
@NullMarked
abstract class ComponentReportingTasksPlugin : Plugin<Project> {
    @Suppress("deprecation")
    override fun apply(project: Project) {
        project.getTasks().register<ComponentReport>(DiagnosticsTaskNames.COMPONENTS_TASK, ComponentReport::class.java, ComponentReportAction(project.toString()))
        project.getTasks()
            .register<DependentComponentsReport>(DiagnosticsTaskNames.DEPENDENT_COMPONENTS_TASK, DependentComponentsReport::class.java, DependentComponentsReportAction(project.toString()))
    }

    @Suppress("deprecation")
    private class ComponentReportAction(private val projectName: String) : Action<ComponentReport> {
        override fun execute(task: ComponentReport) {
            task.setDescription("Displays the components produced by " + projectName + ". [deprecated]")
            task.setImpliesSubProjects(true)
        }
    }

    @Suppress("deprecation")
    private class DependentComponentsReportAction(private val projectName: String) : Action<DependentComponentsReport> {
        override fun execute(task: DependentComponentsReport) {
            task.setDescription("Displays the dependent components of components in " + projectName + ". [deprecated]")
            task.setImpliesSubProjects(true)
        }
    }
}
