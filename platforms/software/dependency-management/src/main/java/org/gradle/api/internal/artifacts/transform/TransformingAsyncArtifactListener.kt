/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenArtifacts
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.internal.Deferrable
import org.gradle.internal.DisplayName
import org.gradle.internal.Try
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation
import java.util.function.Consumer
import java.util.function.Function

class TransformingAsyncArtifactListener(
    private val transformSteps: MutableList<BoundTransformStep>,
    private val target: ImmutableAttributes,
    private val capabilities: ImmutableCapabilities,
    private val result: ImmutableList.Builder<ResolvedArtifactSet.Artifacts>
) : ResolvedArtifactSet.Visitor {
    override fun visitArtifacts(artifacts: ResolvedArtifactSet.Artifacts) {
        artifacts.visit(object : ArtifactVisitor {
            override fun visitArtifact(
                artifactSetName: DisplayName,
                sourceVariantId: VariantIdentifier,
                attributes: ImmutableAttributes,
                variantCapabilities: ImmutableCapabilities,
                artifact: ResolvableArtifact
            ) {
                val transformedArtifact = TransformedArtifact(artifactSetName, sourceVariantId, target, capabilities, artifact, transformSteps)
                result.add(transformedArtifact)
            }

            override fun requireArtifactFiles(): Boolean {
                return false
            }

            override fun visitFailure(failure: Throwable) {
                result.add(BrokenArtifacts(failure))
            }
        })
    }

    override fun prepareForVisit(source: FileCollectionInternal.Source): FileCollectionStructureVisitor.VisitType {
        // Visit everything
        return FileCollectionStructureVisitor.VisitType.Visit
    }

    class TransformedArtifact(
        val artifactSetName: DisplayName,
        val sourceVariantId: VariantIdentifier,
        val target: ImmutableAttributes,
        val capabilities: ImmutableCapabilities,
        val artifact: ResolvableArtifact,
        val transformSteps: MutableList<BoundTransformStep>
    ) : ResolvedArtifactSet.Artifacts, RunnableBuildOperation {
        private var transformedSubject: Try<TransformStepSubject?>? = null
        private var invocation: Deferrable<Try<TransformStepSubject?>?>? = null

        override fun prepareForVisitingIfNotAlready() {
            // The parameters of the transforms should already be isolated prior to visiting this set.
            // However, in certain cases, the transform's parameters may not be isolated (eg https://github.com/gradle/gradle/issues/23116), so do this now
            // Those cases should be improved so that the parameters are always isolated, for example by always using work nodes to do this work
            for (step in transformSteps) {
                step.getTransformStep().isolateParametersIfNotAlready()
            }
        }

        override fun startFinalization(actions: BuildOperationQueue<RunnableBuildOperation>, requireFiles: Boolean) {
            if (prepareInvocation()) {
                actions.add(this)
            }
        }

        override fun description(): BuildOperationDescriptor.Builder {
            return BuildOperationDescriptor.displayName("Execute transform chain: " + artifact.id.getDisplayName())
        }

        override fun run(context: BuildOperationContext?) {
            finalizeValue()
        }

        /**
         * Returns true if this artifact should be queued for execution, false when a value is already available.
         */
        private fun prepareInvocation(): Boolean {
            synchronized(this) {
                if (transformedSubject != null) {
                    // Already have a result, no need to execute
                    return false
                }
            }
            if (!artifact.fileSource.isFinalized()) {
                // No input artifact yet, should execute
                return true
            }
            if (!artifact.fileSource.getValue().isSuccessful) {
                synchronized(this) {
                    // Failed to resolve input artifact, no need to execute
                    transformedSubject = Try.failure<U?>(artifact.fileSource.getValue().failure!!.get())
                    return false
                }
            }

            val invocation: Deferrable<Try<TransformStepSubject?>?> = createInvocation()
            synchronized(this) {
                this.invocation = invocation
                if (invocation.completed!!.isPresent()) {
                    // Have already executed the transform, no need to execute
                    transformedSubject = invocation.completed!!.get()
                    return false
                } else {
                    // Have not executed the transform, should execute
                    return true
                }
            }
        }

        private fun finalizeValue(): Try<TransformStepSubject?> {
            synchronized(this) {
                if (transformedSubject != null) {
                    return transformedSubject!!
                }
            }

            artifact.fileSource.finalizeIfNotAlready()
            if (!artifact.fileSource.getValue().isSuccessful) {
                synchronized(this) {
                    transformedSubject = Try.failure<U?>(artifact.fileSource.getValue().failure!!.get())
                    return transformedSubject!!
                }
            }

            var invocation: Deferrable<Try<TransformStepSubject?>?>?
            synchronized(this) {
                invocation = this.invocation
            }

            if (invocation == null) {
                invocation = createInvocation()
            }
            val result: Try<TransformStepSubject?> = invocation.completeAndGet()!!
            synchronized(this) {
                transformedSubject = result
                return result
            }
        }

        private fun createInvocation(): Deferrable<Try<TransformStepSubject?>?> {
            val initialSubject: TransformStepSubject = TransformStepSubject.Companion.initial(artifact)
            val initialStep = transformSteps.get(0)
            var invocation: Deferrable<Try<TransformStepSubject?>?> = initialStep.getTransformStep()
                .createInvocation(initialSubject, initialStep.getUpstreamDependencies(), null)
            for (i in 1..<transformSteps.size) {
                val nextStep = transformSteps.get(i)
                invocation = invocation
                    .flatMap<Try<TransformStepSubject?>?>(Function { intermediateResult: Try<TransformStepSubject?>? ->
                        intermediateResult!!
                            .map<org.gradle.internal.Deferrable<org.gradle.internal.Try<org.gradle.api.internal.artifacts.transform.TransformStepSubject>?>?>(java.util.function.Function { intermediateSubject: org.gradle.api.internal.artifacts.transform.TransformStepSubject? ->
                                nextStep.getTransformStep()
                                    .createInvocation(intermediateSubject!!, nextStep.getUpstreamDependencies(), null)
                            })!!
                            .getOrMapFailure(Function { failure: Throwable? -> Deferrable.completed(Try.failure<U?>(failure)) })
                    })
            }
            return invocation
        }

        override fun visit(visitor: ArtifactVisitor) {
            val transformedSubject: Try<TransformStepSubject?> = finalizeValue()
            transformedSubject.ifSuccessfulOrElse(
                Consumer { subject: TransformStepSubject? ->
                    for (output in subject!!.getFiles()) {
                        val resolvedArtifact = artifact.transformedTo(output)
                        visitor.visitArtifact(artifactSetName, sourceVariantId, target, capabilities, resolvedArtifact)
                    }
                },
                Consumer { failure: Throwable? ->
                    visitor.visitFailure(
                        TransformException(String.format("Failed to transform %s to match attributes %s.", artifact.id.getDisplayName(), target), failure!!)
                    )
                }
            )
        }
    }
}
