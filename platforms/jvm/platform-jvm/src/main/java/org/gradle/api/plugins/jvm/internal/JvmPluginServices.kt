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
package org.gradle.api.plugins.jvm.internal

import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.attributes.HasConfigurableAttributes
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.HasInternalProtocol
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.jspecify.annotations.NullMarked

/**
 * This class exposes a number of internal utilities for use by Gradle JVM plugins.
 */
@NullMarked
@HasInternalProtocol
@ServiceScope(Scope.Project::class)
@Suppress("deprecation")
interface JvmPluginServices : JvmEcosystemUtilities {
    /**
     * Registers a variant on `configuration` which exposes the resources defined by `sourceSet`.
     *
     * @param configuration The [Configuration] for which a resources variant should be exposed.
     * @param sourceSet The [SourceSet] which will contribute resources to this variant.
     */
    fun configureResourcesDirectoryVariant(configuration: Configuration, sourceSet: SourceSet): ConfigurationVariant?

    /**
     * Registers a variant on `configuration` which exposes the classses defined by `sourceSet`.
     *
     * @param configuration The [Configuration] for which a classes variant should be exposed.
     * @param sourceSet The [SourceSet] which will contribute classes to this variant.
     */
    fun configureClassesDirectoryVariant(configuration: Configuration, sourceSet: SourceSet): ConfigurationVariant?

    /**
     * Configures a configuration with reasonable defaults to be resolved as a compile classpath.
     *
     * @param configuration the configuration to be configured
     */
    fun configureAsCompileClasspath(configuration: HasConfigurableAttributes<*>)

    /**
     * Configures a consumable configuration to provide an API compile classpath.
     *
     * @param configuration the configuration to be configured
     */
    fun configureAsApiElements(configuration: HasConfigurableAttributes<*>)

    /**
     * Configures a consumable configuration to provide a runtime classpath.
     *
     * @param configuration the configuration to be configured
     */
    fun configureAsRuntimeElements(configuration: HasConfigurableAttributes<*>)

    /**
     * Configures a configuration with reasonable defaults to be resolved as a project's main sources variant.
     *
     * @param configuration the configuration to be configured
     */
    fun configureAsSources(configuration: HasConfigurableAttributes<*>)

    fun <T> configureAttributes(configurableAttributes: HasConfigurableAttributes<T?>, details: Action<in JvmEcosystemAttributesDetails>)

    /**
     * Replaces the artifacts of an outgoing configuration with a new set of artifacts.
     * This can be used whenever the default artifacts configured are not the ones you want to publish.
     * If this configuration inherits from other configurations, their artifacts will be removed.
     *
     * @param outgoingConfiguration the configuration for which to replace artifacts
     * @param providers the artifacts or providers of artifacts (e.g tasks providers) which should be associated with this configuration
     */
    fun replaceArtifacts(outgoingConfiguration: Configuration, vararg providers: Any)
}
