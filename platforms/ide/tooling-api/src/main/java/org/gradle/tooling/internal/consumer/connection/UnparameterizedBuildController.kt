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
package org.gradle.tooling.internal.consumer.connection

import java.io.File
import java.lang.reflect.Proxy
import org.gradle.api.Action
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.operations.MultipleBuildOperationFailures
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.Failure
import org.gradle.tooling.FetchModelResult
import org.gradle.tooling.UnknownModelException
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.internal.adapter.ObjectGraphAdapter
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.DefaultFetchModelResult
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.internal.protocol.BuildResult
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException
import org.gradle.tooling.internal.protocol.ModelIdentifier
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.ProjectIdentifier
import org.gradle.tooling.model.ProjectModel
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.internal.Exceptions

internal abstract class UnparameterizedBuildController(
    private val adapter: ProtocolToModelAdapter,
    private val modelMapping: ModelMapping,
    protected val gradleVersion: VersionDetails,
    private val rootDir: File
) : HasCompatibilityMapping(
    gradleVersion
), BuildController {
    private val resultAdapter: ObjectGraphAdapter

    init {
        // Treat all models returned to the action as part of the same object graph
        this.resultAdapter = adapter.newGraph()
    }

    @Throws(UnknownModelException::class)
    override fun <T> getModel(modelType: Class<T?>?): T? {
        return getModel<T?>(null, modelType)
    }

    override fun <T> findModel(modelType: Class<T?>?): T? {
        return findModel<T?>(null, modelType)
    }

    override val buildModel: GradleBuild
        get() = getModel<GradleBuild>(null, GradleBuild::class.java as Class<GradleBuild?>)!!

    @Throws(UnknownModelException::class)
    override fun <T> getModel(target: Model?, modelType: Class<T?>?): T? {
        return getModel<T?, Any>(target, modelType, null, null)
    }

    override fun <T> findModel(target: Model?, modelType: Class<T?>?): T? {
        return findModel<T?, Any>(target, modelType, null, null)
    }

    @Throws(UnsupportedVersionException::class)
    override fun <T, P> getModel(modelType: Class<T?>?, parameterType: Class<P?>?, parameterInitializer: Action<in P?>?): T? {
        return getModel<T?, P?>(null, modelType, parameterType, parameterInitializer)
    }

    override fun <T, P> findModel(modelType: Class<T?>?, parameterType: Class<P?>?, parameterInitializer: Action<in P?>?): T? {
        return findModel<T?, P?>(null, modelType, parameterType, parameterInitializer)
    }

    override fun <T, P> findModel(target: Model?, modelType: Class<T?>?, parameterType: Class<P?>?, parameterInitializer: Action<in P?>?): T? {
        try {
            return getModel<T?, P?>(target, modelType, parameterType, parameterInitializer)
        } catch (e: UnknownModelException) {
            // Ignore
            return null
        }
    }

    @Throws(UnsupportedVersionException::class, UnknownModelException::class)
    override fun <T, P> getModel(target: Model?, modelType: Class<T?>?, parameterType: Class<P?>?, parameterInitializer: Action<in P?>?): T? {
        val originalTarget = unpackModelTarget(target)
        val modelIdentifier = getModelIdentifierFromModelType<T?>(modelType!!)
        val parameter: P? = initializeParameter<P?>(parameterType, parameterInitializer)

        val result: BuildResult<*>?
        try {
            result = getModelResult(originalTarget, modelIdentifier, parameter)
        } catch (e: InternalUnsupportedModelException) {
            throw Exceptions.unknownModel(modelType, e)
        }

        return adaptModel<T?>(target, modelType, result!!.model!!)
    }

    protected fun <T> adaptModel(target: Model?, modelType: Class<T?>, model: Any): T? {
        val viewBuilder = resultAdapter.builder<T?>(modelType)
        applyCompatibilityMapping<T?>(viewBuilder!!, DefaultProjectIdentifier(rootDir, getProjectPath(target)))
        return viewBuilder!!.build(model)
    }

    protected fun <T> getModelIdentifierFromModelType(modelType: Class<T?>): ModelIdentifier {
        return modelMapping.getModelIdentifierFromModelType(modelType)
    }

    protected fun unpackModelTarget(target: Model?): Any? {
        return if (target == null) null else adapter.unpack(target)
    }

    @Throws(InternalUnsupportedModelException::class)
    protected abstract fun getModelResult(target: Any?, modelIdentifier: ModelIdentifier, parameter: Any?): BuildResult<*>?

    override fun getCanQueryProjectModelInParallel(modelType: Class<*>?): Boolean {
        return false
    }

    override fun <T> run(actions: MutableCollection<out BuildAction<out T?>?>?): MutableList<T?> {
        val results: MutableList<T?> = ArrayList<T?>(actions?.size ?: 0)
        val failures: MutableList<Throwable> = ArrayList<Throwable>()
        for (action in actions.orEmpty()) {
            try {
                val result: T? = action!!.execute(this)
                results.add(result)
            } catch (t: Throwable) {
                failures.add(t)
            }
        }
        if (!failures.isEmpty()) {
            throw MultipleBuildOperationFailures(failures, null)
        }
        return results
    }

    override fun send(value: Any?) {
        throw UnsupportedVersionException(String.format("Gradle version %s does not support streaming values to the client.", gradleVersion.version))
    }

    override fun <M> fetch(modelType: Class<M?>?): FetchModelResult<M?> {
        return fetch<M?, Any>(null, modelType, null, null)
    }

    override fun <M> fetch(target: Model?, modelType: Class<M?>?): FetchModelResult<M?> {
        return fetch<M?, Any>(target, modelType, null, null)
    }

    override fun <M, P> fetch(modelType: Class<M?>?, parameterType: Class<P?>?, parameterInitializer: Action<in P?>?): FetchModelResult<M?> {
        return fetch<M?, P?>(null, modelType, parameterType, parameterInitializer)
    }

    /**
     * This is implemented just for backward compatibility.
     * Actual implementation for newer Gradle versions is [FetchAwareBuildControllerAdapter.fetch]
     */
    override fun <M, P> fetch(target: Model?, modelType: Class<M?>?, parameterType: Class<P?>?, parameterInitializer: Action<in P?>?): FetchModelResult<M?> {
        try {
            val model: Any = getModel<M?, P?>(target, modelType, parameterType, parameterInitializer) as Any
            return DefaultFetchModelResult.success<M?>(model as M?)
        } catch (e: Exception) {
            return DefaultFetchModelResult.failure<M?>(e)
        }
    }

    companion object {
        fun <P> initializeParameter(parameterType: Class<P?>?, parameterInitializer: Action<in P?>?): P? {
            validateParameters<P?>(parameterType, parameterInitializer)
            if (parameterType != null) {
                // TODO: move this to ObjectFactory
                val parameter = parameterType.cast(Proxy.newProxyInstance(parameterType.getClassLoader(), arrayOf<Class<*>>(parameterType), ToolingParameterProxy()))
                parameterInitializer!!.execute(parameter)
                return parameter
            } else {
                return null
            }
        }

        private fun <P> validateParameters(parameterType: Class<P?>?, parameterInitializer: Action<in P?>?) {
            if ((parameterType == null && parameterInitializer != null) || (parameterType != null && parameterInitializer == null)) {
                throw NullPointerException("parameterType and parameterInitializer both need to be set for a parameterized model request.")
            }

            if (parameterType != null) {
                ToolingParameterProxy.Companion.validateParameter(parameterType)
            }
        }

        private fun getProjectPath(target: Model?): String {
            if (target is ProjectModel) {
                return (target as ProjectModel).projectIdentifier!!.projectPath!!
            } else {
                return ":"
            }
        }
    }
}
