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
package org.gradle.testing.jacoco.tasks

import org.gradle.api.Action
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.jacoco.AntJacocoCheck
import org.gradle.internal.jacoco.JacocoCoverageParameters
import org.gradle.internal.jacoco.rules.JacocoViolationRulesContainerImpl
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRulesContainer
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Task for verifying code coverage metrics. Fails the task if violations are detected based on specified rules.
 *
 *
 * Requires JaCoCo version &gt;= 0.6.3.
 *
 * @since 3.4
 */
@CacheableTask
abstract class JacocoCoverageVerification : JacocoReportBase() {
    /**
     * Returns the violation rules set for this task.
     *
     * @return Violation rules container
     */
    @get:Nested
    val violationRules: JacocoViolationRulesContainer

    @get:OutputFile
    protected val dummyOutputFile: File
        /**
         * For internal use only. This property exists, because only tasks with outputs can be up-to-date and cached.
         */
        get() = File(getTemporaryDir(), "success.txt")

    private val projectName = getProject().getName()

    init {
        val instantiator = getInstantiator()
        violationRules = instantiator.newInstance<JacocoViolationRulesContainerImpl>(JacocoViolationRulesContainerImpl::class.java, instantiator)
    }

    /**
     * Configures the violation rules for this task.
     */
    fun violationRules(configureAction: Action<in JacocoViolationRulesContainer?>): JacocoViolationRulesContainer {
        configureAction.execute(violationRules)
        return violationRules
    }

    @get:Inject
    protected abstract val workerExecutor: WorkerExecutor?

    @TaskAction
    @Throws(IOException::class)
    fun check() {
        val queue = this.workerExecutor.classLoaderIsolation()
        queue.submit<JacocoCoverageParameters?>(AntJacocoCheck::class.java, Action { parameters: JacocoCoverageParameters? ->
            parameters!!.antLibraryClasspath.convention(getJacocoClasspath())
            parameters.projectName.convention(projectName)
            parameters.encoding.convention(getSourceEncoding())
            parameters.allSourcesDirs.convention(getAllSourceDirs())
            parameters.allClassesDirs.convention(getAllClassDirs())
            parameters.executionData.convention(getExecutionData())

            parameters.failOnViolation.convention(this.violationRules.isFailOnViolation())
            parameters.rules.convention(this.violationRules.getRules())
            parameters.dummyOutputFile.fileValue(this.dummyOutputFile)
        })
    }
}
