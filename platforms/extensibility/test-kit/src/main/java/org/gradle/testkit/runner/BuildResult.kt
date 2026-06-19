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
package org.gradle.testkit.runner

import org.gradle.api.Incubating
import java.io.BufferedReader

/**
 * The result of executing a build, via the [GradleRunner].
 *
 * @since 2.6
 * @see GradleRunner.build
 * @see GradleRunner.buildAndFail
 */
interface BuildResult {
    /**
     * The textual output produced during the build.
     *
     *
     * This is equivalent to the console output produced when running a build from the command line.
     * It contains both the standard output, and standard error output, of the build.
     *
     * @return the build output, or an empty string if there was no build output (e.g. ran with `-q`)
     * @since 2.9
     */
    val output: String?

    @get:Incubating
    val outputReader: BufferedReader?

    /**
     * The tasks that were part of the build.
     *
     *
     * The order of the tasks corresponds to the order in which the tasks were started.
     * If executing a parallel enabled build, the order is not guaranteed to be deterministic.
     *
     *
     * The returned list is an unmodifiable view of items.
     * The returned list will be empty if no tasks were executed.
     * This can occur if the build fails early, due to a build script failing to compile for example.
     *
     * @return the build tasks
     */
    val tasks: MutableList<BuildTask?>?

    /**
     * The subset of [.getTasks] that had the given outcome.
     *
     *
     * The returned list is an unmodifiable view of items.
     * The returned list will be empty if no tasks were executed with the given outcome.
     *
     * @param outcome the desired outcome
     * @return the build tasks with the given outcome
     */
    fun tasks(outcome: TaskOutcome?): MutableList<BuildTask?>?

    /**
     * The paths of the subset of [.getTasks] that had the given outcome.
     *
     *
     * The returned list is an unmodifiable view of items.
     * The returned list will be empty if no tasks were executed with the given outcome.
     *
     * @param outcome the desired outcome
     * @return the paths of the build tasks with the given outcome
     */
    fun taskPaths(outcome: TaskOutcome?): MutableList<String?>?

    /**
     * Returns the result object for a particular task, or `null` if the given task was not part of the build.
     *
     * @param taskPath the path of the target task
     * @return information about the executed task, or `null` if the task was not executed
     */
    fun task(taskPath: String?): BuildTask?
}
