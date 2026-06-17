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
package org.gradle.plugin.management.internal

import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.dsl.ModuleComponentSelectorParsers
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.plugin.management.PluginRequest
import org.gradle.plugin.management.PluginResolveDetails

class DefaultPluginResolveDetails(private val pluginRequest: PluginRequestInternal) : PluginResolveDetails {
    private var targetPluginRequest: PluginRequestInternal

    init {
        this.targetPluginRequest = pluginRequest
    }

    override fun getRequested(): PluginRequest {
        return pluginRequest
    }

    override fun useModule(notation: Any) {
        targetPluginRequest = DefaultPluginRequest(
            targetPluginRequest.getId(),
            targetPluginRequest.isApply(),
            targetPluginRequest.getOrigin(),
            targetPluginRequest.getScriptDisplayName(),
            targetPluginRequest.getLineNumber(),
            targetPluginRequest.getVersion(),
            USE_MODULE_NOTATION_PARSER.parseNotation(notation),
            targetPluginRequest,
            targetPluginRequest.getAlternativeCoordinates().orElse(null)
        )
    }

    override fun useVersion(version: String?) {
        targetPluginRequest = DefaultPluginRequest(
            targetPluginRequest.getId(),
            targetPluginRequest.isApply(),
            targetPluginRequest.getOrigin(),
            targetPluginRequest.getScriptDisplayName(),
            targetPluginRequest.getLineNumber(),
            version,
            targetPluginRequest.getSelector(),
            targetPluginRequest,
            targetPluginRequest.getAlternativeCoordinates().orElse(null)
        )
    }

    override fun getTarget(): PluginRequestInternal {
        return targetPluginRequest
    }

    companion object {
        private val USE_MODULE_NOTATION_PARSER: NotationParser<Any?, ModuleComponentSelector?> = ModuleComponentSelectorParsers.parser("useModule()")
    }
}
