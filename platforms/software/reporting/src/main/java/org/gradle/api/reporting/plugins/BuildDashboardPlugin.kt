/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.reporting.plugins

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.reporting.GenerateBuildDashboard
import org.gradle.api.reporting.Reporting
import org.gradle.api.reporting.ReportingExtension

/**
 * Adds a task, "buildDashboard", that aggregates the output of all tasks that produce reports.
 *
 * @see [Build Dashboard plugin reference](https://docs.gradle.org/current/userguide/build_dashboard_plugin.html)
 */
abstract class BuildDashboardPlugin : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(ReportingBasePlugin::class.java)

        val buildDashboard = project.getTasks().register<GenerateBuildDashboard?>(BUILD_DASHBOARD_TASK_NAME, GenerateBuildDashboard::class.java, object : Action<GenerateBuildDashboard?> {
            override fun execute(buildDashboardTask: GenerateBuildDashboard) {
                buildDashboardTask.setDescription("Generates a dashboard of all the reports produced by this build.")
                buildDashboardTask.setGroup("reporting")

                val htmlReport = buildDashboardTask.getReports().getHtml()
                htmlReport.getOutputLocation().convention(project.getExtensions().getByType<ReportingExtension?>(ReportingExtension::class.java).getBaseDirectory().dir("buildDashboard"))
            }
        })

        for (aProject in project.getAllprojects()) {
            aProject.getTasks().configureEach(object : Action<Task?> {
                override fun execute(task: Task?) {
                    if (task !is Reporting<*>) {
                        return
                    }

                    if (task.getName() != BUILD_DASHBOARD_TASK_NAME) {
                        task.finalizedBy(buildDashboard)
                    }
                }
            })
        }
    }

    companion object {
        const val BUILD_DASHBOARD_TASK_NAME: String = "buildDashboard"
    }
}
