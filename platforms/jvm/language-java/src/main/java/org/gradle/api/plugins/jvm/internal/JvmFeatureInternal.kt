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
package org.gradle.api.plugins.jvm.internal

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.ConsumableConfiguration

/**
 * A Jvm Feature wraps a source set to encapsulate the logic and domain objects required to
 * implement a feature of a JVM component. Features are used to model constructs like
 * production libraries, test suites, test fixtures, applications, etc. While features are not
 * individually consumable themselves for publication or through dependency-management,
 * they can be exposed to consumers via an owning component.
 *
 *
 * Features are classified by their capabilities. Each variant of a feature provides at least
 * the same set of capabilities as the feature itself. Since all variants of a feature are derived
 * from the same sources, they all expose the same API or "content" and thus provide the same
 * capabilities. Some variants may expose additional capabilities than those of its owning feature,
 * for example with fat jars.
 *
 *
 * TODO: The current API is written as if this were a single-target feature. Before we make this API
 * public, we should make this API multi-target aware. Alternatively, we could implement a
 * SingleTargetJvmFeature now and in the future implement a MultiTargetJvmFeature when we're ready.
 * This would allow us to use the JvmFeature interface as a common parent interface.
 */
interface JvmFeatureInternal : Named {
    /**
     * Get the capabilities of this feature. All variants exposed by this feature must provide at least
     * the same capabilities as this feature.
     */
    val capabilities: ImmutableCapabilities?

    /**
     * Configures this feature to publish a variant containing a list of this feature's source directories.
     */
    fun withSourceElements()

    /**
     * Adds the `api` and `compileOnlyApi` dependency configurations to this feature.
     *
     * TODO: Should this live on the "base" JVM feature? Should all JVM features know how to add
     * an API? Or should we have subclasses which have APIs and others, which support
     * application features and test suites, which do not have APIs?
     */
    fun withApi()

    /**
     * Creates the javadoc elements configuration, which exposes the javadoc jar of this feature, if
     * it has not been created already. Any tasks needed to produce the artifacts of the configuration
     * are also created and configured if necessary.
     */
    fun maybeRegisterJavadocElements(): NamedDomainObjectProvider<ConsumableConfiguration?>?

    /**
     * Creates the sources elements configuration, which exposes the sources jar of this feature, if
     * it has not been created already. Any tasks needed to produce the artifacts of the configuration
     * are also created and configured if necessary.
     */
    fun maybeRegisterSourcesElements(): NamedDomainObjectProvider<ConsumableConfiguration?>?

    // TODO: Many of the methods below probably belong on a JvmTarget. Features may have many targets
    // and thus many configurations, jar tasks, compile tasks, etc.
    /**
     * Get the [Jar] task which assembles the resources and compilation outputs into
     * a single artifact.
     *
     * @return A provider which supplies the feature's [Jar] task.
     */
    @JvmField
    val jarTask: TaskProvider<Jar?>?

    /**
     * Get the [JavaCompile] task which compiles the Java source files into classes.
     *
     * @return A provider which supplies the feature's [JavaCompile] task.
     */
    @JvmField
    val compileJavaTask: TaskProvider<JavaCompile?>?

    /**
     * Get this feature's backing source set.
     *
     *
     * [SourceSet.getOutput] and the classpath-returning methods on the returned
     * source set should ideally be avoided in favor of the similarly-named methods on
     * this feature. The concept of source sets having a single set of outputs is only
     * relevant for single-target features.
     *
     * @return This feature's source set.
     */
    @JvmField
    val sourceSet: SourceSet?

    /**
     * Gets the dependency configuration for which to declare dependencies internal to the feature.
     * Dependencies declared on this configuration are present during compilation and runtime, but are not
     * exposed as part of the feature's API variant.
     *
     * @return The `implementation` configuration.
     */
    @JvmField
    val implementationConfiguration: Configuration?

    /**
     * Gets the dependency configuration for which to declare runtime-only dependencies.
     * Dependencies declared on this configuration are present only during runtime, are not
     * present during compilation, and are not exposed as part of the feature's API variant.
     *
     * @return The `runtimeOnly` configuration.
     */
    val runtimeOnlyConfiguration: Configuration?

    /**
     * Gets the dependency configuration for which to declare compile-only dependencies.
     * Dependencies declared on this configuration are present only during compilation, are not
     * present during runtime, and are not exposed as part of the feature's API variant.
     *
     * @return The `compileOnly` configuration.
     */
    val compileOnlyConfiguration: Configuration?

    /**
     * Gets the dependency configuration for which to declare API dependencies.
     * Dependencies declared on this configuration are present during compilation
     * and runtime, and are exposed as part of the feature's API variant.
     *
     * @return null if [.withApi] has not been called.
     */
    @JvmField
    val apiConfiguration: Configuration?

    /**
     * Gets the dependency configuration for which to declare compile-only API dependencies.
     * Dependencies declared on this configuration are present during compilation
     * but not runtime, and are exposed as part of the feature's API variant.
     *
     * @return null if [.withApi] has not been called.
     */
    @JvmField
    val compileOnlyApiConfiguration: Configuration?

    /**
     * Get the resolvable configuration containing the resolved runtime dependencies
     * for this feature. This configuration does not contain the artifacts from the
     * feature's compilation itself.
     *
     * @return The `runtimeClasspath` configuration.
     */
    @JvmField
    val runtimeClasspathConfiguration: Configuration?

    /**
     * Get the resolvable configuration containing the resolved compile dependencies
     * for this feature.
     *
     * @return The `compileClasspath` configuration.
     */
    @JvmField
    val compileClasspathConfiguration: Configuration?

    /**
     * Get the consumable configuration which produces the `apiElements` variant of this feature.
     * This configuration includes all API compilation dependencies as well as the feature's
     * compilation outputs, but does not include `implementation`, `compileOnly`,
     * or `runtimeOnly` dependencies.
     *
     * @return The `apiElements` configuration.
     */
    @JvmField
    val apiElementsConfiguration: NamedDomainObjectProvider<ConsumableConfiguration?>?

    /**
     * Get the consumable configuration which produces the `runtimeElements` variant of this feature.
     * This configuration includes all runtime dependencies as well as the feature's
     * compilation outputs, but does not include `compileOnly` dependencies.
     *
     * @return The `runtimeElements` configuration.
     */
    @JvmField
    val runtimeElementsConfiguration: NamedDomainObjectProvider<ConsumableConfiguration?>?
}
