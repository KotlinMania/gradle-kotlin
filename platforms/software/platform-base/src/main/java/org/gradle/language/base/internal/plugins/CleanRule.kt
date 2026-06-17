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
package org.gradle.language.base.internal.plugins

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Rule
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskContainer

class CleanRule(private val tasks: TaskContainer) : Rule {
    override fun getDescription(): String {
        return "Pattern: " + CLEAN + "<TaskName>: Cleans the output files of a task."
    }

    override fun toString(): String {
        return "Rule: " + getDescription()
    }

    override fun apply(taskName: String) {
        if (!taskName.startsWith(CLEAN) || taskName == CLEAN) {
            return
        }
        val targetTaskName: String = taskName.substring(CLEAN.length)
        if (Character.isLowerCase(targetTaskName.get(0))) {
            return
        }

        val task = tasks.findByName(StringUtils.uncapitalize(targetTaskName))
        if (task == null) {
            return
        }

        @Suppress("deprecation") val clean = tasks.create<Delete>(taskName, Delete::class.java)
        clean.delete(task.getOutputs().getFiles())
    }

    companion object {
        const val CLEAN: String = "clean"
    }
}
