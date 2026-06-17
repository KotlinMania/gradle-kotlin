/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.internal

import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.internal.IdeProjectMetadata
import java.io.File

class EclipseProjectMetadata(private val eclipseModel: EclipseModel, private val projectDir: File?, private val generatorTask: TaskProvider<out Task?>) : IdeProjectMetadata {
    override fun getDisplayName(): DisplayName {
        return Describables.withTypeAndName("Eclipse project", eclipseModel.getProject().getName())
    }

    val name: String?
        get() = eclipseModel.getProject().getName()

    override fun getFile(): File {
        return File(projectDir, ".project")
    }

    override fun getGeneratorTasks(): MutableSet<out Task?> {
        return mutableSetOf(generatorTask.get())
    }

    fun hasJavaTestFixtures(): Boolean {
        return eclipseModel.getClasspath().getContainsTestFixtures().get()
    }
}
