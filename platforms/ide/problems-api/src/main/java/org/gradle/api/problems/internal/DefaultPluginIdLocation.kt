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
package org.gradle.api.problems.internal

import com.google.common.base.Objects

/**
 * Represents an applied plugin ID.
 */
class DefaultPluginIdLocation(private val pluginId: String?) : PluginIdLocation {
    override fun getPluginId(): String {
        return pluginId!!
    }

    override fun equals(o: Any?): Boolean {
        if (o !is DefaultPluginIdLocation) {
            return false
        }
        val that = o
        return Objects.equal(pluginId, that.pluginId)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(pluginId)
    }
}
