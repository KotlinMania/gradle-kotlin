/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.publish.internal.component

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.component.ConfigurationVariantDetails
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.Actions
import org.gradle.internal.deprecation.DeprecationLogger.deprecateBehaviour
import java.util.function.Consumer
import javax.inject.Inject

class ConfigurationVariantMapping(private val outgoingConfiguration: ConfigurationInternal, private var action: Action<in ConfigurationVariantDetails>, private val objectFactory: ObjectFactory) {
    fun addAction(action: Action<in ConfigurationVariantDetails>) {
        this.action = Actions.composite<ConfigurationVariantDetails>(this.action, action)
    }

    fun collectVariants(collector: Consumer<UsageContext>) {
        outgoingConfiguration.runDependencyActions()
        outgoingConfiguration.markAsObserved("published as a variant")
        outgoingConfiguration.markDependenciesObserved()
        val outgoingConfigurationName = outgoingConfiguration.getName()

        if (!outgoingConfiguration.isTransitive()) {
            deprecateBehaviour(String.format("Publishing non-transitive configuration '%s'.", outgoingConfigurationName))
                .withContext("Setting 'transitive = false' at the configuration level is ignored by publishing.")!!
                .withAdvice("Consider using 'transitive = false' on each dependency if this needs to be published.")!!
                .willBecomeAnErrorInGradle10()
                .undocumented()!! // TODO: We don't have documentation for this anymore?
                .nagUser()
        }

        val seen: MutableSet<String> = HashSet<String>()

        // Visit implicit sub-variant
        val defaultConfigurationVariant: ConfigurationVariant = objectFactory.newInstance<DefaultConfigurationVariant>(DefaultConfigurationVariant::class.java, outgoingConfiguration)
        visitVariant(collector, seen, defaultConfigurationVariant, outgoingConfigurationName)

        // Visit explicit sub-variants
        val subvariants = outgoingConfiguration.getOutgoing().getVariants()
        for (subvariant in subvariants) {
            val publishedVariantName = outgoingConfigurationName + StringUtils.capitalize(subvariant.getName())
            visitVariant(collector, seen, subvariant, publishedVariantName)
        }
    }

    private fun visitVariant(
        collector: Consumer<UsageContext>,
        seen: MutableSet<String>,
        subvariant: ConfigurationVariant,
        name: String
    ) {
        val details = objectFactory.newInstance<DefaultConfigurationVariantDetails>(DefaultConfigurationVariantDetails::class.java, subvariant)
        action.execute(details)

        if (!details.shouldPublish()) {
            return
        }

        if (!seen.add(name)) {
            throw InvalidUserDataException("Cannot add feature variant '" + name + "' as a variant with the same name is already registered")
        }

        collector.accept(
            FeatureConfigurationVariant(
                name,
                outgoingConfiguration,
                subvariant,
                details.mavenScope,
                details.isOptional,
                details.dependencyMappingDetails
            )
        )
    }

    // Cannot be private due to reflective instantiation
    internal abstract class DefaultConfigurationVariant @Inject constructor(private val outgoingConfiguration: ConfigurationInternal) : ConfigurationVariant {
        init {
            getDescription().convention(outgoingConfiguration.getDescription()).finalizeValueOnRead()
        }

        override fun getArtifacts(): PublishArtifactSet {
            return outgoingConfiguration.getArtifacts()
        }

        override fun artifact(notation: Any) {
            throw InvalidUserCodeException("Cannot add artifacts during filtering")
        }

        override fun artifact(notation: Any, configureAction: Action<in ConfigurablePublishArtifact>) {
            throw InvalidUserCodeException("Cannot add artifacts during filtering")
        }

        override fun getName(): String {
            return outgoingConfiguration.getName()
        }

        override fun attributes(action: Action<in AttributeContainer>): ConfigurationVariant? {
            throw InvalidUserCodeException("Cannot mutate outgoing configuration during filtering")
        }

        override fun getAttributes(): AttributeContainer {
            return outgoingConfiguration.getAttributes()!!
        }
    }

    // Cannot be private due to reflective instantiation
    internal class DefaultConfigurationVariantDetails @Inject constructor(private val variant: ConfigurationVariant, private val objectFactory: ObjectFactory) : ConfigurationVariantDetailsInternal {
        private var skip = false
        var mavenScope: String = "compile"
            private set
        var isOptional: Boolean = false
            private set
        private var dependencyMappingDetails: DefaultDependencyMappingDetails? = null

        override fun getConfigurationVariant(): ConfigurationVariant {
            return variant
        }

        override fun skip() {
            skip = true
        }

        override fun mapToOptional() {
            this.isOptional = true
        }

        override fun mapToMavenScope(scope: String) {
            this.mavenScope = assertValidScope(scope)
        }

        override fun dependencyMapping(action: Action<in ConfigurationVariantDetailsInternal.DependencyMappingDetails>) {
            if (dependencyMappingDetails == null) {
                dependencyMappingDetails = objectFactory.newInstance<DefaultDependencyMappingDetails>(DefaultDependencyMappingDetails::class.java)
            }
            action.execute(dependencyMappingDetails)
        }

        fun shouldPublish(): Boolean {
            return !skip
        }

        companion object {
            private fun assertValidScope(scope: String): String {
                var scope = scope
                scope = scope.lowercase()
                if ("compile" == scope || "runtime" == scope) {
                    return scope
                }
                throw InvalidUserCodeException("Invalid Maven scope '" + scope + "'. You must choose between 'compile' and 'runtime'")
            }
        }
    }

    abstract class DefaultDependencyMappingDetails : ConfigurationVariantDetailsInternal.DependencyMappingDetails {
        var resolutionConfiguration: Configuration? = null
            private set

        override fun fromResolutionOf(configuration: Configuration) {
            this.resolutionConfiguration = configuration
        }
    }
}
