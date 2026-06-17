/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.testkit.runner.internal

import com.google.common.io.ByteSource
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.internal.feature.BuildResultOutputFeatureCheck
import org.gradle.testkit.runner.internal.feature.FeatureCheck
import java.io.BufferedReader
import java.nio.charset.Charset

class FeatureCheckBuildResult(
    buildOperationParameters: BuildOperationParameters,
    outputSource: ByteSource,
    tasks: MutableList<BuildTask?>
) : BuildResult {
    private val delegateBuildResult: BuildResult
    private val outputFeatureCheck: FeatureCheck

    constructor(buildOperationParameters: BuildOperationParameters, output: String, tasks: MutableList<BuildTask?>) : this(
        buildOperationParameters,
        ByteSource.wrap(output.toByteArray(Charset.defaultCharset())),
        tasks
    )

    init {
        delegateBuildResult = DefaultBuildResult(outputSource, tasks)
        outputFeatureCheck = BuildResultOutputFeatureCheck(buildOperationParameters.targetGradleVersion!!, buildOperationParameters.isEmbedded)
    }

    override fun getOutput(): String? {
        outputFeatureCheck.verify()
        return delegateBuildResult.output
    }

    override fun getOutputReader(): BufferedReader? {
        outputFeatureCheck.verify()
        return delegateBuildResult.outputReader
    }

    override fun getTasks(): MutableList<BuildTask?>? {
        return delegateBuildResult.tasks
    }

    override fun tasks(outcome: TaskOutcome?): MutableList<BuildTask?>? {
        return delegateBuildResult.tasks(outcome)
    }

    override fun taskPaths(outcome: TaskOutcome?): MutableList<String?>? {
        return delegateBuildResult.taskPaths(outcome)
    }

    override fun task(taskPath: String?): BuildTask? {
        return delegateBuildResult.task(taskPath)
    }
}
