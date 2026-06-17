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

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.tooling.model.Model

/**
 * Provides a [BuildAction] various ways to control a Gradle build and access information about the build.
 *
 * @since 1.8
 */
interface BuildController {
    /**
     * Fetches a snapshot of the model of the given type for the default project. The default project is generally the
     * project referenced when a [ProjectConnection] is created.
     *
     *
     * Any of following models types may be available, depending on the version of Gradle being used by the target
     * build:
     *
     *
     *  * [GradleBuild]
     *  * [org.gradle.tooling.model.build.BuildEnvironment]
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
     * @param modelType The model type.
     * @param <T> The model type.
     * @return The model.
     * @throws UnknownModelException When the default project does not support the requested model.
     * @since 1.8
    </T> */
    @Throws(UnknownModelException::class)
    fun <T> getModel(modelType: Class<T?>?): T?

    /**
     * Fetches a snapshot of the model of the given type, if available.
     *
     *
     * See [.getModel] for more details.
     *
     * @param modelType The model type.
     * @param <T> The model type.
     * @return The model, or null if not present.
    </T> */
    fun <T> findModel(modelType: Class<T?>?): T?

    /**
     * Returns an overview of the Gradle build, including some basic details of the projects that make up the build.
     * This is equivalent to calling `#getModel(GradleBuild.class)`.
     *
     * @return The model.
     */
    @JvmField
    val buildModel: GradleBuild?

    /**
     * Fetches a snapshot of the model of the given type for the given element, usually a Gradle project.
     *
     *
     * The following elements are supported as targets:
     *
     *
     *  * Any [org.gradle.tooling.model.gradle.BasicGradleProject]
     *  * Any [org.gradle.tooling.model.GradleProject]
     *  * Any [org.gradle.tooling.model.eclipse.EclipseProject]
     *  * Any [org.gradle.tooling.model.idea.IdeaModule]
     *
     *
     *
     * See [.getModel] for more details.
     *
     * @param target The target element, usually a project.
     * @param modelType The model type.
     * @param <T> The model type.
     * @return The model.
     * @throws UnknownModelException When the target project does not support the requested model.
    </T> */
    @Throws(UnknownModelException::class)
    fun <T> getModel(target: Model?, modelType: Class<T?>?): T?

    /**
     * Fetches a snapshot of the model of the given type, if available.
     *
     *
     * See [.getModel] for more details.
     *
     * @param modelType The model type.
     * @param <T> The model type.
     * @return The model, or null if not present.
    </T> */
    fun <T> findModel(target: Model?, modelType: Class<T?>?): T?

    /**
     * Fetches a snapshot of the model of the given type using the given parameter.
     *
     *
     * See [.getModel] for more details.
     *
     * @param modelType The model type.
     * @param <T> The model type.
     * @param parameterType The parameter type.
     * @param <P> The parameter type.
     * @param parameterInitializer Action to configure the parameter
     * @return The model.
     * @throws UnknownModelException When the target project does not support the requested model.
     * @throws UnsupportedVersionException When the target project does not support the requested model or Gradle version does not support parameterized models.
     * @since 4.4
    </P></T> */
    @Throws(UnsupportedVersionException::class, UnknownModelException::class)
    fun <T, P> getModel(modelType: Class<T?>?, parameterType: Class<P?>?, parameterInitializer: Action<in P?>?): T?

    /**
     * Fetches a snapshot of the model of the given type using the given parameter, if available.
     *
     *
     * See [.getModel] for more details.
     *
     * @param modelType The model type.
     * @param <T> The model type.
     * @param parameterType The parameter type.
     * @param <P> The parameter type.
     * @param parameterInitializer Action to configure the parameter
     * @return The model.
     * @since 4.4
    </P></T> */
    fun <T, P> findModel(modelType: Class<T?>?, parameterType: Class<P?>?, parameterInitializer: Action<in P?>?): T?

    /**
     * Fetches a snapshot of the model of the given type for the given element using the given parameter.
     *
     *
     * The parameter type must be an interface only with getters and setters and no nesting is supported.
     * The Tooling API will create a proxy instance of this interface and use the initializer to run against
     * that instance to configure it and then pass to the model builder.
     *
     *
     *
     * See [.getModel] for more details.
     *
     * @param target The target element, usually a project.
     * @param modelType The model type.
     * @param <T> The model type.
     * @param parameterType The parameter type.
     * @param <P> The parameter type.
     * @param parameterInitializer Action to configure the parameter
     * @return The model.
     * @throws UnknownModelException When the target project does not support the requested model.
     * @throws UnsupportedVersionException When the target project does not support the requested model or Gradle version does not support parameterized models.
     * @since 4.4
    </P></T> */
    @Throws(UnsupportedVersionException::class, UnknownModelException::class)
    fun <T, P> getModel(target: Model?, modelType: Class<T?>?, parameterType: Class<P?>?, parameterInitializer: Action<in P?>?): T?

