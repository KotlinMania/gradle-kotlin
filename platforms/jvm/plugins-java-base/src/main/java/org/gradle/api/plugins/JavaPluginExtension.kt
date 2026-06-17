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
package org.gradle.api.plugins

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.java.archives.Manifest
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.jvm.toolchain.JavaToolchainSpec

/**
 * Common configuration for JVM (Java) based projects.
 *
 * This extension is added by the [JavaBasePlugin] and would be more appropriately named
 * the `JvmPluginExtension` extension.  It is used to configure many of the project's
 * JVM-related settings and behavior.
 *
 * @since 4.10
 */
interface JavaPluginExtension {
    /**
     * Sets the source compatibility used for compiling Java sources.
     *
     *
     * This property cannot be set if a [toolchain][.getToolchain] has been configured.
     *
     * @param value The value for the source compatibility
     *
     * @see .toolchain
     */
    @get:ToBeReplacedByLazyProperty
    var sourceCompatibility: JavaVersion?

    /**
     * Sets the target compatibility used for compiling Java sources.
     *
     *
     * This property cannot be set if a [toolchain][.getToolchain] has been configured.
     *
     * @param value The value for the target compatibility
     *
     * @see .toolchain
     */
    @get:ToBeReplacedByLazyProperty
    var targetCompatibility: JavaVersion?

    /**
     * Registers a feature.
     *
     *
     * The new feature will have a default capability corresponding to the
     * "group", "name" + feature name and version of this project. For example,
     * if the group of the component is "org", that the project name is "lib"
     * the version is "1.0" and the feature name is "myFeature", then a
     * capability named "org:lib-my-feature:1.0" is automatically added.
     *
     *
     * In order to consume this feature in another module add a dependency like
     * the following:
     *
     * <pre>
     * dependencies {
     * implementation(project(":lib")) {
     * capabilities {
     * requireCapability("org:lib-my-feature:1.0")
     * }
     * }
     * }
    </pre> *
     *
     * The [FeatureSpec.capability] method can be
     * used to refine the capabilities of this feature.
     *
     * @param name the name of the feature
     * @param configureAction the configuration for the feature
     *
     * @since 5.3
     */
    fun registerFeature(name: String?, configureAction: Action<in FeatureSpec?>?)

    /**
     * If this method is called, Gradle will not automatically try to fetch
     * dependencies which have a JVM version compatible with the target compatibility
     * of this module.
     * <P>
     * This should be used whenever the default behavior is not
     * applicable, in particular when for some reason it's not possible to split
     * a module and that this module only has some classes which require dependencies
     * on higher versions.
     *
     * @since 5.3
    </P> */
    fun disableAutoTargetJvm()

    /**
     * Enables generating a Javadoc artifact for the main feature of this project. If no components with a main feature are defined for this project, this has no effect.
     * <P>
     * Adds a task `javadocJar` that will package the output of the `javadoc` task in a JAR with classifier `javadoc`.
    </P> * <P>
     * The produced artifact is registered as a documentation variant on the `java` component and added as a dependency on the `assemble` task.
     * This means that if `maven-publish` or `ivy-publish` is also applied, the javadoc JAR will be published.
    </P> * <P>
     * If the project already has a task named `javadocJar` then no task is created.
    </P> * <P>
     * The publishing of the Javadoc variant can also be disabled using [org.gradle.api.component.ConfigurationVariantDetails.skip]
     * through [org.gradle.api.component.AdhocComponentWithVariants.withVariantsFromConfiguration],
     * if it should only be built locally by calling or wiring the ':javadocJar' task.
     *
     * @since 6.0
    </P> */
    fun withJavadocJar()

    /**
     * Enables generating a sources artifact for the main feature of this project. If no components with a main feature are defined for this project, this has no effect.
     * <P>
     * Adds a task `sourcesJar` that will package the Java sources of the main [SourceSet][org.gradle.api.tasks.SourceSet] in a JAR with classifier `sources`.
    </P> * <P>
     * The produced artifact is registered as a documentation variant on the `java` component and added as a dependency on the `assemble` task.
     * This means that if `maven-publish` or `ivy-publish` is also applied, the sources JAR will be published.
    </P> * <P>
     * If the project already has a task named `sourcesJar` then no task is created.
    </P> * <P>
     * The publishing of the sources variant can be disabled using [org.gradle.api.component.ConfigurationVariantDetails.skip]
     * through [org.gradle.api.component.AdhocComponentWithVariants.withVariantsFromConfiguration],
     * if it should only be built locally by calling or wiring the ':sourcesJar' task.
     *
     * @since 6.0
    </P> */
    fun withSourcesJar()

