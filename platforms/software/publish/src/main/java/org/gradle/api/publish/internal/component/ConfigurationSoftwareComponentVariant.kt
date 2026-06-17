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

import com.google.common.collect.ImmutableSet
import org.gradle.api.DomainObjectSet
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import org.gradle.api.component.SoftwareComponentVariant
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.component.AbstractSoftwareComponentVariant

/**
 * A [SoftwareComponentVariant] based on a consumable [Configuration].
 */
open class ConfigurationSoftwareComponentVariant(protected val name: String, attributes: AttributeContainer, artifacts: MutableSet<out PublishArtifact>, private val configuration: Configuration) :
    AbstractSoftwareComponentVariant((attributes as AttributeContainerInternal).asImmutable(), artifacts) {
    private var dependencies: DomainObjectSet<ModuleDependency>? = null
    private var dependencyConstraints: DomainObjectSet<DependencyConstraint>? = null
    private var capabilities: MutableSet<out Capability>? = null
    private var excludeRules: MutableSet<ExcludeRule>? = null

    constructor(base: SoftwareComponentVariant, artifacts: MutableSet<out PublishArtifact>, configuration: Configuration) : this(base.getName(), base.getAttributes(), artifacts, configuration)

    override fun getName(): String {
        return name
    }

    override fun getDependencies(): MutableSet<ModuleDependency> {
        if (dependencies == null) {
            dependencies = configuration.getIncoming().getDependencies().withType<ModuleDependency>(ModuleDependency::class.java)
        }
        return dependencies!!
    }

    override fun getDependencyConstraints(): MutableSet<out DependencyConstraint> {
        if (dependencyConstraints == null) {
            dependencyConstraints = configuration.getIncoming().getDependencyConstraints()
        }
        return dependencyConstraints!!
    }

    override fun getCapabilities(): MutableSet<out Capability> {
        if (capabilities == null) {
            this.capabilities = ImmutableSet.copyOf<Capability>(
                Configurations.collectCapabilities(
                    configuration,
                    HashSet<Capability?>(),
                    HashSet<Configuration?>()
                )
            )
        }
        return capabilities!!
    }

    override fun getGlobalExcludes(): MutableSet<ExcludeRule> {
        if (excludeRules == null) {
            this.excludeRules = ImmutableSet.copyOf((configuration as ConfigurationInternal).allExcludeRules)
        }
        return excludeRules!!
    }
}
