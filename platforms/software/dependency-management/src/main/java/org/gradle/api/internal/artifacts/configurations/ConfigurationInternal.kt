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
package org.gradle.api.internal.artifacts.configurations

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.DisplayName
import org.gradle.internal.deprecation.DeprecatableConfiguration

interface ConfigurationInternal : DeprecatableConfiguration, Configuration {
    // This type is referenced by Nebula:
    // https://github.com/nebula-plugins/gradle-resolution-rules-plugin/blob/db24ee7e0b5c5c6f6327cdfd377e90e505bb1fd2/src/main/kotlin/nebula/plugin/resolutionrules/configurations.kt#L59
    enum class InternalState {
        UNRESOLVED,
        OBSERVED
    }

    @JvmField
    val displayName: String?

    fun asDescribable(): DisplayName?

    override fun getAttributes(): AttributeContainerInternal?

    override fun getResolutionStrategy(): ResolutionStrategyInternal?

    /**
     * Runs any registered dependency actions for this Configuration, and any parent Configuration.
     * Actions may mutate the dependency set for this configuration.
     * After execution, all actions are de-registered, so execution will only occur once.
     */
    fun runDependencyActions()

    /**
     * Marks this configuration as observed, meaning its state has been seen by some external operation
     * and further changes to this configuration that would change its public state are forbidden.
     *
     *
     * The state guarded by this method includes all mutable state except for the dependencies,
     * dependency constraints, and global excludes of this configuration. After configuration
     * dependencies are observed, [.markDependenciesObserved] should be called.
     *
     * @param reason Describes the external operation that observed this configuration
     */
    fun markAsObserved(reason: String)

    /**
     * Marks the dependencies of a configuration observed, after which the dependencies,
     * dependency constraints, and global excludes of this configuration cannot be mutated.
     *
     * @throws IllegalStateException if [.markAsObserved] has not yet been called.
     */
    fun markDependenciesObserved()

    @JvmField
    val domainObjectContext: DomainObjectContext?

    /**
     * Visits the variants of this configuration.
     */
    fun collectVariants(visitor: VariantVisitor)

    @JvmField
    val isCanBeMutated: Boolean

    /**
     * Gets the complete set of exclude rules including those contributed by
     * superconfigurations.
     */
    @JvmField
    val allExcludeRules: MutableSet<ExcludeRule>?

    /**
     * @see ResolutionParameters.getConfigurationIdentity
     */
    val configurationIdentity: ConfigurationIdentity?

    /**
     * @see ResolutionParameters.getResolutionHost
     */
    val resolutionHost: ResolutionHost?

    /**
     * Return true if this is a detached configuration, false otherwise.
     */
    val isDetachedConfiguration: Boolean

    /**
     * Version locks to use during resolution as a result of consistent resolution.
     */
    val consistentResolutionVersionLocks: ImmutableList<ResolutionParameters.ModuleVersionLock>?

    /**
     * @implSpec Usage: This method should only be called on resolvable configurations and should throw an exception if
     * called on a configuration that does not permit this usage.
     */
    val consistentResolutionSource: ConfigurationInternal?

    /**
     * A handle to the internal view of the results of resolution of this configuration.
     */
    val resolutionAccess: ResolutionAccess?

    val isDeclarableByExtension: Boolean
        /**
         * Test if this configuration can either be declared against or extends another
         * configuration which can be declared against.
         *
         * @return `true` if so; `false` otherwise
         */
        get() = isDeclarableByExtension(this)

    /**
     * Returns the role used to create this configuration and set its initial allowed usage.
     */
    val roleAtCreation: ConfigurationRole?

    interface VariantVisitor {
        // This configuration as a variant. May not always be present
        fun visitOwnVariant(displayName: DisplayName, attributes: ImmutableAttributes, artifacts: MutableCollection<out PublishArtifact>)

        // A child variant. May not always be present
        fun visitChildVariant(name: String, displayName: DisplayName, attributes: ImmutableAttributes, artifacts: MutableCollection<out PublishArtifact>)
    }

    companion object {
        /**
         * Test if the given configuration can either be declared against or extends another
         * configuration which can be declared against.
         * This method should probably be made `private` when upgrading to Java 9.
         *
         * @param configuration the configuration to test
         * @return `true` if so; `false` otherwise
         */
        fun isDeclarableByExtension(configuration: ConfigurationInternal): Boolean {
            if (configuration.isCanBeDeclared()) {
                return true
            } else {
                return configuration.getExtendsFrom().stream()
                    .map<ConfigurationInternal> { obj: Configuration? -> ConfigurationInternal::class.java.cast(obj) }
                    .anyMatch { ci: ConfigurationInternal? -> ci!!.isDeclarableByExtension }
            }
        }
    }
}
