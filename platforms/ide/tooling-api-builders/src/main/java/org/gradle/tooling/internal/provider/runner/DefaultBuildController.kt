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
package org.gradle.tooling.internal.provider.runner

import com.google.common.collect.ImmutableList
import org.gradle.api.BuildCancelledException
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.build.event.types.DefaultFailure
import org.gradle.internal.buildtree.BuildTreeModelController
import org.gradle.internal.buildtree.BuildTreeModelSideEffect
import org.gradle.internal.buildtree.BuildTreeModelSideEffectExecutor
import org.gradle.internal.buildtree.BuildTreeModelTarget
import org.gradle.internal.buildtree.ToolingModelRequestContext
import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.work.WorkerThreadRegistry
import org.gradle.tooling.internal.gradle.GradleBuildIdentity
import org.gradle.tooling.internal.gradle.GradleProjectIdentity
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1
import org.gradle.tooling.internal.protocol.BuildResult
import org.gradle.tooling.internal.protocol.InternalActionAwareBuildController
import org.gradle.tooling.internal.protocol.InternalBuildController
import org.gradle.tooling.internal.protocol.InternalBuildControllerVersion2
import org.gradle.tooling.internal.protocol.InternalFailure
import org.gradle.tooling.internal.protocol.InternalFetchAwareBuildController
import org.gradle.tooling.internal.protocol.InternalFetchModelResult
import org.gradle.tooling.internal.protocol.InternalStreamedValueRelay
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException
import org.gradle.tooling.internal.protocol.ModelIdentifier
import org.gradle.tooling.internal.provider.connection.ProviderBuildResult
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.internal.provider.serialization.StreamedValue
import org.gradle.tooling.provider.model.UnknownModelException
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderResultInternal
import org.jspecify.annotations.NullMarked
import java.util.function.Function
import java.util.function.Supplier

@NullMarked
internal class DefaultBuildController(
    private val controller: BuildTreeModelController,
    private val workerThreadRegistry: WorkerThreadRegistry,
    private val cancellationToken: BuildCancellationToken,
    private val buildEventConsumer: BuildEventConsumer,
    private val sideEffectExecutor: BuildTreeModelSideEffectExecutor,
    private val payloadSerializer: PayloadSerializer
) : InternalBuildController, InternalBuildControllerVersion2, InternalActionAwareBuildController, InternalStreamedValueRelay, InternalFetchAwareBuildController {
    @get:Throws(BuildExceptionVersion1::class)
    @get:Deprecated("")
    val buildModel: BuildResult<*>
        /**
         * This is used by consumers 1.8-rc-1 to 4.3
         */
        get() {
            assertCanQuery()
            return ProviderBuildResult<Any?>(controller.getConfiguredModel())
        }

    /**
     * This is used by consumers 1.8-rc-1 to 4.3
     */
    @Deprecated("")
    @Throws(BuildExceptionVersion1::class, InternalUnsupportedModelException::class)
    override fun getModel(target: Any, modelIdentifier: ModelIdentifier): BuildResult<*> {
        return getModel(target, modelIdentifier, null)
    }

    /**
     * This is used by consumers 4.4 and later
     */
    @Throws(BuildExceptionVersion1::class, InternalUnsupportedModelException::class)
    override fun getModel(target: Any?, modelIdentifier: ModelIdentifier, parameter: Any?): BuildResult<*> {
        val model = doGetModel(target, ToolingModelRequestContext(modelIdentifier.name!!, parameter, false))
        return ProviderBuildResult<Any?>(model.getModel())
    }

    @Throws(BuildExceptionVersion1::class, InternalUnsupportedModelException::class)
    private fun doGetModel(target: Any?, modelRequestContext: ToolingModelRequestContext): ToolingModelBuilderResultInternal {
        assertCanQuery()
        if (cancellationToken.isCancellationRequested()) {
            throw BuildCancelledException(String.format("Could not build '%s' model. Build cancelled.", modelRequestContext.getModelName()))
        }

        val scopedTarget: BuildTreeModelTarget = resolveTarget(target)
        try {
            return controller.getModel(scopedTarget, modelRequestContext)
        } catch (e: UnknownModelException) {
            throw InternalUnsupportedModelException().initCause(e) as InternalUnsupportedModelException?
        }
    }

    override fun getCanQueryProjectModelInParallel(modelType: Class<*>): Boolean {
        return controller.queryModelActionsRunInParallel()
    }

    override fun <T> run(actions: MutableList<Supplier<T?>>): MutableList<T?> {
        assertCanQuery()
        return controller.runQueryModelActions<T?>(actions)!!
    }

    private fun assertCanQuery() {
        check(workerThreadRegistry.isWorkerThread()) { "A build controller cannot be used from a thread that is not managed by Gradle." }
    }

    override fun dispatch(value: Any) {
        val serializedModel = payloadSerializer.serialize(value)
        val streamedValue = StreamedValue(serializedModel)
        val buildEventConsumer = this.buildEventConsumer
        sideEffectExecutor.runIsolatableSideEffect(BuildTreeModelSideEffect { buildEventConsumer.dispatch(streamedValue) })
    }

    override fun <M> fetch(target: Any?, modelIdentifier: ModelIdentifier, parameter: Any?): InternalFetchModelResult<M?> {
        try {
            val resultInternal = doGetModel(target, ToolingModelRequestContext(modelIdentifier.name!!, parameter, true))
            val failures: MutableList<InternalFailure> = toInternalFailures(resultInternal.getFailures())
            return DefaultInternalFetchModelResult<M?>(uncheckedNonnullCast<M?>(resultInternal.getModel()), failures)
        } catch (e: Exception) {
            val failures: MutableList<InternalFailure> = ImmutableList.of<InternalFailure>(DefaultFailure.fromThrowable(e))
            return DefaultInternalFetchModelResult<M?>(null, failures)
        }
    }

    companion object {
        private fun resolveTarget(target: Any?): BuildTreeModelTarget {
            if (target == null) {
                return BuildTreeModelTarget.ofDefault()
            } else if (target is GradleProjectIdentity) {
                val projectIdentity = target
                return BuildTreeModelTarget.ofProject(projectIdentity.rootDir, projectIdentity.projectPath!!)
            } else if (target is GradleBuildIdentity) {
                val buildIdentity = target
                return BuildTreeModelTarget.ofBuild(buildIdentity.rootDir)
            } else {
                throw IllegalArgumentException("Don't know how to build models for " + target)
            }
        }

        private fun toInternalFailures(failures: MutableList<Failure>): MutableList<InternalFailure> {
            return failures
                .stream()
                .map<InternalFailure> { failure: Failure? -> DefaultFailure.fromFailure(failure, Function { dummy: ProblemInternal? -> null }) }
                .collect(ImmutableList.toImmutableList<InternalFailure>())
        }
    }
}
