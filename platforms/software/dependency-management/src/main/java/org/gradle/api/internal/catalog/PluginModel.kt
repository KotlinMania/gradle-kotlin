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
package org.gradle.api.internal.catalog

import org.gradle.api.internal.artifacts.ImmutableVersionConstraint

class PluginModel(
    val id: String,
    versionRef: String?,
    version: ImmutableVersionConstraint,
    context: String?
) : AbstractContextAwareModel(context) {
    private val version: ImmutableVersionConstraint?
    val versionRef: String?
    private val hashCode: Int

    init {
        this.version = version
        this.versionRef = versionRef
        this.hashCode = doComputeHashCode()
    }

    fun getVersion(): ImmutableVersionConstraint {
        return version!!
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as PluginModel

        if (id != that.id) {
            return false
        }
        if (if (version != null) (version != that.version) else that.version != null) {
            return false
        }
        return if (versionRef != null) (versionRef == that.versionRef) else that.versionRef == null
    }

    override fun hashCode(): Int {
        return hashCode
    }

    private fun doComputeHashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (if (version != null) version.hashCode() else 0)
        result = 31 * result + (if (versionRef != null) versionRef.hashCode() else 0)
        return result
    }

    override fun toString(): String {
        return "plugin {" +
                "id='" + id + '\'' +
                ", version='" + version + '\'' +
                '}'
    }
}
