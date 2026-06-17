/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.tooling

/**
 * Used to execute a [BuildAction] in the build process.
 *
 * @param <T> The type of result produced by this executer.
 * @since 1.8
</T> */
interface BuildActionExecuter<T> : ConfigurableLauncher<BuildActionExecuter<T?>?> {
    /**
     * Builder for a build action that hooks into different phases of the build.
     *
     *
     * A single [BuildAction] is allowed per build phase. Use composite actions if needed.
     *
     * @since 4.8
     */
    interface Builder {
        /**
         * Executes the given action after projects are loaded and sends its result to the given result handler.
         *
         *
         * Action will be executed after projects are loaded and Gradle will configure projects as necessary for the models requested.
         *
         *
         * If the operation fails, build will fail with the appropriate exception. The handler won't be notified in case of failure.
         *
         * @param buildAction The action to run in the specified build phase.
         * @param handler The handler to supply the result of the given action to.
         * @param <T> The returning type of the action.
         * @return The builder.
         * @throws IllegalArgumentException If an action has already been added to this build phase. Multiple actions per phase are not supported yet.
        </T> */
        @Throws(IllegalArgumentException::class)
        fun <T> projectsLoaded(buildAction: BuildAction<T?>?, handler: IntermediateResultHandler<in T?>?): Builder?

        /**
         * Executes the given action after tasks are run and sends its result to the given result handler.
         *
         *
         * If the operation fails, build will fail with the appropriate exception. The handler won't be notified in case of failure.
         *
         * @param buildAction The action to run in the specified build phase.
         * @param handler The handler to supply the result of the given action to.
         * @param <T> The returning type of the action.
         * @return The builder.
         * @throws IllegalArgumentException If an action has already been added to this build phase. Multiple actions per phase are not supported yet.
        </T> */
        @Throws(IllegalArgumentException::class)
        fun <T> buildFinished(buildAction: BuildAction<T?>?, handler: IntermediateResultHandler<in T?>?): Builder?

        /**
         * Builds the executer from the added actions.
         *
         * @return The executer.
         */
        fun build(): BuildActionExecuter<Void?>?
    }

    /**
     * Sets the listener to use to streamed values sent from the action via [BuildController.send].
     * Replaces the current listener.
     *
     * @since 8.6
     */
    fun setStreamedValueListener(listener: StreamedValueListener?)

    /**
     *
     * Specifies the tasks to execute before executing the BuildAction.
     *
     *
     * If not configured or a null array, then no tasks will be executed. If an empty array, the default tasks for the build will be executed.
     *
     *
     * If the target Gradle version is &gt;=6.8 then you can execute tasks from included builds. You can target tasks from included builds by specifying the task identity path (i.e. `':included-build-name:subproject-name:taskName'`).
     *
     * @param tasks The paths of the tasks to be executed. Relative paths are evaluated relative to the project for which this launcher was created. An empty list will run the project's default tasks.
     * @return this
     * @since 3.5
     */
    fun forTasks(vararg tasks: String?): BuildActionExecuter<T?>?

    /**
     *
     * Specifies the tasks to execute before executing the BuildAction.
     *
     *
     * If not configured or a null iterable, then no tasks will be executed. If an empty iterable, the default tasks for the build will be executed.
     *
     *
     * If the target Gradle version is &gt;=6.8 then you can execute tasks from included builds. You can target tasks from included builds by specifying the task identity path (i.e. `':included-build-name:subproject-name:taskName'`).
     *
     * @param tasks The paths of the tasks to be executed. Relative paths are evaluated relative to the project for which this launcher was created. An empty list will run the project's default tasks.
     * @return this
     * @since 3.5
     */
    fun forTasks(tasks: Iterable<String?>?): BuildActionExecuter<T?>?

    /**
     * Runs the action, blocking until its result is available.
     *
     * @throws UnsupportedVersionException When the target Gradle version does not support build action execution.
     * @throws org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException When the target Gradle version does not support some requested configuration option.
     * @throws org.gradle.tooling.exceptions.UnsupportedBuildArgumentException When there is a problem with build arguments provided by [.withArguments].
     * @throws BuildActionFailureException When the build action fails with an exception.
     * @throws BuildCancelledException When the operation was cancelled before it completed successfully.
     * @throws BuildException On some failure executing the Gradle build.
     * @throws GradleConnectionException On some other failure using the connection.
     * @throws IllegalStateException When the connection has been closed or is closing.
     * @since 1.8
     */
    @Throws(GradleConnectionException::class, IllegalStateException::class)
    fun run(): T?

    /**
     * Starts executing the action, passing the result to the given handler when complete. This method returns immediately, and the result is later passed to the given handler's [ ][ResultHandler.onComplete] method.
     *
     *
     * If the operation fails, the handler's [ResultHandler.onFailure] method is called with the appropriate exception. See
     * [.run] for a description of the various exceptions that the operation may fail with.
     *
     * @param handler The handler to supply the result to.
     * @throws IllegalStateException When the connection has been closed or is closing.
     * @since 1.8
     */
    @Throws(IllegalStateException::class)
    fun run(handler: ResultHandler<in T?>?)
}
