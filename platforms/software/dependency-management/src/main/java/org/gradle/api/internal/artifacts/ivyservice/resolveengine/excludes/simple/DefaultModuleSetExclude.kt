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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleSetExclude
import org.gradle.internal.collect.PersistentSet
import org.gradle.internal.component.model.IvyArtifactName

internal class DefaultModuleSetExclude(private val modules: PersistentSet<String?>) : ModuleSetExclude {
    private val hashCode: Int

    init {
        this.hashCode = modules.hashCode()
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultModuleSetExclude

        return modules == that.modules
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun getModules(): PersistentSet<String?> {
        return modules
    }

    override fun excludes(module: ModuleIdentifier): Boolean {
        return modules.contains(module.getName())
    }

    override fun excludesArtifact(module: ModuleIdentifier?, artifactName: IvyArtifactName?): Boolean {
        return false
    }

    override fun mayExcludeArtifacts(): Boolean {
        return false
    }

    override fun toString(): String {
        return "{ \"module names\" : [" + ExcludeJsonHelper.toJson(modules) + "]}"
    }
}
