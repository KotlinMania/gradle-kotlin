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
import org.gradle.util.internal.CollectionUtils
import org.gradle.util.internal.CollectionUtils.collect
import java.io.BufferedReader
import java.io.IOException
import java.nio.charset.Charset
import java.util.Collections
import java.util.function.Function

class DefaultBuildResult(private val outputSource: ByteSource, private val tasks: MutableList<BuildTask>) : BuildResult {
    constructor(output: String, tasks: MutableList<BuildTask>) : this(ByteSource.wrap(output.toByteArray(Charset.defaultCharset())), tasks)

    override fun getOutput(): String {
        try {
            return outputSource.asCharSource(Charset.defaultCharset()).read()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    override fun getOutputReader(): BufferedReader {
        try {
            return outputSource.asCharSource(Charset.defaultCharset()).openBufferedStream()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    override fun getTasks(): MutableList<BuildTask?> {
        return Collections.unmodifiableList<BuildTask?>(tasks)
    }

    override fun tasks(outcome: TaskOutcome?): MutableList<BuildTask?> {
        return Collections.unmodifiableList<BuildTask?>(CollectionUtils.filter<BuildTask?>(tasks, org.gradle.api.specs.Spec { element: BuildTask? -> element!!.outcome == outcome }))
    }

    override fun taskPaths(outcome: TaskOutcome?): MutableList<String?> {
        return Collections.unmodifiableList<String?>(collect<String?, BuildTask?>(tasks(outcome), Function { obj: BuildTask? -> obj!!.path }))
    }

    override fun task(taskPath: String?): BuildTask? {
        for (task in tasks) {
            if (task.path == taskPath) {
                return task
            }
        }

        return null
    }
}
