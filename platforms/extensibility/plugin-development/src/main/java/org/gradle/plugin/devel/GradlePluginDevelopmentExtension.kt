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
package org.gradle.plugin.devel

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.internal.tasks.DefaultSourceSetContainer
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import java.util.Arrays

/**
 * Configuration options for the [org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin].
 *
 *
 * Below is a full configuration example. Since all properties have sensible defaults,
 * typically only selected properties will be configured.
 *
 * <pre class='autoTested'>
 * plugins {
 * id 'java-gradle-plugin'
 * }
 *
 * sourceSets {
 * functionalTest
 * }
 *
 * gradlePlugin {
 * testSourceSets project.sourceSets.functionalTest
 * plugins {
 * helloPlugin {
 * id  = 'org.example.hello'
 * implementationClass = 'org.example.HelloPlugin'
 * }
 * }
 * }
</pre> *
 *
 * @see org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
 *
 * @since 2.13
 */
abstract class GradlePluginDevelopmentExtension(
    project: Project,
    /**
     * Returns the source set that compiles the code under test. Defaults to `project.sourceSets.main`.
     *
     * @return the plugin source set
     */
    @get:NotToBeReplacedByLazyProperty(because = "this property will be made non-configurable") val pluginSourceSet: SourceSet?, testSourceSet: SourceSet
) {
    private val testSourceSets: SourceSetContainer
    /**
     * Whether the plugin should automatically configure the publications for the plugins.
     * @return true if publishing should be automated, false otherwise
     */
    /**
     * Configures whether the plugin should automatically configure the publications for the plugins.
     * @param automatedPublishing whether to automated publication
     */
    @get:ToBeReplacedByLazyProperty
    var isAutomatedPublishing: Boolean = true

    init {
        this.testSourceSets = project.getObjects().newInstance<DefaultSourceSetContainer>(DefaultSourceSetContainer::class.java)
        testSourceSets(testSourceSet)
    }

    /**
     * Adds some source sets to the collection which will be using TestKit.
     *
     * Calling this method multiple times with different source sets is **additive** - this method
     * will add to the existing collection of source sets.
     *
     * @param testSourceSet the test source set to add
     * @since 7.4
     */
    @Incubating
    fun testSourceSet(testSourceSet: SourceSet) {
        this.testSourceSets.add(testSourceSet)
    }

    /**
     * Provides the source sets executing the functional tests with TestKit.
     *
     *
     * Calling this method multiple times with different source sets is **NOT** additive.  Calling this
     * method will overwrite any existing test source sets with the provided arguments.
     *
     * @param testSourceSets the test source sets
     */
    fun testSourceSets(vararg testSourceSets: SourceSet?) {
        this.testSourceSets.clear()
        this.testSourceSets.addAll(Arrays.asList<SourceSet?>(*testSourceSets))
    }


    /**
     * Returns the source sets executing the functional tests with TestKit. Defaults to `project.sourceSets.test`.
     *
     * @return the test source sets
     */
    @NotToBeReplacedByLazyProperty(because = "this property will be replaced by another API")
    fun getTestSourceSets(): MutableSet<SourceSet?> {
        return testSourceSets
    }

    /**
     * Returns the property holding the URL for the plugin's website.
     *
     * @since 7.6
     */
    abstract val website: Property<String?>?

    /**
     * Returns the property holding the URL for the plugin's VCS repository.
     *
     * @since 7.6
     */
    abstract val vcsUrl: Property<String?>?

    /**
     * Returns the declared plugins.
     *
     * @return the declared plugins, never null
     */
    abstract val plugins: NamedDomainObjectContainer<PluginDeclaration?>?

    /**
     * Configures the declared plugins.
     *
     * @param action the configuration action to invoke on the plugins
     */
    fun plugins(action: Action<in NamedDomainObjectContainer<PluginDeclaration?>?>) {
        action.execute(this.plugins)
    }
}