    /**
     * Fetches a snapshot of the model of the given type for the given element using the given parameter, if available.
     *
     *
     * See [.getModel] for more details.
     *
     * @param target The target element, usually a project.
     * @param modelType The model type.
     * @param <T> The model type.
     * @param parameterType The parameter type.
     * @param <P> The parameter type.
     * @param parameterInitializer Action to configure the parameter
     * @return The model.
     * @since 4.4
    </P></T> */
    fun <T, P> findModel(target: Model?, modelType: Class<T?>?, parameterType: Class<P?>?, parameterInitializer: Action<in P?>?): T?

    /**
     * Runs the given actions and returns their results. Attempts to run the actions in parallel, when supported by the Gradle version.
     *
     *
     * This method works with all Gradle versions. For versions 6.7 and earlier, the actions are run sequentially rather than in parallel.
     *
     *
     * When one or more actions fail with an exception, the exceptions are rethrown by this method and no result is returned.
     *
     * @param actions The actions to run.
     * @param <T> the result type.
     * @return The action results. These are returned in the same order as the actions that produce them.
     * @since 6.8
    </T> */
    fun <T> run(actions: MutableCollection<out BuildAction<out T?>?>?): MutableList<T?>?

    /**
     * Returns `true` when actions run using [.run] and that query project models of the given type will run in parallel.
     * Returns `false` when the models will not be queried in parallel, for example because the target Gradle version does not support parallel execution,
     * or because some build configuration disables the parallel execution, or because the queries are unsafe to perform in parallel.
     *
     * @return `true` when project models may be queried in parallel.
     * @since 6.8
     */
    fun getCanQueryProjectModelInParallel(modelType: Class<*>?): Boolean

    /**
     *
     * Streams an object to the client application.
     *
     *
     * The client application can receive objects sent using this method by registering a [StreamedValueListener].
     * The client application receives objects in the order they were sent, and before it receives the result of the [BuildAction].
     *
     *
     * This method sends the object asynchronously and does not block until the client application has received the object.
     *
     *
     * The build action will fail if the client application did not register a listener to receive the streamed objects.
     *
     * @since 8.6
     */
    fun send(value: Any?)

    /**
     * Fetches a snapshot of the model of the given type for the default project using resilient model fetching.
     *
     * See [.fetch] for more details about resilient fetching.
     *
     * @param modelType The model type.
     * @param <M> The model type.
     * @return The fetch result.
     * @since 9.3.0
    </M> */
    @Incubating
    fun <M> fetch(modelType: Class<M?>?): FetchModelResult<M?>?

    /**
     * Fetches a snapshot of the model of the given type for the given element using resilient model fetching.
     *
     * See [.fetch] for more details about resilient fetching.
     *
     * @param target The target element, usually a project.
     * @param modelType The model type.
     * @param <M> The model type.
     * @return The fetch result.
     * @since 9.3.0
    </M> */
    @Incubating
    fun <M> fetch(target: Model?, modelType: Class<M?>?): FetchModelResult<M?>?

    /**
     * Fetches a snapshot of the model of the given type using the given parameter using resilient model fetching.
     *
     * See [.fetch] for more details about resilient fetching.
     *
     * @param modelType The model type.
     * @param <M> The model type.
     * @param parameterType The parameter type.
     * @param <P> The parameter type.
     * @param parameterInitializer Action to configure the parameter
     * @return The fetch result.
     * @since 9.3.0
    </P></M> */
    @Incubating
    fun <M, P> fetch(
        modelType: Class<M?>?,
        parameterType: Class<P?>?,
        parameterInitializer: Action<in P?>?
    ): FetchModelResult<M?>?


    /**
     * Fetches a snapshot of the model of the given type for the given element using the given parameter with resilient model fetching.
     *
     *
     * Unlike [.getModel], this method uses resilient model fetching which means that if the model
     * cannot be fetched due to an error, the method will not throw an exception. Instead, it returns a [FetchModelResult] that contains
     * either the successfully fetched model or the failure information.
     *
     *
     * This is particularly useful when you want to fetch multiple models and continue processing even if some models fail to load,
     * or when you need detailed information about why a model fetch failed.
     *
     * @param target The target element, usually a project. Pass `null` to target the default project.
     * @param modelType The model type to fetch.
     * @param parameterType The parameter type used to configure the model fetch. Pass `null` if no parameter is needed.
     * @param parameterInitializer Action to configure the parameter. Pass `null` if no parameter initialization is needed.
     * @param <M> The model type.
     * @param <P> The parameter type.
     * @return A [FetchModelResult] containing the target, the fetched model (if successful), and any failures that occurred.
     * Check [FetchModelResult.getFailures] to determine if the fetch was successful.
     * @since 9.3.0
    </P></M> */
    @Incubating
    fun <M, P> fetch(
        target: Model?,
        modelType: Class<M?>?,
        parameterType: Class<P?>?,
        parameterInitializer: Action<in P?>?
    ): FetchModelResult<M?>?
}
