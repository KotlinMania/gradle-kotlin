/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationRunner
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import javax.annotation.concurrent.ThreadSafe

@ThreadSafe
class DefaultTransformedVariantFactory(
    private val buildOperationRunner: BuildOperationRunner,
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory,
    private val transformStepNodeFactory: TransformStepNodeFactory
) : TransformedVariantFactory {
    private val variants: ConcurrentMap<VariantKey, ResolvedArtifactSet> = ConcurrentHashMap<VariantKey, ResolvedArtifactSet>()

    override fun transformedExternalArtifacts(
        componentIdentifier: ComponentIdentifier,
        sourceVariant: ResolvedVariant,
        variantDefinition: VariantDefinition,
        dependenciesResolver: TransformUpstreamDependenciesResolver
    ): ResolvedArtifactSet {
        return locateOrCreate(DefaultTransformedVariantFactory.Factory { componentIdentifier: ComponentIdentifier, sourceVariant: ResolvedVariant, variantDefinition: VariantDefinition, dependenciesResolver: TransformUpstreamDependenciesResolver ->
            this.doCreateExternal(
                componentIdentifier,
                sourceVariant,
                variantDefinition,
                dependenciesResolver
            )
        }, componentIdentifier, sourceVariant, variantDefinition, dependenciesResolver)
    }

    override fun transformedProjectArtifacts(
        componentIdentifier: ComponentIdentifier,
        sourceVariant: ResolvedVariant,
        variantDefinition: VariantDefinition,
        dependenciesResolver: TransformUpstreamDependenciesResolver
    ): ResolvedArtifactSet {
        return locateOrCreate(DefaultTransformedVariantFactory.Factory { componentIdentifier: ComponentIdentifier, sourceVariant: ResolvedVariant, variantDefinition: VariantDefinition, dependenciesResolver: TransformUpstreamDependenciesResolver ->
            this.doCreateProject(
                componentIdentifier,
                sourceVariant,
                variantDefinition,
                dependenciesResolver
            )
        }, componentIdentifier, sourceVariant, variantDefinition, dependenciesResolver)
    }

    private fun locateOrCreate(
        factory: Factory,
        componentIdentifier: ComponentIdentifier,
        sourceVariant: ResolvedVariant,
        variantDefinition: VariantDefinition,
        dependenciesResolver: TransformUpstreamDependenciesResolver
    ): ResolvedArtifactSet {
        val target = variantDefinition.getTargetAttributes()
        val transformChain = variantDefinition.getTransformChain()
        val identifier = sourceVariant.identifier
        if (identifier == null) {
            // An ad hoc variant, do not cache the result
            return factory.create(componentIdentifier, sourceVariant, variantDefinition, dependenciesResolver)
        }
        val variantKey: VariantKey?
        if (transformChain.requiresDependencies()) {
            variantKey = VariantWithUpstreamDependenciesKey(identifier, target, dependenciesResolver)
        } else {
            variantKey = VariantKey(identifier, target)
        }

        // Can't use computeIfAbsent() as the default implementation does not allow recursive updates
        var result = variants.get(variantKey)
        if (result == null) {
            val newResult = factory.create(componentIdentifier, sourceVariant, variantDefinition, dependenciesResolver)
            result = variants.putIfAbsent(variantKey, newResult)
            if (result == null) {
                result = newResult
            }
        }
        return result
    }

    private fun doCreateExternal(
        componentIdentifier: ComponentIdentifier,
        sourceVariant: ResolvedVariant,
        variantDefinition: VariantDefinition,
        dependenciesResolver: TransformUpstreamDependenciesResolver
    ): TransformedExternalArtifactSet {
        return TransformedExternalArtifactSet(
            componentIdentifier,
            sourceVariant.sourceVariantId,
            sourceVariant.artifacts,
            variantDefinition.getTargetAttributes(),
            sourceVariant.capabilities,
            variantDefinition.getTransformChain(),
            dependenciesResolver,
            calculatedValueContainerFactory
        )
    }

    private fun doCreateProject(
        componentIdentifier: ComponentIdentifier,
        sourceVariant: ResolvedVariant,
        variantDefinition: VariantDefinition,
        dependenciesResolver: TransformUpstreamDependenciesResolver
    ): TransformedProjectArtifactSet {
        val sourceAttributes: AttributeContainer?
        val sourceArtifacts: ResolvedArtifactSet?
        val previous = variantDefinition.getPrevious()
        if (previous != null) {
            sourceAttributes = previous.getTargetAttributes()
            sourceArtifacts = transformedProjectArtifacts(componentIdentifier, sourceVariant, previous, dependenciesResolver)
        } else {
            sourceAttributes = sourceVariant.attributes
            sourceArtifacts = sourceVariant.artifacts
        }
        val targetComponentVariant = ComponentVariantIdentifier(componentIdentifier, variantDefinition.getTargetAttributes(), sourceVariant.capabilities)
        val transformStepNodes = createTransformStepNodes(sourceArtifacts, sourceAttributes!!, targetComponentVariant, variantDefinition, dependenciesResolver)
        return TransformedProjectArtifactSet(sourceVariant.sourceVariantId, targetComponentVariant, transformStepNodes)
    }

    private fun createTransformStepNodes(
        sourceArtifacts: ResolvedArtifactSet,
        sourceAttributes: AttributeContainer,
        targetComponentVariant: ComponentVariantIdentifier,
        variantDefinition: VariantDefinition,
        dependenciesResolver: TransformUpstreamDependenciesResolver
    ): MutableList<TransformStepNode> {
        val componentId = targetComponentVariant.getComponentId()
        val transformStep = variantDefinition.getTransformStep()

        val builder = ImmutableList.builder<TransformStepNode>()
        sourceArtifacts.visitTransformSources(object : ResolvedArtifactSet.TransformSourceVisitor {
            override fun visitArtifact(artifact: ResolvableArtifact) {
                val upstreamDependencies = dependenciesResolver.dependenciesFor(componentId, transformStep)
                val transformStepNode: TransformStepNode = transformStepNodeFactory.createInitial(
                    targetComponentVariant,
                    sourceAttributes,
                    transformStep,
                    artifact,
                    upstreamDependencies,
                    buildOperationRunner,
                    calculatedValueContainerFactory
                )
                builder.add(transformStepNode)
            }

            override fun visitTransform(source: TransformStepNode) {
                val upstreamDependencies = dependenciesResolver.dependenciesFor(componentId, transformStep)
                val transformStepNode: TransformStepNode =
                    transformStepNodeFactory.createChained(targetComponentVariant, sourceAttributes, transformStep, source, upstreamDependencies, buildOperationRunner, calculatedValueContainerFactory)
                builder.add(transformStepNode)
            }
        })
        return builder.build()
    }

    private interface Factory {
        fun create(
            componentIdentifier: ComponentIdentifier,
            sourceVariant: ResolvedVariant,
            variantDefinition: VariantDefinition,
            dependenciesResolver: TransformUpstreamDependenciesResolver
        ): ResolvedArtifactSet
    }

    private open class VariantKey(private val sourceVariant: VariantResolveMetadata.Identifier, private val target: ImmutableAttributes) {
        override fun hashCode(): Int {
            return sourceVariant.hashCode() xor target.hashCode()
        }

        override fun equals(obj: Any): Boolean {
            if (obj === this) {
                return true
            }
            if (obj == null || obj.javaClass != javaClass) {
                return false
            }
            val other = obj as VariantKey
            return sourceVariant == other.sourceVariant && target == other.target
        }
    }

    private class VariantWithUpstreamDependenciesKey(
        sourceVariant: VariantResolveMetadata.Identifier,
        target: ImmutableAttributes,
        private val dependenciesResolver: TransformUpstreamDependenciesResolver
    ) : VariantKey(sourceVariant, target) {
        override fun hashCode(): Int {
            return super.hashCode() xor dependenciesResolver.hashCode()
        }

        override fun equals(obj: Any): Boolean {
            if (!super.equals(obj)) {
                return false
            }
            val other = obj as VariantWithUpstreamDependenciesKey
            return dependenciesResolver == other.dependenciesResolver
        }
    }
}
