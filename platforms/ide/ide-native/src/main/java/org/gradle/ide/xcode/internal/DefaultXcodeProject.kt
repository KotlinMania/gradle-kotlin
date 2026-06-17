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
package org.gradle.ide.xcode.internal

import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.TaskDependency
import org.gradle.ide.xcode.XcodeProject
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

class DefaultXcodeProject @Inject constructor(objectFactory: ObjectFactory) : XcodeProject {
    val groups: Groups
    val targets: MutableList<XcodeTarget> = ArrayList<XcodeTarget>()
    var locationDir: File? = null

    init {
        this.groups = objectFactory.newInstance<Groups>(Groups::class.java)
    }

    val taskDependencies: Callable<MutableList<TaskDependency?>?>
        get() = object : Callable<MutableList<TaskDependency?>?> {
            @Throws(Exception::class)
            override fun call(): MutableList<TaskDependency?> {
                val result: MutableList<TaskDependency?> = ArrayList<TaskDependency?>()
                for (xcodeTarget in this.targets) {
                    result.addAll(xcodeTarget.getTaskDependencies())
                }
                return result
            }
        }

    fun addTarget(target: XcodeTarget?) {
        targets.add(target!!)
    }

    abstract class Groups {
        abstract val root: ConfigurableFileCollection?

        abstract val sources: ConfigurableFileCollection?

        abstract val tests: ConfigurableFileCollection?

        abstract val headers: ConfigurableFileCollection?
    }

    companion object {
        const val BUILD_DEBUG: String = "Debug"
        const val BUILD_RELEASE: String = "Release"
        const val TEST_DEBUG: String = "__GradleTestRunner_Debug"
    }
}
