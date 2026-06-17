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

import org.gradle.api.Named
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty

/**
 * Describes a Gradle plugin under development.
 *
 * @see org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
 *
 * @since 2.14
 */
abstract class PluginDeclaration(private val name: String?) : Named {
    @get:ToBeReplacedByLazyProperty
    var id: String? = null

    @get:ToBeReplacedByLazyProperty
    var implementationClass: String? = null
    /**
     * Returns the display name for this plugin declaration.
     *
     *
     * The display name is used when publishing this plugin to repositories
     * that support human-readable artifact names.
     *
     * @since 4.10
     */
    /**
     * Sets the display name for this plugin declaration.
     *
     *
     * The display name is used when publishing this plugin to repositories
     * that support human-readable artifact names.
     *
     * @since 4.10
     */
    @get:ToBeReplacedByLazyProperty
    var displayName: String? = null
    /**
     * Returns the description for this plugin declaration.
     *
     *
     * The description is used when publishing this plugin to repositories
     * that support providing descriptions for artifacts.
     *
     * @since 4.10
     */
    /**
     * Sets the description for this plugin declaration.
     *
     *
     * The description is used when publishing this plugin to repositories
     * that support providing descriptions for artifacts.
     *
     * @since 4.10
     */
    @get:ToBeReplacedByLazyProperty
    var description: String? = null

    @NotToBeReplacedByLazyProperty(because = "Final property from Named interface")
    override fun getName(): String? {
        return name
    }

    /**
     * Returns the tags property for this plugin declaration.
     *
     *
     * Tags are used when publishing this plugin to repositories that support tagging plugins,
     * for example the [Gradle Plugin Portal](http://plugins.gradle.org).
     *
     * @since 7.6
     */
    abstract val tags: SetProperty<String?>?
}
