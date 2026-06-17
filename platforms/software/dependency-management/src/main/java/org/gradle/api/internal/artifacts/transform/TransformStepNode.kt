/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.transform

import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.capabilities.ImmutableCapability
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.execution.plan.CreationOrderedNode
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.SelfExecutingNode
import org.gradle.execution.plan.TaskDependencyResolver
import org.gradle.internal.Describables
import org.gradle.internal.Try.getOrMapFailure
import org.gradle.internal.model.CalculatedValueContainer
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.model.ValueCalculator
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.internal.scan.UsedByScanPlugin
import org.gradle.operations.dependencies.transforms.ExecutePlannedTransformStepBuildOperationType
import org.gradle.operations.dependencies.transforms.PlannedTransformStepIdentity
import org.gradle.operations.dependencies.variants.Capability
import java.util.function.Function
import java.util.stream.Collectors
import javax.annotation.OverridingMethodsMustInvokeSuper

abstract class TransformStepNode protected constructor(
    val transformStepNodeId: Long,
    val targetComponentVariant: ComponentVariantIdentifier,
    val sourceAttributes: AttributeContainer,
    val transformStep: TransformStep,
    val inputArtifact: ResolvableArtifact,
    val upstreamDependencies: TransformUpstreamDependencies
) : CreationOrderedNode(), SelfExecutingNode {
    private var cachedIdentity: PlannedTransformStepIdentity? = null

    val nodeIdentity: PlannedTransformStepIdentity
        get() {
            if (cachedIdentity == null) {
                cachedIdentity = createIdentity()
            }
            return cachedIdentity
        }

    private fun createIdentity(): PlannedTransformStepIdentity {
        val projectId = transformStep.getOwningProject()!!.getProjectIdentity()
        val consumerBuildPath = projectId.getBuildPath().asString()
        val consumerProjectPath = projectId.getProjectPath().asString()
        val componentId = ComponentToOperationConverter.convertComponentIdentifier(targetComponentVariant.getComponentId())
        val sourceAttributes = AttributesToMapConverter.convertToMap(this.sourceAttributes)
        val targetAttributes = AttributesToMapConverter.convertToMap(targetComponentVariant.getAttributes())
        val capabilities = targetComponentVariant.getCapabilities().asSet().stream()
            .map<Capability> { capability: ImmutableCapability? -> Companion.convertCapability(capability!!) }
            .collect(Collectors.toList())

        return DefaultPlannedTransformStepIdentity(
            consumerBuildPath,
            consumerProjectPath,
            componentId,
            sourceAttributes,
            targetAttributes,
            capabilities,
            inputArtifact.artifactName.displayName!!,
            upstreamDependencies.getConfigurationIdentity(),
            transformStepNodeId
        )
    }

    override fun getOwningProject(): ProjectInternal? {
        return transformStep.getOwningProject()
    }

    override fun isPublicNode(): Boolean {
        return true
    }

    override fun toString(): String {
        return transformStep.getDisplayName()
    }

    val transformedSubject: Try<TransformStepSubject?>
        get() = this.transformedArtifacts.getValue()

    override fun execute(context: NodeExecutionContext) {
        this.transformedArtifacts.run(context)
    }

    open fun executeIfNotAlready() {
        transformStep.isolateParametersIfNotAlready()
        upstreamDependencies.finalizeIfNotAlready()
        this.transformedArtifacts.finalizeIfNotAlready()
    }

    protected abstract val transformedArtifacts: CalculatedValueContainer<TransformStepSubject, *>?

    override fun getNodeFailure(): Throwable {
        return null
    }

    override fun resolveDependencies(dependencyResolver: TaskDependencyResolver) {
        processDependencies(dependencyResolver.resolveDependenciesFor(null, TaskDependencyContainer { context: TaskDependencyResolveContext? -> this.transformedArtifacts.visitDependencies(context) }))
    }

    protected fun processDependencies(dependencies: MutableSet<Node>) {
        for (dependency in dependencies) {
            addDependencySuccessor(dependency)
        }
    }

    class InitialTransformStepNode(
        transformStepNodeId: Long,
        targetComponentVariant: ComponentVariantIdentifier,
        sourceAttributes: AttributeContainer,
        transformStep: TransformStep,
        artifact: ResolvableArtifact,
        upstreamDependencies: TransformUpstreamDependencies,
        buildOperationRunner: BuildOperationRunner,
        calculatedValueContainerFactory: CalculatedValueContainerFactory
    ) : TransformStepNode(transformStepNodeId, targetComponentVariant, sourceAttributes, transformStep, artifact, upstreamDependencies) {
        private val result: CalculatedValueContainer<TransformStepSubject, TransformInitialArtifact>

        init {
            result =
                calculatedValueContainerFactory.create<TransformStepSubject, TransformInitialArtifact>(Describables.of(this), InitialTransformStepNode.TransformInitialArtifact(buildOperationRunner))
        }

        override fun getTransformedArtifacts(): CalculatedValueContainer<TransformStepSubject, TransformInitialArtifact> {
            return result
        }

        protected inner class TransformInitialArtifact(buildOperationRunner: BuildOperationRunner) : AbstractTransformArtifacts(buildOperationRunner) {
            override fun visitDependencies(context: TaskDependencyResolveContext) {
                super.visitDependencies(context)
                context.add(this.inputArtifact)
            }

            override fun createBuildOperation(context: NodeExecutionContext): TransformStepBuildOperation {
                return object : TransformStepBuildOperation() {
                    protected override fun transform(): TransformStepSubject {
                        return transformStep
                            .createInvocation(org.gradle.api.internal.artifacts.transform.TransformStepSubject.Companion.initial(this.inputArtifact), upstreamDependencies, context)
                            .completeAndGet()
                            .get()!!
                    }

                    override fun describeSubject(): String {
                        return inputArtifact.id.getDisplayName()
                    }
                }
            }
        }
    }

    class ChainedTransformStepNode(
        transformStepNodeId: Long,
        targetComponentVariant: ComponentVariantIdentifier,
        sourceAttributes: AttributeContainer,
        transformStep: TransformStep,
        val previousTransformStepNode: TransformStepNode,
        upstreamDependencies: TransformUpstreamDependencies,
        buildOperationExecutor: BuildOperationRunner,
        calculatedValueContainerFactory: CalculatedValueContainerFactory
    ) : TransformStepNode(transformStepNodeId, targetComponentVariant, sourceAttributes, transformStep, previousTransformStepNode.inputArtifact, upstreamDependencies) {
        private val result: CalculatedValueContainer<TransformStepSubject, TransformPreviousArtifacts>

        init {
            result = calculatedValueContainerFactory.create<TransformStepSubject, TransformPreviousArtifacts>(
                Describables.of(this),
                ChainedTransformStepNode.TransformPreviousArtifacts(buildOperationExecutor)
            )
        }

        override fun getTransformedArtifacts(): CalculatedValueContainer<TransformStepSubject, TransformPreviousArtifacts> {
            return result
        }

        override fun executeIfNotAlready() {
            // Only finalize the previous node when executing this node on demand
            previousTransformStepNode.executeIfNotAlready()
            super.executeIfNotAlready()
        }

        protected inner class TransformPreviousArtifacts(buildOperationRunner: BuildOperationRunner) : AbstractTransformArtifacts(buildOperationRunner) {
            override fun visitDependencies(context: TaskDependencyResolveContext) {
                super.visitDependencies(context)
                context.add(DefaultTransformNodeDependency(mutableListOf<TransformStepNode>(previousTransformStepNode)))
            }

            override fun createBuildOperation(context: NodeExecutionContext): TransformStepBuildOperation {
                return object : TransformStepBuildOperation() {
                    protected override fun transform(): TransformStepSubject {
                        return previousTransformStepNode.transformedSubject
                            .flatMap<TransformStepSubject?>(Function { transformedSubject: TransformStepSubject? ->
                                transformStep
                                    .createInvocation(transformedSubject!!, upstreamDependencies, context)
                                    .completeAndGet()
                            })
                            .get()
                    }

                    override fun describeSubject(): String {
                        return previousTransformStepNode.transformedSubject
                            .map<String?>(Function { obj: TransformStepSubject? -> obj!!.getDisplayName() })
                            .getOrMapFailure(Function { obj: Throwable? -> obj!!.message })
                    }
                }
            }
        }
    }

    protected abstract inner class AbstractTransformArtifacts protected constructor(private val buildOperationRunner: BuildOperationRunner) : ValueCalculator<TransformStepSubject> {
        @OverridingMethodsMustInvokeSuper
        override fun visitDependencies(context: TaskDependencyResolveContext) {
            context.add(transformStep)
            context.add(upstreamDependencies)
        }

        override fun calculateValue(context: NodeExecutionContext): TransformStepSubject {
            val buildOperation = createBuildOperation(context)
            val owningProject = transformStep.getOwningProject()
            return if (owningProject == null || !context.isPartOfExecutionGraph())
                buildOperation.transform()
            else
                buildOperationRunner.call<TransformStepSubject>(buildOperation)
        }

        protected abstract fun createBuildOperation(context: NodeExecutionContext): TransformStepBuildOperation
    }

    protected abstract inner class TransformStepBuildOperation : CallableBuildOperation<TransformStepSubject> {
        override fun description(): BuildOperationDescriptor.Builder {
            val transformStepName = transformStep.getDisplayName()
            val subjectName = describeSubject()
            val basicName = subjectName + " with " + transformStepName
            return BuildOperationDescriptor.displayName("Transform " + basicName)
                .progressDisplayName(TRANSFORMING_PROGRESS_PREFIX + basicName)
                .metadata(BuildOperationCategory.TRANSFORM)
                .details(ExecutePlannedTransformStepBuildOperationDetails(this@TransformStepNode, transformStepName, subjectName))
        }

        protected abstract fun describeSubject(): String

        override fun call(context: BuildOperationContext): TransformStepSubject {
            context.setResult(RESULT)
            return transform()
        }

        abstract fun transform(): TransformStepSubject

        companion object {
            @UsedByScanPlugin("The string is used for filtering out artifact transform logs in Develocity")
            private const val TRANSFORMING_PROGRESS_PREFIX = "Transforming "
        }
    }

    companion object {
        private fun convertCapability(capability: org.gradle.api.capabilities.Capability): Capability {
            return object : Capability {
                val group: String
                    get() = capability.getGroup()

                val name: String
                    get() = capability.getName()

                val version: String
                    get() = capability.getVersion()!!

                override fun toString(): String {
                    return group + ":" + name + (if (version == null) "" else (":" + version))
                }
            }
        }

        private val RESULT: ExecutePlannedTransformStepBuildOperationType.Result = object : ExecutePlannedTransformStepBuildOperationType.Result {
        }
    }
}
