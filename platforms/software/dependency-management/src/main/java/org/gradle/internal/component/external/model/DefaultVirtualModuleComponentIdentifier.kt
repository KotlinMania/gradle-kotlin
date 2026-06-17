/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.component.external.model

import com.google.common.base.Objects
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.DisplayName

class DefaultVirtualModuleComponentIdentifier(module: ModuleIdentifier, version: String) : VirtualComponentIdentifier, ModuleComponentIdentifier, DisplayName {
    private val moduleIdentifier: ModuleIdentifier
    private val version: String
    private val hashCode: Int

    init {
        checkNotNull(module) { "module cannot be null" }
        checkNotNull(module.getGroup()) { "group cannot be null" }
        checkNotNull(module.getName()) { "name cannot be null" }
        checkNotNull(version) { "version cannot be null" }
        this.moduleIdentifier = module
        this.version = version
        // Do NOT change the order of members used in hash code here, it's been empirically
        // tested to reduce the number of collisions on a large dependency graph (performance test)
        this.hashCode = Objects.hashCode(version, module)
    }

    override fun getDisplayName(): String {
        val group = moduleIdentifier.getGroup()
        val module = moduleIdentifier.getName()
        val builder = StringBuilder(group.length + module.length + version.length + 2)
        builder.append(group)
        builder.append(":")
        builder.append(module)
        builder.append(":")
        builder.append(version)
        return builder.toString()
    }

    override fun getCapitalizedDisplayName(): String {
        return getDisplayName()
    }

    override fun getGroup(): String {
        return moduleIdentifier.getGroup()
    }

    override fun getModule(): String {
        return moduleIdentifier.getName()
    }

    override fun getVersion(): String {
        return version
    }

    override fun getModuleIdentifier(): ModuleIdentifier {
        return moduleIdentifier
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultVirtualModuleComponentIdentifier

        if (moduleIdentifier != that.moduleIdentifier) {
            return false
        }
        return version == that.version
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun toString(): String {
        return getDisplayName()
    }
}

