/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.tasks

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.internal.instrumentation.api.annotations.NotToBeMigratedToLazy

/**
 * A `SourceSet` represents a logical group of Java source and resource files. They
 * are covered in more detail in the
 * [user manual](https://docs.gradle.org/current/userguide/building_java_projects.html#sec:java_source_sets).
 *
 *
 * The following example shows how you can configure the 'main' source set, which in this
 * case involves excluding classes whose package begins 'some.unwanted.package' from
 * compilation of the source files in the 'java' [SourceDirectorySet]:
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
 */
@NotToBeMigratedToLazy
interface SourceSet : ExtensionAware {
    /**
     * Returns the name of this source set.
     *
     * @return The name. Never returns null.
     */
    @JvmField
    val name: String?

    /**
     * Returns the classpath used to compile this source.
     *
     * @return The classpath. Never returns null.
     */
    /**
     * Sets the classpath used to compile this source.
     *
     * @param classpath The classpath. Should not be null.
     */
    @JvmField
    var compileClasspath: FileCollection?

    /**
     * Returns the classpath used to load annotation processors when compiling this source set.
     * This path is also used for annotation processor discovery. The classpath can be empty,
     * which means use the compile classpath; if you want to disable annotation processing,
     * then use `-proc:none` as a compiler argument.
     *
     * @return The annotation processor path. Never returns null.
     * @since 4.6
     */
    /**
     * Set the classpath to use to load annotation processors when compiling this source set.
     * This path is also used for annotation processor discovery. The classpath can be empty,
     * which means use the compile classpath; if you want to disable annotation processing,
     * then use `-proc:none` as a compiler argument.
     *
     * @param annotationProcessorPath The annotation processor path. Should not be null.
     * @since 4.6
     */
    @JvmField
    var annotationProcessorPath: FileCollection?

    /**
     * Returns the classpath used to execute this source.
     *
     * @return The classpath. Never returns null.
     */
    /**
     * Sets the classpath used to execute this source.
     *
     * @param classpath The classpath. Should not be null.
     */
    @JvmField
    var runtimeClasspath: FileCollection?

    /**
     * [SourceSetOutput] is a [FileCollection] of all output directories (compiled classes, processed resources, etc.)
     * and it provides means to configure the default output dirs and register additional output dirs. See examples in [SourceSetOutput]
     *
     * @return The output dirs, as a [SourceSetOutput].
     */
    @JvmField
    val output: SourceSetOutput?

    /**
     * Registers a set of tasks which are responsible for compiling this source set into the classes directory. The
     * paths are evaluated as per [org.gradle.api.Task.dependsOn].
     *
     * @param taskPaths The tasks which compile this source set.
     * @return this
     */
    fun compiledBy(vararg taskPaths: Any?): SourceSet?

    /**
     * Returns the non-Java resources which are to be copied into the resources output directory.
     *
     * @return the resources. Never returns null.
     */
    @JvmField
    val resources: SourceDirectorySet?

    /**
     * Configures the non-Java resources for this set.
     *
     *
     * The given closure is used to configure the [SourceDirectorySet] which contains the resources.
     *
     * @param configureClosure The closure to use to configure the resources.
     * @return this
     */
    fun resources(@DelegatesTo(SourceDirectorySet::class) configureClosure: Closure<*>?): SourceSet?

    /**
     * Configures the non-Java resources for this set.
     *
     *
     * The given action is used to configure the [SourceDirectorySet] which contains the resources.
     *
     * @param configureAction The action to use to configure the resources.
     * @return this
     */
    fun resources(configureAction: Action<in SourceDirectorySet?>?): SourceSet?

    /**
     * Returns the Java source which is to be compiled by the Java compiler into the class output directory.
     *
     * @return the Java source. Never returns null.
     */
    @JvmField
    val java: SourceDirectorySet?

    /**
     * Configures the Java source for this set.
     *
     *
     * The given closure is used to configure the [SourceDirectorySet] which contains the Java source.
     *
     * @param configureClosure The closure to use to configure the Java source.
     * @return this
     */
    fun java(@DelegatesTo(SourceDirectorySet::class) configureClosure: Closure<*>?): SourceSet?

    /**
     * Configures the Java source for this set.
     *
     *
     * The given action is used to configure the [SourceDirectorySet] which contains the Java source.
     *
     * @param configureAction The action to use to configure the Java source.
     * @return this
     */
    fun java(configureAction: Action<in SourceDirectorySet?>?): SourceSet?

    /**
     * All Java source files for this source set. This includes, for example, source which is directly compiled, and
     * source which is indirectly compiled through joint compilation.
     *
     * @return the Java source. Never returns null.
     */
    @JvmField
    val allJava: SourceDirectorySet?

    /**
     * All source files for this source set.
     *
     * @return the source. Never returns null.
     */
    @JvmField
    val allSource: SourceDirectorySet?

    /**
     * Returns the name of the classes task for this source set.
     *
     * @return The task name. Never returns null.
     */
    @JvmField
    val classesTaskName: String?

    /**
     * Returns the name of the resource process task for this source set.
     *
     * @return The task name. Never returns null.
     */
    @JvmField
    val processResourcesTaskName: String?

