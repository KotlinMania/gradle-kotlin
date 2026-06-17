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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import org.gradle.api.Action
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.artifacts.DownloadArtifactBuildOperationType
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.internal.DisplayName
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.resolve.resolver.ComponentArtifactResolver

class ArtifactBackedResolvedVariant(
    private val identifier: VariantResolveMetadata.Identifier?,
    val sourceVariantId: VariantIdentifier,
    private val displayName: DisplayName,
    val attributes: ImmutableAttributes,
    val capabilities: ImmutableCapabilities,
    private val artifacts: MutableList<out ComponentArtifactMetadata?>,
    private val componentArtifactResolver: ComponentArtifactResolver
) : ResolvedVariant {
    override fun getArtifacts(): ResolvedArtifactSet? {
        val resolvedArtifacts: MutableCollection<out ResolvableArtifact>?
        try {
            resolvedArtifacts = componentArtifactResolver.resolveArtifacts(artifacts)
        } catch (e: Exception) {
            return UnavailableResolvedArtifactSet(e)
        }
        if (resolvedArtifacts!!.isEmpty()) {
            return ResolvedArtifactSet.EMPTY
        }
        if (resolvedArtifacts.size == 1) {
            return SingleArtifactSet(displayName, sourceVariantId, attributes, capabilities, resolvedArtifacts.iterator().next())
        }

        val artifactSets: MutableList<SingleArtifactSet?> = ArrayList<SingleArtifactSet?>(resolvedArtifacts.size)
        for (artifact in resolvedArtifacts) {
            artifactSets.add(SingleArtifactSet(displayName, sourceVariantId, attributes, capabilities, artifact))
        }
        return CompositeResolvedArtifactSet.Companion.of(artifactSets)
    }

    override fun getIdentifier(): VariantResolveMetadata.Identifier {
        return identifier!!
    }

    override fun toString(): String {
        return displayName.getDisplayName()
    }

    override fun asDescribable(): DisplayName {
        return displayName
    }

    private class SingleArtifactSet(
        private val artifactSetName: DisplayName?,
        private val sourceVariantId: VariantIdentifier?,
        private val variantAttributes: ImmutableAttributes?,
        private val capabilities: ImmutableCapabilities?,
        private val artifact: ResolvableArtifact
    ) : ResolvedArtifactSet, ResolvedArtifactSet.Artifacts {
        override fun visit(visitor: ResolvedArtifactSet.Visitor) {
            visitor.visitArtifacts(this)
        }

        override fun startFinalization(actions: BuildOperationQueue<RunnableBuildOperation?>, requireFiles: Boolean) {
            if (requireFiles) {
                if (artifact.isResolveSynchronously) {
                    // Resolve it now
                    artifact.fileSource.finalizeIfNotAlready()
                } else {
                    // Resolve it later
                    actions.add(DownloadArtifactFile(artifact))
                }
            }
        }

        override fun visit(visitor: ArtifactVisitor) {
            if (visitor.requireArtifactFiles() && !artifact.fileSource.getValue().isSuccessful) {
                visitor.visitFailure(artifact.fileSource.getValue().failure.get())
            } else {
                visitor.visitArtifact(artifactSetName, sourceVariantId, variantAttributes, capabilities, artifact)
                visitor.endVisitCollection(FileCollectionInternal.OTHER)
            }
        }

        override fun visitTransformSources(visitor: ResolvedArtifactSet.TransformSourceVisitor) {
            if (artifact.id.getComponentIdentifier() is ProjectComponentIdentifier) {
                visitor.visitArtifact(artifact)
            }
        }

        override fun visitExternalArtifacts(visitor: Action<ResolvableArtifact?>) {
            if (artifact.id.getComponentIdentifier() !is ProjectComponentIdentifier) {
                visitor.execute(artifact)
            }
        }

        override fun visitDependencies(context: TaskDependencyResolveContext) {
            context.add(artifact)
        }

        override fun toString(): String {
            return artifact.id.getDisplayName()
        }
    }

    private class DownloadArtifactFile(private val artifact: ResolvableArtifact) : RunnableBuildOperation {
        override fun run(context: BuildOperationContext) {
            artifact.fileSource.finalizeIfNotAlready()
            context.setResult(DownloadArtifactBuildOperationType.RESULT)
        }

        override fun description(): BuildOperationDescriptor.Builder {
            val displayName: String? = artifact.id.getDisplayName()
            return BuildOperationDescriptor.displayName("Resolve " + displayName)
                .details(DownloadArtifactBuildOperationType.DetailsImpl(displayName))
        }
    }
}
