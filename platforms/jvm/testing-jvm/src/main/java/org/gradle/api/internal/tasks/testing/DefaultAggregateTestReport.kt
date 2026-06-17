/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.tasks.testing

import org.gradle.api.Action
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.AggregateTestReport
import org.gradle.api.tasks.testing.TestReport
import org.gradle.language.base.plugins.LifecycleBasePlugin
import javax.inject.Inject

abstract class DefaultAggregateTestReport @Inject constructor(name: String, tasks: TaskContainer) : AggregateTestReport {
    private val name: String?
    private val reportTask: TaskProvider<TestReport?>

    init {
        this.name = name
        reportTask = tasks.register<TestReport?>(name, TestReport::class.java, object : Action<TestReport?> {
            override fun execute(task: TestReport) {
                task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP)
                task.setDescription("Generates aggregated test report.")
            }
        })
    }

    override fun getName(): String? {
        return name
    }

    override fun getReportTask(): TaskProvider<TestReport?> {
        return reportTask
    }
}