    /**
     * Returns the name of the compile Java task for this source set.
     *
     * @return The task name. Never returns null.
     */
    @JvmField
    val compileJavaTaskName: String?

    /**
     * Returns the name of a compile task for this source set.
     *
     * @param language The language to be compiled.
     * @return The task name. Never returns null.
     */
    fun getCompileTaskName(language: String?): String?

    /**
     * Returns the name of the Javadoc task for this source set.
     *
     * @return The task name. Never returns null.
     *
     * @since 6.0
     */
    @JvmField
    val javadocTaskName: String?

    /**
     * Returns the name of the Jar task for this source set.
     *
     * @return The task name. Never returns null.
     */
    @JvmField
    val jarTaskName: String?

    /**
     * Returns the name of the Javadoc Jar task for this source set.
     *
     * @return The task name. Never returns null.
     *
     * @since 6.0
     */
    @JvmField
    val javadocJarTaskName: String?

    /**
     * Returns the name of the Source Jar task for this source set.
     *
     * @return The task name. Never returns null.
     *
     * @since 6.0
     */
    @JvmField
    val sourcesJarTaskName: String?

    /**
     * Returns the name of a task for this source set.
     *
     * @param verb The action, may be null.
     * @param target The target, may be null
     * @return The task name, generally of the form ${verb}${name}${target}
     */
    fun getTaskName(verb: String?, target: String?): String?

    /**
     * Returns the name of the compile only configuration for this source set.
     *
     * @return The compile only configuration name
     *
     * @since 2.12
     */
    @JvmField
    val compileOnlyConfigurationName: String?

    /**
     * Returns the name of the 'compile only api' configuration for this source set.
     *
     * @return The 'compile only api' configuration name
     *
     * @since 6.7
     */
    val compileOnlyApiConfigurationName: String?

    /**
     * Returns the name of the compile classpath configuration for this source set.
     *
     * @return The compile classpath configuration
     *
     * @since 2.12
     */
    @JvmField
    val compileClasspathConfigurationName: String?

    /**
     * Returns the name of the configuration containing annotation processors and their
     * dependencies needed to compile this source set.
     *
     * @return the name of the annotation processor configuration.
     * @since 4.6
     */
    @JvmField
    val annotationProcessorConfigurationName: String?

    /**
     * Returns the name of the API configuration for this source set. The API configuration
     * contains dependencies which are exported by this source set, and is not transitive
     * by default. This configuration is not meant to be resolved and should only contain
     * dependencies that are required when compiling against this component.
     *
     * @return The API configuration name
     *
     * @since 3.3
     */
    val apiConfigurationName: String?

    /**
     * Returns the name of the implementation configuration for this source set. The implementation
     * configuration should contain dependencies which are specific to the implementation of the component
     * (internal APIs).
     *
     * @return The configuration name
     * @since 3.4
     */
    @JvmField
    val implementationConfigurationName: String?

    /**
     * Returns the name of the configuration that should be used when compiling against the API
     * of this component. This configuration is meant to be consumed by other components when
     * they need to compile against it.
     *
     * @return The API compile configuration name
     *
     * @since 3.3
     */
    val apiElementsConfigurationName: String?

    /**
     * Returns the name of the configuration that contains dependencies that are only required
     * at runtime of the component. Dependencies found in this configuration are visible to
     * the runtime classpath of the component, but not to consumers.
     *
     * @return the runtime only configuration name
     * @since 3.4
     */
    @JvmField
    val runtimeOnlyConfigurationName: String?

    /**
     * Returns the name of the runtime classpath configuration of this component: the runtime
     * classpath contains elements of the implementation, as well as runtime only elements.
     *
     * @return the name of the runtime classpath configuration
     * @since 3.4
     */
    @JvmField
    val runtimeClasspathConfigurationName: String?

    /**
     * Returns the name of the configuration containing elements that are strictly required
     * at runtime. Consumers of this configuration will get all the mandatory elements for
     * this component to execute at runtime.
     *
     * @return the name of the runtime elements configuration.
     * @since 3.4
     */
    val runtimeElementsConfigurationName: String?

    /**
     * Returns the name of the configuration that represents the variant that carries the
     * Javadoc for this source set in packaged form. Used to publish a variant with a '-javadoc' zip.
     *
     * @return the name of the javadoc elements configuration.
     * @since 6.0
     */
    @JvmField
    val javadocElementsConfigurationName: String?

    /**
     * Returns the name of the configuration that represents the variant that carries the
     * original source code in packaged form. Used to publish a variant with a '-sources' zip.
     *
     * @return the name of the sources elements configuration.
     * @since 6.0
     */
    @JvmField
    val sourcesElementsConfigurationName: String?

    companion object {
        /**
         * Determines if this source set is the main source set
         *
         * @since 6.7
         */
        @JvmStatic
        fun isMain(sourceSet: SourceSet): Boolean {
            return MAIN_SOURCE_SET_NAME == sourceSet.name
        }

        /**
         * The name of the main source set.
         */
        const val MAIN_SOURCE_SET_NAME: String = "main"

        /**
         * The name of the test source set.
         */
        const val TEST_SOURCE_SET_NAME: String = "test"
    }
}
