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

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.ConfigurationPublications
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.DomainObjectCollectionInternal
import org.gradle.api.internal.artifacts.ConfigurationVariantInternal
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.internal.DisplayName
import org.gradle.internal.typeconversion.NotationParser
import java.util.function.Supplier
import javax.inject.Inject

class DefaultConfigurationPublications @Inject constructor(// Parent state
    private val displayName: DisplayName,
    // Mutable state
    private val artifacts: PublishArtifactSet,
    private val allArtifacts: PublishArtifactSetProvider,
    private val parentAttributes: AttributeContainerInternal,
    // Services
    private val objectFactory: ObjectFactory,
    private val artifactNotationParser: NotationParser<Any, ConfigurablePublishArtifact>,
    private val capabilityNotationParser: NotationParser<Any, Capability>,
    private val fileCollectionFactory: FileCollectionFactory,
    private val attributesFactory: AttributesFactory,
    private val domainObjectCollectionFactory: DomainObjectCollectionFactory,
    private val taskDependencyFactory: TaskDependencyFactory
) : ConfigurationPublications {
    private val attributes: AttributeContainerInternal
    private var variants: NamedDomainObjectContainer<ConfigurationVariant>? = null
    private var capabilities: DomainObjectSet<Capability>? = null
    private var observationReason: Supplier<String>? = null

    init {
        this.attributes = attributesFactory.mutable(parentAttributes)
    }

    fun collectVariants(visitor: ConfigurationInternal.VariantVisitor) {
        val allArtifactSet = allArtifacts.publishArtifactSet
        if (variants == null || variants!!.isEmpty() || !allArtifactSet.isEmpty()) {
            visitor.visitOwnVariant(displayName, attributes.asImmutable(), allArtifactSet)
        }
        if (variants != null) {
            for (variant in variants!!.withType<ConfigurationVariantInternal>(ConfigurationVariantInternal::class.java)) {
                visitor.visitChildVariant(variant.getName(), variant.getDisplayName(), variant.getAttributes().asImmutable(), variant.getArtifacts())
            }
        }
    }

    override fun getAttributes(): AttributeContainer {
        return attributes
    }

    override fun attributes(action: Action<in AttributeContainer>): ConfigurationPublications {
        action.execute(attributes)
        return this
    }

    override fun getArtifacts(): PublishArtifactSet {
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

    override fun artifacts(provider: Provider<out Iterable<out Any>>) {
        artifacts.addAllLater(provider.map<MutableList<PublishArtifact>>({ iterable: Iterable<out Any> ->
            val results: MutableList<PublishArtifact> = ArrayList<PublishArtifact>()
            iterable.forEach { notation: Any -> results.add(artifactNotationParser.parseNotation(notation)) }
            results
        }))
    }

    override fun artifacts(provider: Provider<out Iterable<out Any>>, configureAction: Action<in ConfigurablePublishArtifact>) {
        artifacts.addAllLater(provider.map<MutableList<PublishArtifact>>({ iterable: Iterable<out Any> ->
            val results: MutableList<PublishArtifact> = ArrayList<PublishArtifact>()
            iterable.forEach { notation: Any ->
                val artifact = artifactNotationParser.parseNotation(notation)
                configureAction.execute(artifact)
                results.add(artifact)
            }
            results
        }))
    }

    override fun getVariants(): NamedDomainObjectContainer<ConfigurationVariant> {
        if (variants == null) {
            // Create variants container only as required
            variants = domainObjectCollectionFactory.newNamedDomainObjectContainer<ConfigurationVariant>(
                ConfigurationVariant::class.java,
                NamedDomainObjectFactory { name: String? -> this.createVariant(name!!) })
            (variants as DomainObjectCollectionInternal<*>).beforeCollectionChanges(Action { variantName: String? ->
                if (this.isObserved) {
                    throw InvalidUserCodeException("Cannot add secondary artifact set to " + displayName + " after " + observationReason!!.get() + ".")
                }
            })
        }
        return variants!!
    }

    override fun variants(configureAction: Action<in NamedDomainObjectContainer<ConfigurationVariant>>) {
        configureAction.execute(getVariants())
    }

    override fun capability(notation: Any) {
        if (this.isObserved) {
            throw InvalidUserCodeException("Cannot declare capability '" + notation + "' on " + displayName + " after " + observationReason!!.get() + ".")
        }

        if (capabilities == null) {
            capabilities = domainObjectCollectionFactory.newDomainObjectSet<Capability>(Capability::class.java)
        }
        if (notation is Provider<*>) {
            capabilities!!.addLater(notation.map<Capability>({ notation: N? -> capabilityNotationParser.parseNotation(notation) }))
        } else {
            val descriptor = capabilityNotationParser.parseNotation(notation)
            capabilities!!.add(descriptor!!)
        }
    }

    override fun getCapabilities(): MutableCollection<out Capability> {
        return if (capabilities == null) mutableListOf<Capability>() else ImmutableList.copyOf<Capability>(capabilities)
    }

    fun preventFromFurtherMutation(observationReason: Supplier<String>) {
        this.observationReason = observationReason
        if (variants != null) {
            for (variant in variants) {
                (variant as ConfigurationVariantInternal).preventFurtherMutation()
            }
        }
    }

    private val isObserved: Boolean
        get() = observationReason != null

    private fun createVariant(name: String): DefaultVariant {
        return objectFactory.newInstance<DefaultVariant>(
            DefaultVariant::class.java,
            displayName,
            name,
            parentAttributes,
            artifactNotationParser,
            fileCollectionFactory,
            attributesFactory,
            domainObjectCollectionFactory,
            taskDependencyFactory
        )
    }
}
