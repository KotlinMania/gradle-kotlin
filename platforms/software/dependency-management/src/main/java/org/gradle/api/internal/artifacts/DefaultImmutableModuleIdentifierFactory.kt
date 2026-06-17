/*
 * Copyright 2017 the original author or authors.
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
import java.util.concurrent.ConcurrentHashMap

class DefaultImmutableModuleIdentifierFactory : ImmutableModuleIdentifierFactory {
    private val groupIdToModules: MutableMap<String?, MutableMap<String?, ModuleIdentifier?>?> = ConcurrentHashMap<String?, MutableMap<String?, ModuleIdentifier?>?>()
    private val idToVersions: MutableMap<ModuleIdentifier?, MutableMap<String?, ModuleVersionIdentifier?>?> = ConcurrentHashMap<ModuleIdentifier?, MutableMap<String?, ModuleVersionIdentifier?>?>()

    override fun module(group: String?, name: String?): ModuleIdentifier? {
        var byName = groupIdToModules.get(group)
        if (byName == null) {
            byName = groupIdToModules.computeIfAbsent(group) { k: String? -> ConcurrentHashMap<String?, ModuleIdentifier?>() }
        }
        var moduleIdentifier = byName!!.get(name)
        if (moduleIdentifier == null) {
            moduleIdentifier = DefaultModuleIdentifier.Companion.newId(group, name)
            byName.put(name, moduleIdentifier)
        }
        return moduleIdentifier
    }

    override fun moduleWithVersion(group: String?, name: String?, version: String?): ModuleVersionIdentifier? {
        val mi = module(group, name)
        return moduleWithVersion(mi, version)
    }

    override fun moduleWithVersion(mi: ModuleIdentifier?, version: String?): ModuleVersionIdentifier? {
        var byVersion = idToVersions.get(mi)
        if (byVersion == null) {
            byVersion = idToVersions.computeIfAbsent(mi) { k: ModuleIdentifier? -> ConcurrentHashMap<String?, ModuleVersionIdentifier?>() }
        }
        var identifier = byVersion!!.get(version)
        if (identifier == null) {
            identifier = DefaultModuleVersionIdentifier.Companion.newId(mi, version)
            byVersion.put(version, identifier)
        }
        return identifier
    }
}
