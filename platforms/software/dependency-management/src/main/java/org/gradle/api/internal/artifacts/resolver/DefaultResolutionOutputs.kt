/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.internal.artifacts.resolver

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.ResolverResults
import org.gradle.api.internal.artifacts.configurations.ArtifactCollectionInternal
import org.gradle.api.internal.artifacts.configurations.DefaultArtifactCollection
import org.gradle.api.internal.artifacts.configurations.ResolutionResultProviderBackedSelectedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet
import org.gradle.api.internal.artifacts.result.DefaultResolutionResult
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.internal.Actions
import org.gradle.internal.model.CalculatedValueContainerFactory
import java.util.function.Function
import javax.inject.Inject

/**
 * Default implementation of [ResolutionOutputsInternal]. This class is in charge of
 * converting internal results in the form of [ResolverResults] into public facing types like:
 *
 *
 *  * [org.gradle.api.file.FileCollection]
 *  * [org.gradle.api.artifacts.ArtifactCollection]
 *  * [ArtifactView]
 *  * [org.gradle.api.artifacts.result.ResolvedVariantResult]
 *  * [org.gradle.api.artifacts.result.ResolvedComponentResult]
 *
 */
class DefaultResolutionOutputs(
    resolutionAccess: ResolutionAccess,
    taskDependencyFactory: TaskDependencyFactory,
    calculatedValueContainerFactory: CalculatedValueContainerFactory,
    attributesFactory: AttributesFactory,
    attributeDesugaring: AttributeDesugaring,
    objectFactory: ObjectFactory
) : ResolutionOutputsInternal {
    private val resolutionAccess: ResolutionAccess
    private val taskDependencyFactory: TaskDependencyFactory
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory
    private val attributesFactory: AttributesFactory
    private val attributeDesugaring: AttributeDesugaring
    private val objectFactory: ObjectFactory

    init {
        this.resolutionAccess = resolutionAccess
        this.taskDependencyFactory = taskDependencyFactory
        this.calculatedValueContainerFactory = calculatedValueContainerFactory
        this.attributesFactory = attributesFactory
        this.attributeDesugaring = attributeDesugaring
        this.objectFactory = objectFactory
    }

    override fun getResolutionResult(): ResolutionResult {
        return DefaultResolutionResult(resolutionAccess, attributeDesugaring)
    }

    override fun getFiles(): FileCollectionInternal {
        return doGetArtifactView(Actions.doNothing<ArtifactView.ViewConfiguration>()).getFiles()
    }

    override fun getArtifacts(): ArtifactCollectionInternal {
        return doGetArtifactView(Actions.doNothing<ArtifactView.ViewConfiguration>()).getArtifacts()
    }

    override fun artifactView(action: Action<in ArtifactView.ViewConfiguration>): ArtifactView {
        return doGetArtifactView(action)
    }

    private fun doGetArtifactView(action: Action<in ArtifactView.ViewConfiguration>): DefaultArtifactView {
        // We use the instantiator to generate closure-accepting methods.
        val viewConfiguration = objectFactory.newInstance<DefaultArtifactViewConfiguration>(DefaultArtifactViewConfiguration::class.java, attributesFactory)
        action.execute(viewConfiguration)

        return DefaultArtifactView(
            viewConfiguration.lenient,
            viewConfiguration.componentFilter,
            viewConfiguration.reselectVariants,
            viewConfiguration.viewAttributes,

            resolutionAccess,
            taskDependencyFactory,
            calculatedValueContainerFactory,
            attributesFactory,
            attributeDesugaring
        )
    }

    @VisibleForTesting
    class DefaultArtifactView(// View configuration
        private val lenient: Boolean,
        private val componentFilter: Spec<in ComponentIdentifier?>,
        private val reselectVariants: Boolean,
        private val viewAttributes: AttributeContainerInternal,

        // Services
        private val resolutionAccess: ResolutionAccess,
        taskDependencyFactory: TaskDependencyFactory,
        calculatedValueContainerFactory: CalculatedValueContainerFactory,
        attributesFactory: AttributesFactory,
        attributeDesugaring: AttributeDesugaring
    ) : ArtifactView {
        private val taskDependencyFactory: TaskDependencyFactory
        private val calculatedValueContainerFactory: CalculatedValueContainerFactory
        private val attributesFactory: AttributesFactory
        private val attributeDesugaring: AttributeDesugaring

        init {
            this.resolutionAccess = resolutionAccess
            this.taskDependencyFactory = taskDependencyFactory
            this.calculatedValueContainerFactory = calculatedValueContainerFactory
            this.attributesFactory = attributesFactory
            this.attributeDesugaring = attributeDesugaring
        }

        override fun getArtifacts(): ArtifactCollectionInternal {
            val selectedArtifacts: SelectedArtifactSet = ResolutionResultProviderBackedSelectedArtifactSet(
                resolutionAccess.getResults().map<SelectedArtifactSet>(Function { results: ResolverResults? -> this.selectArtifacts(results!!) })
            )

            return DefaultArtifactCollection(
                selectedArtifacts,
                lenient,
                resolutionAccess.getHost(),
                taskDependencyFactory,
                calculatedValueContainerFactory,
                attributeDesugaring
            )
        }

        override fun getFiles(): FileCollectionInternal {
            return getArtifacts().getArtifactFiles()
        }

        private fun selectArtifacts(results: ResolverResults): SelectedArtifactSet {
            // If the user set the view attributes, we allow variant matching to fail for no matching variants.
            // If we are using the original request attributes, variant matching should not fail.
            // TODO #27773: This is probably not desired behavior. It can be very confusing to request new attributes and
            // then have an ArtifactView silently return no results. We should add a switch specifying whether you
            // want 0 or 1 artifact result, 1 artifact result, or 1+ artifact results for each graph variant, and then
            // deprecate views that select no artifacts without the user specifying that switch.
            val allowNoMatchingVariants = !viewAttributes.isEmpty()

            return results.visitedArtifacts.select(
                ArtifactSelectionSpec(
                    getAttributes(),
                    componentFilter,
                    reselectVariants,
                    allowNoMatchingVariants,
                    resolutionAccess.getDefaultSortOrder()
                )
            )
        }

        override fun getAttributes(): ImmutableAttributes {
            val baseAttributes = resolutionAccess.getAttributes()

            // The user did not specify any attributes. Use the original request attributes.
            if (viewAttributes.isEmpty()) {
                return baseAttributes
            }

            // When re-selecting, we do not base the view attributes on the original request attributes.
            if (reselectVariants) {
                return viewAttributes.asImmutable()
            }

            // Otherwise, artifact views without re-selection are based on the original request attributes.
            return attributesFactory.concat(baseAttributes, viewAttributes.asImmutable())
        }
    }

    class DefaultArtifactViewConfiguration @Inject constructor(attributesFactory: AttributesFactory) : ArtifactView.ViewConfiguration {
        private val viewAttributes: AttributeContainerInternal
        private var componentFilter: Spec<in ComponentIdentifier?> = Specs.satisfyAll<ComponentIdentifier>()
        private var lenient = false
        private var reselectVariants = false

        init {
            this.viewAttributes = attributesFactory.mutable()
        }

        override fun getAttributes(): AttributeContainer {
            return viewAttributes
        }

        override fun attributes(action: Action<in AttributeContainer>): ArtifactView.ViewConfiguration {
            action.execute(viewAttributes)
            return this
        }

        override fun componentFilter(componentFilter: Spec<in ComponentIdentifier?>): ArtifactView.ViewConfiguration {
            check(this.componentFilter === Specs.SATISFIES_ALL) { "The component filter can only be set once before the view was computed" }
            this.componentFilter = componentFilter
            return this
        }

        override fun isLenient(): Boolean {
            return lenient
        }

        override fun setLenient(lenient: Boolean) {
            this.lenient = lenient
        }

        // TODO: Deprecate this in favor of setLenient(Boolean)
        override fun lenient(lenient: Boolean): ArtifactView.ViewConfiguration {
            this.lenient = lenient
            return this
        }

        override fun withVariantReselection(): ArtifactView.ViewConfiguration {
            this.reselectVariants = true
            return this
        }
    }
}
