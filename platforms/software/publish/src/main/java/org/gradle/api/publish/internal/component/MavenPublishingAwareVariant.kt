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
package org.gradle.api.publish.internal.component

import org.gradle.api.component.SoftwareComponentVariant

interface MavenPublishingAwareVariant : SoftwareComponentVariant {
    val scopeMapping: ScopeMapping

    // Order is important!
    enum class ScopeMapping(val scope: String, val isOptional: Boolean) {
        compile("compile", false),
        runtime("runtime", false),
        compile_optional("compile", true),
        runtime_optional("runtime", true);

        companion object {
            fun of(scope: String, optional: Boolean): ScopeMapping {
                var scope = scope
                if (optional) {
                    scope += "_optional"
                }
                return valueOf(scope)
            }
        }
    }

    companion object {
        fun scopeForVariant(variant: SoftwareComponentVariant): ScopeMapping {
            if (variant is MavenPublishingAwareVariant) {
                return variant.scopeMapping
            }
            // TODO: Update native plugins to use maven-aware variants so we can remove this.
            val name = variant.getName()
            if ("api" == name || "apiElements" == name) {
                return ScopeMapping.compile
            }
            return ScopeMapping.runtime
        }
    }
}
