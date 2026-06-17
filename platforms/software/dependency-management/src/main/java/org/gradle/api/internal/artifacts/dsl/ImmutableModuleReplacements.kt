/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl

import com.google.common.collect.ImmutableMap
import org.gradle.api.artifacts.ModuleIdentifier

/**
 * Immutable user-configured description of which modules replace other
 * modules in a dependency graph.
 *
 * @see DependencyHandler.getModules
 */
class ImmutableModuleReplacements(private val replacements: ImmutableMap<ModuleIdentifier, Replacement>) {
    fun getReplacementFor(sourceModule: ModuleIdentifier): Replacement? {
        return replacements.get(sourceModule)
    }

    class Replacement internal constructor(val target: ModuleIdentifier, val reason: String?)
}
