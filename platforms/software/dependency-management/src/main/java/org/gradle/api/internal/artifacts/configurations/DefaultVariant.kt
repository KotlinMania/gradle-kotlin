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
package org.gradle.api.internal.artifacts.configurations

import org.gradle.api.Action
import org.gradle.api.Describable
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.ConfigurationVariantInternal
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.FreezableAttributeContainer
import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.Factory
import org.gradle.internal.typeconversion.NotationParser
import javax.inject.Inject

abstract class DefaultVariant @Inject constructor(
    parentDisplayName: Describable,
    private val name: String,
    parentAttributes: AttributeContainerInternal,
    private val artifactNotationParser: NotationParser<Any, ConfigurablePublishArtifact>,
    fileCollectionFactory: FileCollectionFactory,
    attributesFactory: AttributesFactory,
    domainObjectCollectionFactory: DomainObjectCollectionFactory,
    taskDependencyFactory: TaskDependencyFactory
) : ConfigurationVariantInternal {
    private val displayName: DisplayName
    private val attributes: FreezableAttributeContainer
    private val artifacts: PublishArtifactSet

    private var lazyArtifacts: Factory<MutableList<PublishArtifact>?>? = null

    init {
        this.displayName = Describables.of(parentDisplayName, Describables.quoted("variant", name))
        this.attributes = attributesFactory.freezable(attributesFactory.mutable(parentAttributes), displayName)
        this.artifacts =
            DefaultPublishArtifactSet(displayName, domainObjectCollectionFactory.newDomainObjectSet<PublishArtifact>(PublishArtifact::class.java), fileCollectionFactory, taskDependencyFactory)
    }

    override fun getName(): String {
        return name
    }

    override fun getDisplayName(): DisplayName {
        return displayName
    }

    override fun getAttributes(): AttributeContainerInternal {
        return attributes
    }

    override fun attributes(action: Action<in AttributeContainer>): ConfigurationVariant {
        action.execute(attributes)
        return this
    }

    override fun getArtifacts(): PublishArtifactSet {
        if (lazyArtifacts != null) {
            artifacts.addAll(lazyArtifacts!!.create()!!)
            lazyArtifacts = null
        }
        return artifacts
    }

    override fun artifact(notation: Any) {
        artifacts.add(artifactNotationParser.parseNotation(notation))
    }

    override fun artifact(notation: Any, configureAction: Action<in ConfigurablePublishArtifact>) {
        val publishArtifact = artifactNotationParser.parseNotation(notation)
        artifacts.add(publishArtifact)
        configureAction.execute(publishArtifact)
    }

    override fun toString(): String {
        return getDisplayName().getDisplayName()
    }

    override fun artifactsProvider(artifacts: Factory<MutableList<PublishArtifact>?>) {
        this.lazyArtifacts = artifacts
    }

    override fun preventFurtherMutation() {
        attributes.freeze()
    }
}
