/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeAnyOf
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.internal.collect.PersistentSet
import org.gradle.internal.component.model.IvyArtifactName

internal class DefaultExcludeAnyOf private constructor(components: PersistentSet<ExcludeSpec?>?) : DefaultCompositeExclude(components), ExcludeAnyOf {
    override fun mask(): Int {
        return 1731217984
    }

    private var mayExcludeArtifacts: Boolean? = null

    override fun getDisplayName(): String {
        return "any of"
    }

    override fun excludes(module: ModuleIdentifier?): Boolean {
        for (component in getComponents()) {
            if (component.excludes(module)) {
                return true
            }
        }
        return false
    }

    override fun excludesArtifact(module: ModuleIdentifier?, artifactName: IvyArtifactName?): Boolean {
        for (component in getComponents()) {
            if (component.excludesArtifact(module, artifactName)) {
                return true
            }
        }
        return false
    }

    override fun mayExcludeArtifacts(): Boolean {
        if (mayExcludeArtifacts != null) {
            return mayExcludeArtifacts!!
        }
        mayExcludeArtifacts = false
        for (component in getComponents()) {
            if (component.mayExcludeArtifacts()) {
                mayExcludeArtifacts = true
                break
            }
        }
        return mayExcludeArtifacts!!
    }

    companion object {
        fun of(components: PersistentSet<ExcludeSpec?>?): ExcludeSpec {
            return DefaultExcludeAnyOf(components)
        }
    }
}
