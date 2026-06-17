/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

class DefaultModuleVersionIdentifier : ModuleVersionIdentifier {
    private val id: ModuleIdentifier
    private val version: String
    private val hashCode: Int

    private constructor(group: String, name: String, version: String) {
        checkNotNull(group) { "group cannot be null" }
        checkNotNull(name) { "name cannot be null" }
        checkNotNull(version) { "version cannot be null" }
        this.id = DefaultModuleIdentifier.Companion.newId(group, name)
        this.version = version
        this.hashCode = 31 * id.hashCode() xor version.hashCode()
    }

    private constructor(id: ModuleIdentifier, version: String) {
        checkNotNull(version) { "version cannot be null" }
        this.id = id
        this.version = version
        // pre-compute the hashcode as it's going to be used anyway, and this object
        // is used as a key in several hash maps
        this.hashCode = 31 * id.hashCode() xor version.hashCode()
    }

    override fun getGroup(): String {
        return id.getGroup()
    }

    override fun getName(): String {
        return id.getName()
    }

    override fun getVersion(): String {
        return version
    }

    override fun toString(): String {
        val group = id.getGroup()
        val module = id.getName()
        return group + ":" + module + ":" + version
    }

    override fun equals(obj: Any?): Boolean {
        if (obj === this) {
            return true
        }
        if (obj == null || obj.javaClass != javaClass) {
            return false
        }
        val other = obj as DefaultModuleVersionIdentifier
        if (id != other.id) {
            return false
        }
        return version == other.version
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun getModule(): ModuleIdentifier {
        return id
    }

    companion object {
        fun newId(module: Module): ModuleVersionIdentifier {
            return DefaultModuleVersionIdentifier(module.group!!, module.name!!, module.version!!)
        }

        fun newId(id: ModuleIdentifier, version: String): ModuleVersionIdentifier {
            return DefaultModuleVersionIdentifier(id, version)
        }

        fun newId(group: String, name: String, version: String): ModuleVersionIdentifier {
            return DefaultModuleVersionIdentifier(group, name, version)
        }

        fun newId(componentId: ModuleComponentIdentifier): ModuleVersionIdentifier {
            return DefaultModuleVersionIdentifier(componentId.getGroup(), componentId.getModule(), componentId.getVersion())
        }
    }
}
