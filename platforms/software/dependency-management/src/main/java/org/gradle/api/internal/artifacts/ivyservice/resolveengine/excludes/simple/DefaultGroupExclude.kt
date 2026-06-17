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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupExclude
import org.gradle.internal.component.model.IvyArtifactName

internal class DefaultGroupExclude private constructor(private val group: String) : GroupExclude {
    private val hashCode: Int

    init {
        this.hashCode = group.hashCode()
    }

    override fun getGroup(): String {
        return group
    }

    override fun excludes(module: ModuleIdentifier): Boolean {
        return group == module.getGroup()
    }

    override fun excludesArtifact(module: ModuleIdentifier?, artifactName: IvyArtifactName?): Boolean {
        return false
    }

    override fun mayExcludeArtifacts(): Boolean {
        return false
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultGroupExclude

        if (hashCode != that.hashCode) {
            return false
        }
        return group == that.group
    }

    override fun hashCode(): Int {
        return group.hashCode()
    }

    override fun toString(): String {
        return "{\"exclude group\" : \"" + group + "\"}"
    }

    companion object {
        fun of(group: String): GroupExclude {
            return DefaultGroupExclude(group)
        }
    }
}
