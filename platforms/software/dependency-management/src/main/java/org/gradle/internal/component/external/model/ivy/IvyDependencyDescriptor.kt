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
package org.gradle.internal.component.external.model.ivy

import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSetMultimap
import com.google.common.collect.ListMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.SetMultimap
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor
import org.gradle.internal.component.model.ConfigurationGraphResolveState
import org.gradle.internal.component.model.ConfigurationMetadata
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.VariantGraphResolveState
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler
import java.util.LinkedList

/**
 * Represents a dependency as represented in an Ivy module descriptor file.
 */
class IvyDependencyDescriptor(
    @JvmField val selector: ModuleComponentSelector,
    @JvmField val dynamicConstraintVersion: String,
    val isChanging: Boolean,
    val isTransitive: Boolean,
    val isOptional: Boolean,
    confMappings: Multimap<String, String>,
    artifacts: MutableList<Artifact>,
    excludes: MutableList<Exclude>
) : ExternalDependencyDescriptor() {
    val confMappings: SetMultimap<String, String>
    val allExcludes: MutableList<Exclude>
    @JvmField
    val dependencyArtifacts: MutableList<Artifact>

    init {
        this.confMappings = ImmutableSetMultimap.copyOf<String, String>(confMappings)
        dependencyArtifacts = ImmutableList.copyOf<Artifact>(artifacts)
        this.allExcludes = ImmutableList.copyOf<Exclude>(excludes)
    }

    constructor(requested: ModuleComponentSelector, confMappings: ListMultimap<String, String>) : this(
        requested,
        requested.getVersion(),
        false,
        true,
        false,
        confMappings,
        mutableListOf<Artifact>(),
        mutableListOf<Exclude>()
    )

    override fun toString(): String {
        return "dependency: " + selector + ", confs: " + this.confMappings
    }

    val isConstraint: Boolean
        get() = false

    public override fun withRequested(newRequested: ModuleComponentSelector): IvyDependencyDescriptor {
        return IvyDependencyDescriptor(
            newRequested, dynamicConstraintVersion,
            this.isChanging,
            this.isTransitive, isOptional,
            this.confMappings,
            this.dependencyArtifacts,
            this.allExcludes
        )
    }

    /**
     * Choose a set of configurations from the target component.
     * The set chosen is based on a) the name of the configuration that declared this dependency and b) the [.confs] mapping for this dependency.
     *
     * The `confs` mapping is structured as `fromConfiguration -&gt; [targetConf...]`. Targets are collected for all configurations in the `fromConfiguration` hierarchy.
     * - '*' is a wildcard key, that matches _all_ `fromConfiguration values.
     * - '*, !A' is a key that matches _all_ `fromConfiguration values _except_ 'A'.
     * - '%' is a key that matches a `fromConfiguration` value that is not matched by any of the other keys.
     * - '@' and '#' are special values for matching target configurations. See [the Ivy docs](http://ant.apache.org/ivy/history/latest-milestone/ivyfile/dependency.html) for details.
     */
    fun selectLegacyConfigurations(
        fromConfiguration: ConfigurationMetadata,
        ivyComponent: IvyComponentGraphResolveState,
        resolutionFailureHandler: ResolutionFailureHandler
    ): MutableList<out VariantGraphResolveState> {
        // TODO - all this matching stuff is constant for a given DependencyMetadata instance
        val targets: MutableList<ConfigurationGraphResolveState> = LinkedList<ConfigurationGraphResolveState>()
        var matched = false
        val fromConfigName: String = fromConfiguration.name!!
        for (config in fromConfiguration.hierarchy) {
            if (confMappings.containsKey(config)) {
                val targetPatterns = confMappings.get(config)
                if (!targetPatterns.isEmpty()) {
                    matched = true
                }
                for (targetPattern in targetPatterns) {
                    findMatches(ivyComponent, fromConfigName, config, targetPattern, targets, resolutionFailureHandler)
                }
            }
        }
        if (!matched && confMappings.containsKey("%")) {
            for (targetPattern in confMappings.get("%")) {
                findMatches(ivyComponent, fromConfigName, fromConfigName, targetPattern, targets, resolutionFailureHandler)
            }
        }

        // TODO - this is not quite right, eg given *,!A->A;*,!B->B the result should be B->A and A->B but will in fact be B-> and A->
        val wildcardPatterns = confMappings.get("*")
        if (!wildcardPatterns.isEmpty()) {
            var excludeWildcards = false
            for (confName in fromConfiguration.hierarchy) {
                if (confMappings.containsKey("!" + confName)) {
                    excludeWildcards = true
                    break
                }
            }
            if (!excludeWildcards) {
                for (targetPattern in wildcardPatterns) {
                    findMatches(ivyComponent, fromConfigName, fromConfigName, targetPattern, targets, resolutionFailureHandler)
                }
            }
        }

        val builder = ImmutableList.builderWithExpectedSize<VariantGraphResolveState>(targets.size)
        for (target in targets) {
            builder.add(target.asVariant()!!)
        }

        return builder.build()
    }

    private fun findMatches(
        targetComponent: IvyComponentGraphResolveState,
        fromConfiguration: String,
        patternConfiguration: String,
        targetPattern: String,
        targetConfigurations: MutableList<ConfigurationGraphResolveState>,
        resolutionFailureHandler: ResolutionFailureHandler
    ) {
        var targetPattern = targetPattern
        val startFallback = targetPattern.indexOf('(')
        if (startFallback >= 0) {
            if (targetPattern.endsWith(")")) {
                val preferred = targetPattern.substring(0, startFallback)
                val configuration = targetComponent.getConfiguration(preferred)
                if (configuration != null) {
                    maybeAddConfiguration(targetConfigurations, configuration)
                    return
                }
                targetPattern = targetPattern.substring(startFallback + 1, targetPattern.length - 1)
            }
        }

        if (targetPattern == "*") {
            for (targetName in targetComponent.getConfigurationNames()) {
                val configuration = targetComponent.getConfiguration(targetName)
                if (configuration!!.getMetadata()!!.isVisible()) {
                    maybeAddConfiguration(targetConfigurations, configuration)
                }
            }
            return
        }

        if (targetPattern == "@") {
            targetPattern = patternConfiguration
        } else if (targetPattern == "#") {
            targetPattern = fromConfiguration
        }

        val configuration = targetComponent.getConfiguration(targetPattern)
        if (configuration == null) {
            throw resolutionFailureHandler.configurationDoesNotExistFailure(targetComponent, targetPattern)
        }
        maybeAddConfiguration(targetConfigurations, configuration)
    }

    private fun maybeAddConfiguration(configurations: MutableList<ConfigurationGraphResolveState>, toAdd: ConfigurationGraphResolveState) {
        val iter = configurations.iterator()
        while (iter.hasNext()) {
            val configuration = iter.next()
            if (configuration.getMetadata()!!.getHierarchy()!!.contains(toAdd.getName()!!)) {
                // this configuration is a child of toAdd, so no need to add it
                return
            }
            if (toAdd.getMetadata()!!.getHierarchy()!!.contains(configuration.getName()!!)) {
                // toAdd is a child, so implies this configuration
                iter.remove()
            }
        }
        configurations.add(toAdd)
    }

    fun getConfigurationExcludes(configurations: MutableCollection<String>): ImmutableList<ExcludeMetadata> {
        if (allExcludes.isEmpty()) {
            return ImmutableList.of<ExcludeMetadata>()
        }
        val rules = ImmutableList.builderWithExpectedSize<ExcludeMetadata>(
            allExcludes.size
        )
        for (exclude in this.allExcludes) {
            val ruleConfigurations: MutableSet<String> = exclude.configurations!!
            if (include(ruleConfigurations, configurations)) {
                rules.add(exclude)
            }
        }
        return rules.build()
    }

    fun getConfigurationArtifacts(fromConfiguration: ConfigurationMetadata): ImmutableList<IvyArtifactName> {
        if (dependencyArtifacts.isEmpty()) {
            return ImmutableList.of<IvyArtifactName>()
        }

        val includedConfigurations: MutableCollection<String> = fromConfiguration.hierarchy
        val artifacts = ImmutableList.builder<IvyArtifactName>()
        for (depArtifact in dependencyArtifacts) {
            val artifactConfigurations = depArtifact.configurations
            if (include(artifactConfigurations, includedConfigurations)) {
                val ivyArtifactName = depArtifact.artifactName
                artifacts.add(ivyArtifactName)
            }
        }
        return artifacts.build()
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as IvyDependencyDescriptor
        return this.isChanging == that.isChanging && this.isTransitive == that.isTransitive && this.isOptional == that.isOptional && Objects.equal(selector, that.selector)
                && Objects.equal(dynamicConstraintVersion, that.dynamicConstraintVersion)
                && Objects.equal(this.confMappings, that.confMappings)
                && Objects.equal(this.allExcludes, that.allExcludes)
                && Objects.equal(dependencyArtifacts, that.dependencyArtifacts)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(
            selector,
            dynamicConstraintVersion,
            this.isChanging,
            this.isTransitive,
            this.isOptional,
            this.confMappings,
            this.allExcludes,
            dependencyArtifacts
        )
    }

    companion object {
        protected fun include(configurations: Iterable<String>, acceptedConfigurations: MutableCollection<String>): Boolean {
            for (configuration in configurations) {
                if (configuration == "*") {
                    return true
                }
                if (acceptedConfigurations.contains(configuration)) {
                    return true
                }
            }
            return false
        }
    }
}
