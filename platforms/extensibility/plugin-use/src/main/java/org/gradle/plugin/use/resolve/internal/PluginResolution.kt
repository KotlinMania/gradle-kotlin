/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.plugin.use.resolve.internal

import org.gradle.api.internal.plugins.PluginManagerInternal

/**
 * The result of attempting to resolve a plugin to a classpath.
 */
interface PluginResolution {
    /**
     * The ID of the resolved plugin.
     */
    val pluginId: PluginId?

    /**
     * Accepts a visitor and visits the resolved plugin.
     */
    fun accept(visitor: PluginResolutionVisitor?) {}

    /**
     * Apply the plugin to the provided plugin manager.
     */
    fun applyTo(pluginManager: PluginManagerInternal?)

    val pluginVersion: String?
        /**
         * The resolved plugin version, if known.
         *
         * @return The resolved plugin version, or null if the plugin version is not known.
         */
        get() = null
}
