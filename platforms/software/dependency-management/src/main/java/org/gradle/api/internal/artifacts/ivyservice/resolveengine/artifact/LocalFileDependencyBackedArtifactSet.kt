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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultResolvableArtifact
import org.gradle.api.internal.artifacts.transform.AbstractTransformedArtifactSet
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector
import org.gradle.api.internal.artifacts.transform.TransformChain
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependenciesResolver
import org.gradle.api.internal.artifacts.transform.TransformedArtifactSet
import org.gradle.api.internal.artifacts.transform.VariantDefinition
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.specs.Spec
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName.Companion.forFile
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation
import java.io.File

/**
 * Abstract file dependency implementation. The two `default` and `deserialized` subtypes
 * represent the artifact set before and after configuration cache serialization. The deserialized
 * type only stores a subset of the information originally stored by the default type.
 *
 *
 * This is required since the files in a given file dependency artifact set are unknown until
 * dependencies are executed. For this reason, we delay artifact selection until after this artifact
 * set is restored from the configuration cache. This differs from normal artifact variant selection
 * where we can perform selection before serialization.
 *
 *
 * The tricky part that due to the artifactType registry, artifact variant selection depends on the
 * file names of the artifacts exposed by a variant. Normal variants have access to these file names
 * before the dependencies are executed, but file dependencies do not.
 *
 *
 * We should do one of these things to fix the current mess here:
 *
 *  * Kill file dependencies
 *  * Enhance file dependencies to know what files they produce
 *  * Kill artifactType registry
 *
 */