    @JvmField
    @get:Nested
    val modularity: ModularitySpec?

    @JvmField
    @get:Nested
    val toolchain: JavaToolchainSpec?

    /**
     * Configures the project wide toolchain requirements for tasks that require a tool from the toolchain (e.g. [org.gradle.api.tasks.compile.JavaCompile]).
     *
     *
     * Configuring a toolchain cannot be used together with `sourceCompatibility` or `targetCompatibility` on this extension.
     * Both values will be sourced from the toolchain.
     *
     * @since 6.7
     */
    fun toolchain(action: Action<in JavaToolchainSpec?>?): JavaToolchainSpec?

    /**
     * Configure the dependency resolution consistency for this Java project. If no components, features or source sets are added to this project, this has no effect.
     *
     *
     * The given [JavaResolutionConsistency] is used to configure consistent resolution for this project.
     *
     * @param action the configuration action
     *
     * @since 6.8
     */
    @Incubating
    fun consistentResolution(action: Action<in JavaResolutionConsistency?>?)

    /**
     * Configures the source sets of this project.
     *
     * @param closure The closure to execute.
     * @return NamedDomainObjectContainer&lt;org.gradle.api.tasks.SourceSet&gt;
     * @since 7.1
     * @see .sourceSets
     */
    fun sourceSets(closure: Closure<*>?): Any?

    /**
     * Configures the source sets of this project.
     *
     *
     * The given action is executed to configure the [SourceSetContainer].
     *
     *
     * See the example below how [org.gradle.api.tasks.SourceSet] 'main' is accessed and how the [org.gradle.api.file.SourceDirectorySet] 'java'
     * is configured to exclude some package from compilation.
     *
     * <pre class='autoTested'>
     * plugins {
     * id 'java'
     * }
     *
     * sourceSets {
     * main {
     * java {
     * exclude 'some/unwanted/package/ **'
     * }
     * }
     * }
    </pre> *
     *
     * @param action the configuration action
     * @since 9.3.0
     */
    @Incubating
    fun sourceSets(action: Action<in SourceSetContainer?>?)

    /**
     * Returns a file pointing to the root directory supposed to be used for all docs.
     * @since 7.1
     */
    @JvmField
    val docsDir: DirectoryProperty?

    /**
     * Returns a file pointing to the root directory of the test results.
     * @since 7.1
     */
    val testResultsDir: DirectoryProperty?

    /**
     * Returns a file pointing to the root directory to be used for reports.
     * @since 7.1
     */
    @JvmField
    val testReportDir: DirectoryProperty?

    /**
     * Sets the source compatibility used for compiling Java sources.
     *
     * @param value The value for the source compatibility as defined by [JavaVersion.toVersion]
     * @since 7.1
     */
    fun setSourceCompatibility(value: Any?)

    /**
     * Sets the target compatibility used for compiling Java sources.
     *
     * @param value The value for the target compatibility as defined by [JavaVersion.toVersion]
     * @since 7.1
     */
    fun setTargetCompatibility(value: Any?)

    /**
     * Creates a new instance of a [Manifest].
     * @since 7.1
     */
    fun manifest(): Manifest?

    /**
     * Creates and configures a new instance of a [Manifest]. The given closure configures
     * the new manifest instance before it is returned.
     *
     * @param closure The closure to use to configure the manifest.
     * @since 7.1
     */
    fun manifest(@DelegatesTo(Manifest::class) closure: Closure<*>?): Manifest?

    /**
     * Creates and configures a new instance of a [Manifest].
     *
     * @param action The action to use to configure the manifest.
     *
     * @since 7.1
     */
    fun manifest(action: Action<in Manifest?>?): Manifest?

    /**
     * The source sets container.
     *
     * @since 7.1
     */
    @JvmField
    val sourceSets: SourceSetContainer?

    @get:ToBeReplacedByLazyProperty
    val autoTargetJvmDisabled: Boolean
}
