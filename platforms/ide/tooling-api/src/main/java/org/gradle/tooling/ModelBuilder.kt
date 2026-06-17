/*
 * Copyright 2011 the original author or authors.
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
 * A `ModelBuilder` allows you to fetch a snapshot of some model for a project or a build.
 * Instances of `ModelBuilder` are not thread-safe.
 *
 *
 * You use a `ModelBuilder` as follows:
 *
 *
 *  * Create an instance of `ModelBuilder` by calling [ProjectConnection.model].
 *  * Configure the builder as appropriate.
 *  * Call either [.get] or [.get] to build the model.
 *  * Optionally, you can reuse the builder to build the model multiple times.
 *
 *
 * Example:
 * <pre class='autoTested'>
 * ProjectConnection connection = GradleConnector.newConnector()
 * .forProjectDirectory(new File("someFolder"))
 * .connect();
 *
 * try {
 * ModelBuilder&lt;GradleProject&gt; builder = connection.model(GradleProject.class);
 *
 * //configure the standard input in case your build is interactive:
 * builder.setStandardInput(new ByteArrayInputStream("consume this!".getBytes()));
 *
 * //if you want to listen to the progress events:
 * ProgressListener listener = null; // use your implementation
 * builder.addProgressListener(listener);
 *
 * //get the model:
 * GradleProject project = builder.get();
 *
 * //query the model for information:
 * System.out.println("Available tasks: " + project.getTasks());
 * } finally {
 * connection.close();
 * }
</pre> *
 *
 * @param <T> The type of model to build
 * @since 1.0-milestone-3
</T> */
interface ModelBuilder<T> : ConfigurableLauncher<ModelBuilder<T?>?> {
    /**
     *
     * Specifies the tasks to execute before building the model.
     *
     *
     * If not configured, null, or an empty array is passed, then no tasks will be executed.
     *
     *
     * If the target Gradle version is &gt;=6.8 then you can execute tasks from included builds. You can target tasks from included builds by specifying the task identity path (i.e. `':included-build-name:subproject-name:taskName'`).
     *
     * @param tasks The paths of the tasks to be executed. Relative paths are evaluated relative to the project for which this launcher was created.
     * @return this
     * @since 1.2
     */
    fun forTasks(vararg tasks: String?): ModelBuilder<T?>?

    /**
     *
     * Specifies the tasks to execute before building the model.
     *
     *
     * If not configured, null, or an empty array is passed, then no tasks will be executed.
     *
     *
     * If the target Gradle version is &gt;=6.8 then you can execute tasks from included builds. You can target tasks from included builds by specifying the task identity path (i.e. `':included-build-name:subproject-name:taskName'`).
     *
     * @param tasks The paths of the tasks to be executed. Relative paths are evaluated relative to the project for which this launcher was created.
     * @return this
     * @since 2.6
     */
    fun forTasks(tasks: Iterable<String?>?): ModelBuilder<T?>?

    /**
     * Fetch the model, blocking until it is available.
     *
     * @return The model.
     * @throws UnsupportedVersionException When the target Gradle version does not support building models.
     * @throws UnknownModelException When the target Gradle version or build does not support the requested model.
     * @throws org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException
     * When the target Gradle version does not support some requested configuration option such as [.withArguments].
     * @throws org.gradle.tooling.exceptions.UnsupportedBuildArgumentException When there is a problem with build arguments provided by [.withArguments].
     * @throws BuildException On some failure executing the Gradle build.
     * @throws BuildCancelledException When the operation was cancelled before it completed successfully.
     * @throws GradleConnectionException On some other failure using the connection.
     * @throws IllegalStateException When the connection has been closed or is closing.
     * @since 1.0-milestone-3
     */
    @Throws(GradleConnectionException::class, IllegalStateException::class)
    fun get(): T?

    /**
     * Starts fetching the model, passing the result to the given handler when complete. This method returns immediately, and the result is later passed to the given
     * handler's [ResultHandler.onComplete] method.
     *
     *
     * If the operation fails, the handler's [ResultHandler.onFailure]
     * method is called with the appropriate exception. See [.get] for a description of the various exceptions that the operation may fail with.
     *
     * @param handler The handler to supply the result to.
     * @throws IllegalStateException When the connection has been closed or is closing.
     * @since 1.0-milestone-3
     */
    @Throws(IllegalStateException::class)
    fun get(handler: ResultHandler<in T?>?)
}
