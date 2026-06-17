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

/**
 * A task that was executed when running a specific build.
 *
 * @since 2.6
 * @see BuildResult
 */
interface BuildTask {
    /**
     * The unique path of the task.
     *
     *
     * The task path is a combination of its enclosing project's path and its name.
     * For example, in multi project build the `bar` task of the `foo` project has a path of `:foo:bar`.
     * In a single project build, the `bar` task of the lone project has a path of `:bar`.
     *
     *
     * This value corresponds to the value output by Gradle for the task during its normal progress logging.
     *
     * @return the task path
     */
    val path: String?

    /**
     * The outcome of attempting to execute this task.
     *
     * @return the task outcome
     */
    val outcome: TaskOutcome?
}
