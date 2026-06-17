/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.features.internal.binding

import org.gradle.api.Plugin
import java.util.function.Consumer

/**
 * Applies the model defaults for a given project type to a target project if the provided plugin class is a project type plugin.
 */
class DefaultModelDefaultsApplicator(private val defaultsHandlers: MutableList<ModelDefaultsHandler>) : ModelDefaultsApplicator {
    override fun applyDefaultsTo(
        target: Any,
        definition: Any,
        classLoaderContext: ModelDefaultsApplicator.ClassLoaderContext,
        plugin: Plugin<*>,
        projectFeatureImplementation: ProjectFeatureImplementation<*, *>
    ) {
        defaultsHandlers.forEach(Consumer { handler: ModelDefaultsHandler? -> handler!!.apply(target, definition, classLoaderContext, projectFeatureImplementation.getFeatureName(), plugin) })
    }
}
