/*
 * Copyright 2024 the original author or authors.
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

import com.google.common.collect.ImmutableList
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.internal.exceptions.LocationAwareException
import org.gradle.plugin.management.internal.PluginRequestInternal
import org.gradle.util.internal.TextUtil
import java.util.Formatter

/**
 * Default implementation of [PluginResolutionResult].
 */
class PluginResolutionResult(private val found: PluginResolution?, private val notFoundList: ImmutableList<NotFound>) {
    fun isFound(): Boolean {
        return found != null
    }

    /**
     * Get the resolved plugin.
     *
     * @param request The original request. Only used for error reporting.
     *
     * @throws RuntimeException if the plugin was not found.
     */
    fun getFound(request: PluginRequestInternal): PluginResolution {
        if (found != null) {
            return found
        }

        val sb = Formatter()
        sb.format("Plugin %s was not found in any of the following sources:%n", request.getDisplayName())

        for (notFound in notFoundList) {
            sb.format("%n- %s (%s)", notFound.source, notFound.message)
            if (notFound.detail != null) {
                sb.format("%n%s", TextUtil.indent(notFound.detail, "  "))
            }
        }

        val message = sb.toString()
        val exception: Exception = UnknownPluginException(message)
        throw LocationAwareException(exception, request.getScriptDisplayName(), request.getLineNumber())
    }

    val notFound: MutableList<NotFound>
        get() {
            assert(!isFound())
            return notFoundList
        }

    class NotFound private constructor(private val source: String?, private val message: String?, private val detail: String?)
    companion object {
        /**
         * Record that the plugin was not found in some source of plugins.
         */
        fun notFound(): PluginResolutionResult {
            return PluginResolutionResult(null, ImmutableList.of<NotFound?>())
        }

        /**
         * Record that the plugin was not found in some source of plugins.
         *
         * @param sourceDescription a description of the source of plugins, where the plugin requested could not be found
         * @param notFoundMessage message on why the plugin couldn't be found (e.g. it might be available by a different version)
         */
        fun notFound(sourceDescription: String?, notFoundMessage: String?): PluginResolutionResult {
            return PluginResolutionResult(null, ImmutableList.of<NotFound?>(PluginResolutionResult.NotFound(sourceDescription, notFoundMessage, null)))
        }

        /**
         * Record that the plugin was not found in some source of plugins.
         *
         * @param sourceDescription a description of the source of plugins, where the plugin requested could not be found
         * @param notFoundMessage message on why the plugin couldn't be found (e.g. it might be available by a different version)
         * @param notFoundDetail detail on how the plugin couldn't be found (e.g. searched locations)
         */
        fun notFound(sourceDescription: String?, notFoundMessage: String?, notFoundDetail: String?): PluginResolutionResult {
            return PluginResolutionResult(null, ImmutableList.of<NotFound?>(PluginResolutionResult.NotFound(sourceDescription, notFoundMessage, notFoundDetail)))
        }

        /**
         * Record that the plugin was not found in multiple sources of plugins
         */
        fun notFound(notFoundList: ImmutableList<NotFound>): PluginResolutionResult {
            return PluginResolutionResult(null, notFoundList)
        }

        /**
         * Record that the plugin was found in some source of plugins.
         *
         * @param pluginResolution the plugin resolution
         */
        fun found(pluginResolution: PluginResolution?): PluginResolutionResult {
            return PluginResolutionResult(pluginResolution, ImmutableList.of<NotFound?>())
        }
    }
}