abstract class LocalFileDependencyBackedArtifactSet(
    val dependencyMetadata: LocalFileDependencyMetadata,
    val sourceVariantId: VariantIdentifier,
    val componentFilter: Spec<in ComponentIdentifier?>,
    val variantSelector: ArtifactVariantSelector,
    val artifactTypeRegistry: ImmutableArtifactTypeRegistry,
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory,
    val allowNoMatchingVariants: Boolean
) : TransformedArtifactSet, LocalDependencyFiles {
    abstract val requestAttributes: ImmutableAttributes?

    override fun visit(listener: ResolvedArtifactSet.Visitor) {
        val visitType = listener.prepareForVisit(this)
        if (visitType == FileCollectionStructureVisitor.VisitType.NoContents) {
            listener.visitArtifacts(EndCollection(this))
            return
        }

        val componentIdentifier: ComponentIdentifier = dependencyMetadata.componentId
        if (componentIdentifier != null && !componentFilter.isSatisfiedBy(componentIdentifier)) {
            listener.visitArtifacts(EndCollection(this))
            return
        }

        val fileCollection: FileCollectionInternal = dependencyMetadata.files
        val files: MutableSet<File>?
        try {
            files = fileCollection.getFiles()
        } catch (throwable: Exception) {
            listener.visitArtifacts(BrokenArtifacts(throwable))
            return
        }

        val selectedArtifacts = ImmutableList.builderWithExpectedSize<ResolvedArtifactSet>(files.size)
        for (file in files) {
            val artifactIdentifier: ComponentArtifactIdentifier
            if (componentIdentifier == null) {
                artifactIdentifier = OpaqueComponentArtifactIdentifier(file)
                if (!componentFilter.isSatisfiedBy(artifactIdentifier.getComponentIdentifier())) {
                    continue
                }
            } else {
                artifactIdentifier = ComponentFileArtifactIdentifier(componentIdentifier, file.getName())
            }

            val variantAttributes = artifactTypeRegistry.mapAttributesFor(file)
            val variant = SingletonFileResolvedVariant(file, artifactIdentifier, sourceVariantId, LOCAL_FILE, variantAttributes, dependencyMetadata, calculatedValueContainerFactory)
            selectedArtifacts.add(variantSelector.select(variant, this.requestAttributes, allowNoMatchingVariants)!!)
        }
        CompositeResolvedArtifactSet.of(selectedArtifacts.build()).visit(listener)
    }

    override fun visitTransformSources(visitor: ResolvedArtifactSet.TransformSourceVisitor) {
        // Should not be called
        throw UnsupportedOperationException()
    }

    override fun visitExternalArtifacts(visitor: Action<ResolvableArtifact>) {
        // Should not be called
        throw UnsupportedOperationException()
    }

    override fun visitDependencies(context: TaskDependencyResolveContext) {
        context.add(dependencyMetadata.files.getBuildDependencies())
    }

    private class SingletonFileResolvedVariant(
        file: File,
        private val artifactIdentifier: ComponentArtifactIdentifier,
        private val sourceVariantId: VariantIdentifier,
        private val artifactSetName: DisplayName,
        val attributes: ImmutableAttributes,
        private val dependencyMetadata: LocalFileDependencyMetadata,
        private val calculatedValueContainerFactory: CalculatedValueContainerFactory
    ) : ResolvedVariant, ResolvedArtifactSet, ResolvedArtifactSet.Artifacts, ResolvedVariantSet {
        private val artifact: ResolvableArtifact

        init {
            artifact = DefaultResolvableArtifact(
                null, forFile(file, null), this.artifactIdentifier, this.dependencyMetadata.files, calculatedValueContainerFactory.create<File>(
                    Describables.of(
                        artifactIdentifier
                    ), file
                ),
                calculatedValueContainerFactory
            )
        }

        override fun getIdentifier(): VariantResolveMetadata.Identifier {
            return null
        }

        override fun toString(): String {
            return asDescribable().getDisplayName()
        }

        override fun getComponentIdentifier(): ComponentIdentifier {
            return artifactIdentifier.getComponentIdentifier()
        }

        override fun getSourceVariantId(): VariantIdentifier {
            return sourceVariantId
        }

        override fun getArtifacts(): ResolvedArtifactSet {
            return this
        }

        override fun asDescribable(): DisplayName {
            return Describables.of(artifactIdentifier)
        }

        override fun getCandidates(): MutableList<ResolvedVariant> {
            return mutableListOf<ResolvedVariant>(this)
        }

        override fun getOverriddenAttributes(): ImmutableAttributes {
            return ImmutableAttributes.EMPTY
        }

        override fun getProducerSchema(): ImmutableAttributesSchema {
            return ImmutableAttributesSchema.EMPTY
        }

        override fun visit(visitor: ResolvedArtifactSet.Visitor) {
            visitor.visitArtifacts(this)
        }

        override fun visitTransformSources(visitor: ResolvedArtifactSet.TransformSourceVisitor) {
            // Should not be called
            throw UnsupportedOperationException()
        }

        override fun visitExternalArtifacts(visitor: Action<ResolvableArtifact>) {
            // Should not be called
            throw UnsupportedOperationException()
        }

        override fun startFinalization(actions: BuildOperationQueue<RunnableBuildOperation>, requireFiles: Boolean) {
        }

        override fun visit(visitor: ArtifactVisitor) {
            visitor.visitArtifact(artifactSetName, sourceVariantId, this.attributes, ImmutableCapabilities.EMPTY, artifact)
            visitor.endVisitCollection(FileCollectionInternal.OTHER)
        }

        override fun visitDependencies(context: TaskDependencyResolveContext) {
            context.add(dependencyMetadata.files.getBuildDependencies())
        }

        override fun getCapabilities(): ImmutableCapabilities {
            return ImmutableCapabilities.EMPTY
        }

        override fun transformCandidate(sourceVariant: ResolvedVariant, variantDefinition: VariantDefinition): ResolvedArtifactSet {
            assert(sourceVariant === this)
            return LocalFileDependencyBackedArtifactSet.TransformedLocalFileArtifactSet(
                this,
                sourceVariantId,
                variantDefinition.targetAttributes,
                variantDefinition.transformChain!!,
                calculatedValueContainerFactory
            )
        }
    }

    /**
     * An artifact set that contains a single transformed local file.
     */
    private class TransformedLocalFileArtifactSet(
        delegate: SingletonFileResolvedVariant,
        sourceVariantId: VariantIdentifier,
        attributes: ImmutableAttributes,
        transformChain: TransformChain,
        calculatedValueContainerFactory: CalculatedValueContainerFactory
    ) : AbstractTransformedArtifactSet(
        delegate.getComponentIdentifier(),
        sourceVariantId,
        delegate,
        attributes,
        ImmutableCapabilities.EMPTY,
        transformChain,
        TransformUpstreamDependenciesResolver.NO_DEPENDENCIES,  // File dependencies do not themselves depend on other artifacts.
        calculatedValueContainerFactory
    ), FileCollectionInternal.Source

    companion object {
        private val LOCAL_FILE = Describables.of("local file")
    }
}
