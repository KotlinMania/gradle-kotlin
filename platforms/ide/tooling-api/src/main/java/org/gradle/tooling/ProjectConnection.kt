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

import java.io.Closeable
import java.io.File
import java.nio.file.Path
import org.gradle.tooling.model.gradle.GradleBuild

/**
 *
 * Represents a long-lived connection to a Gradle project. You obtain an instance of a `ProjectConnection` by using [GradleConnector.connect].
 *
 * <pre class='autoTested'>
 *
 * try (ProjectConnection connection = GradleConnector.newConnector()
 * .forProjectDirectory(new File("someFolder"))
 * .connect()) {
 *
 * //obtain some information from the build
 * BuildEnvironment environment = connection.model(BuildEnvironment.class).get();
 *
 * //run some tasks
 * connection.newBuild()
 * .forTasks("tasks")
 * .setStandardOutput(System.out)
 * .run();
 *
 * }
</pre> *
 *
 * <h2>Thread safety information</h2>
 *
 *
 * All implementations of `ProjectConnection` are thread-safe, and may be shared by any number of threads.
 *
 *
 * All notifications from a given `ProjectConnection` instance are delivered by a single thread at a time. Note, however, that the delivery thread may change over time.
 *
 * @since 1.0-milestone-3
 */
interface ProjectConnection : Closeable {
    /**
     * Fetches a snapshot of the model of the given type for this project. This method blocks until the model is available.
     *
     *
     * This method is simply a convenience for calling `model(modelType).get()`
     *
     * @param modelType The model type.
     * @param <T> The model type.
     * @return The model.
     * @throws UnsupportedVersionException When the target Gradle version does not support the given model.
     * @throws UnknownModelException When the target Gradle version or build does not support the requested model.
     * @throws BuildException On some failure executing the Gradle build, in order to build the model.
     * @throws GradleConnectionException On some other failure using the connection.
     * @throws IllegalStateException When this connection has been closed or is closing.
     * @since 1.0-milestone-3
    </T> */
    @Throws(GradleConnectionException::class, IllegalStateException::class)
    fun <T> getModel(modelType: Class<T?>?): T?

    /**
     * Starts fetching a snapshot of the given model, passing the result to the given handler when complete. This method returns immediately, and the result is later
     * passed to the given handler's [ResultHandler.onComplete] method.
     *
     *
     * If the operation fails, the handler's [ResultHandler.onFailure] method is called with the appropriate exception.
     * See [.getModel] for a description of the various exceptions that the operation may fail with.
     *
     *
     * This method is simply a convenience for calling `model(modelType).get(handler)`
     *
     * @param modelType The model type.
     * @param handler The handler to pass the result to.
     * @param <T> The model type.
     * @throws IllegalStateException When this connection has been closed or is closing.
     * @since 1.0-milestone-3
    </T> */
    @Throws(IllegalStateException::class)
    fun <T> getModel(modelType: Class<T?>?, handler: ResultHandler<in T?>?)

    /**
     * Creates a launcher which can be used to execute a build.
     *
     *
     * Requires Gradle 1.0-milestone-8 or later.
     *
     * @return The launcher.
     * @since 1.0-milestone-3
     */
    fun newBuild(): BuildLauncher?

    /**
     * Creates a test launcher which can be used to execute tests.
     *
     *
     * Requires Gradle 4.0 or later.
     *
     * @return The launcher.
     * @since 2.6
     */
    fun newTestLauncher(): TestLauncher?

    /**
     * Creates a builder which can be used to query the model of the given type.
     *
     *
     * Any of following models types may be available, depending on the version of Gradle being used by the target
     * build:
     *
     *
     *  * [org.gradle.tooling.model.gradle.GradleBuild]
     *  * [org.gradle.tooling.model.build.BuildEnvironment]
     *  * [org.gradle.tooling.model.build.Help]
     *  * [org.gradle.tooling.model.GradleProject]
     *  * [org.gradle.tooling.model.gradle.BuildInvocations]
     *  * [org.gradle.tooling.model.gradle.ProjectPublications]
     *  * [org.gradle.tooling.model.idea.IdeaProject]
     *  * [org.gradle.tooling.model.idea.BasicIdeaProject]
     *  * [org.gradle.tooling.model.eclipse.EclipseProject]
     *  * [org.gradle.tooling.model.eclipse.HierarchicalEclipseProject]
     *
     *
     *
     * A build may also expose additional custom tooling models. You can use this method to query these models.
     *
     *
     * Requires Gradle 1.0-milestone-8 or later.
     *
     * @param modelType The model type
     * @param <T> The model type.
     * @return The builder.
     * @since 1.0-milestone-3
    </T> */
    fun <T> model(modelType: Class<T?>?): ModelBuilder<T?>?

    /**
     * Creates an executer which can be used to run the given action when the build has finished. The action is serialized into the build
     * process and executed, then its result is serialized back to the caller.
     *
     *
     * Requires Gradle 1.8 or later.
     *
     * @param buildAction The action to run.
     * @param <T> The result type.
     * @return The builder.
     * @since 1.8
     * @see .action
    </T> */
    fun <T> action(buildAction: BuildAction<T?>?): BuildActionExecuter<T?>?

    /**
     * Creates a builder for an executer which can be used to run actions in different phases of the build.
     * The actions are serialized into the build process and executed, then its result is serialized back to the caller.
     *
     *
     * Requires Gradle 4.8 or later.
     *
     * @return The builder.
     * @since 4.8
     */
    fun action(): BuildActionExecuter.Builder?

    /**
     * Notifies all daemons about file changes made by an external process, like an IDE.
     *
     *
     * The daemons will use this information to update the retained file system state.
     *
     *
     * The method should be invoked on every change done by the external process.
     * For example, an IDE should notify Gradle when the user saves a changed file, or
     * after some refactoring finished.
     * This will guarantee that Gradle picks up changes when trigerring a build, even
     * if the file system is too slow to notify file watchers.
     *
     * The caller shouldn't notify Gradle about changes detected by using other file
     * watchers, since Gradle already will be using its own file watcher.
     *
     *
     * The paths which are passed in need to be absolute, canonicalized paths.
     * For a delete, the deleted path should be passed.
     * For a rename, the old and the new path should be passed.
     * When creating a new file, the path to the file should be passed.
     *
     *
     * The call is synchronous, i.e. the method ensures that the changed paths are taken into account
     * by the daemon after it returned. This ensures that for every build started
     * after this method has been called knows about the changed paths.
     *
     *
     * If the version of Gradle does not support virtual file system retention (i.e. &lt; 6.1),
     * then the operation is a no-op.
     *
     * @param changedPaths Absolute paths which have been changed by the external process.
     * @throws IllegalArgumentException When the paths are not absolute.
     * @throws UnsupportedVersionException When the target Gradle version is &lt;= 2.5.
     * @throws GradleConnectionException On some other failure using the connection.
     * @since 6.1
     */
    fun notifyDaemonsAboutChangedPaths(changedPaths: MutableList<Path?>?)

    /**
     * Closes this connection. Blocks until any pending operations are complete. Once this method has returned, no more notifications will be delivered by any threads.
     * @since 1.0-milestone-3
     */
    override fun close()
}
