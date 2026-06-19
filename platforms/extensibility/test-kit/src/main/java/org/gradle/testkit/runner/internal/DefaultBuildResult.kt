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
import java.io.BufferedReader
import java.io.IOException
import java.nio.charset.Charset
import java.util.Collections

class DefaultBuildResult(private val outputSource: ByteSource, private val taskList: List<BuildTask?>) : BuildResult {
    constructor(output: String, tasks: List<BuildTask?>) : this(ByteSource.wrap(output.toByteArray(Charset.defaultCharset())), tasks)

    override val output: String
        get() {
            try {
                return outputSource.asCharSource(Charset.defaultCharset()).read()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }

    override val outputReader: BufferedReader
        get() {
            try {
                return outputSource.asCharSource(Charset.defaultCharset()).openBufferedStream()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }

    override val tasks: MutableList<BuildTask?>
        get() = Collections.unmodifiableList(taskList)

    override fun tasks(outcome: TaskOutcome?): MutableList<BuildTask?> {
        return Collections.unmodifiableList(taskList.filter { element -> element!!.outcome == outcome })
    }

    override fun taskPaths(outcome: TaskOutcome?): MutableList<String?> {
        return Collections.unmodifiableList(tasks(outcome).map { task -> task!!.path })
    }

    override fun task(taskPath: String?): BuildTask? {
        for (task in taskList) {
            if (task!!.path == taskPath) {
                return task
            }
        }

        return null
    }
}
