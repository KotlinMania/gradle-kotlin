/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.artifacts.dependencies

import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.plugin.use.PluginDependency

class DefaultPluginDependency(private val pluginId: String, private val versionConstraint: MutableVersionConstraint) : PluginDependency {
    override fun getPluginId(): String {
        return pluginId
    }

    override fun getVersion(): VersionConstraint {
        return versionConstraint
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultPluginDependency

        if (pluginId != that.pluginId) {
            return false
        }
        return versionConstraint == that.versionConstraint
    }

    override fun hashCode(): Int {
        var result = pluginId.hashCode()
        result = 31 * result + versionConstraint.hashCode()
        return result
    }

    override fun toString(): String {
        val versionConstraintAsString = versionConstraint.toString()
        return if (versionConstraintAsString.isEmpty())
            pluginId
        else
            pluginId + ":" + versionConstraintAsString
    }
}
