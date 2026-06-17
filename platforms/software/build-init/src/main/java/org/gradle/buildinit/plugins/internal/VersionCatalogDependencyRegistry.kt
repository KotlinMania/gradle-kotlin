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
package org.gradle.buildinit.plugins.internal

import com.google.common.collect.Sets
import org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder
import org.jspecify.annotations.NullMarked
import java.lang.String
import java.util.TreeMap
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.Boolean

/**
 * Tracks plugins, libraries and their versions used during build generation.
 */
@NullMarked
class VersionCatalogDependencyRegistry(private val fullyQualifiedAliases: Boolean) {
    private val versions: MutableMap<String, VersionEntry> = TreeMap<String, VersionEntry>()
    private val libraries: MutableMap<String, LibraryEntry> = TreeMap<String, LibraryEntry>()
    private val plugins: MutableMap<String, PluginEntry> = TreeMap<String, PluginEntry>()

    fun getVersions(): MutableCollection<VersionEntry> {
        return versions.values
    }

    fun getLibraries(): MutableCollection<LibraryEntry> {
        return libraries.values
    }

    fun getPlugins(): MutableCollection<PluginEntry> {
        return plugins.values
    }

    fun registerLibrary(module: String, version: String): String {
        val alias: String = if (fullyQualifiedAliases) coordinatesToAlias(module) else moduleToAlias(module)
        val versionEntry = findOrCreateVersionEntry(alias, module, version)
        val libraryEntry = findOrCreateLibraryEntry(alias, module, versionEntry)
        return "libs." + libraryEntry.alias!!.replace("-".toRegex(), ".")
    }

    fun registerPlugin(pluginId: String, version: String, pluginAlias: String?): String {
        val alias = pluginAliasOf(pluginId, pluginAlias)
        val pluginEntry = findOrCreatePluginEntry(alias, pluginId, version)
        return "libs.plugins." + pluginEntry.alias!!.replace("-".toRegex(), ".")
    }

    private fun pluginAliasOf(pluginId: String, pluginAlias: String?): String {
        if (fullyQualifiedAliases) {
            return coordinatesToAlias(pluginId)
        }

        return if (pluginAlias != null) pluginAlias else pluginIdToAlias(pluginId)
    }

    private fun findOrCreateVersionEntry(alias: String, module: String, version: String): VersionEntry {
        for (v in versions.values) {
            if (v.module == module && v.version == version) {
                return v
            }
        }
        val v = VersionEntry()
        v.alias = findFreeAlias(versions.keys, alias)
        v.module = module
        v.version = version
        versions.put(v.alias!!, v)
        return v
    }

    private fun findOrCreateLibraryEntry(alias: String, module: String, versionEntry: VersionEntry): LibraryEntry {
        var alias = alias
        for (l in libraries.values) {
            if (l.module == module && l.version == versionEntry.version) {
                return l
            }
        }
        val l = LibraryEntry()
        if (RESERVED_LIBRARY_PREFIX.matcher(alias).find()) {
            alias = "my" + alias
        }
        l.alias = findFreeAlias(libraries.keys, alias)
        l.module = module
        l.version = versionEntry.version
        l.versionRef = versionEntry.alias
        libraries.put(l.alias!!, l)
        return l
    }

    private fun findOrCreatePluginEntry(alias: String, pluginId: String, version: String): PluginEntry {
        for (p in plugins.values) {
            if (p.pluginId == pluginId && p.version == version) {
                return p
            }
        }
        val p = PluginEntry()
        p.alias = findFreeAlias(plugins.keys, alias)
        p.pluginId = pluginId
        p.version = version
        plugins.put(p.alias!!, p)
        return p
    }

    @NullMarked
    class VersionEntry {
        var alias: String? = null
        var module: String? = null
        var version: String? = null
    }

    @NullMarked
    class LibraryEntry {
        var alias: String? = null
        var module: String? = null
        var version: String? = null
        var versionRef: String? = null
    }

    @NullMarked
    class PluginEntry {
        var alias: String? = null
        var pluginId: String? = null
        var version: String? = null
    }

    companion object {
        private val RESERVED_LIBRARY_PREFIX: Pattern = Pattern.compile("^(" + String.join("|", DefaultVersionCatalogBuilder.FORBIDDEN_LIBRARY_ALIAS_PREFIX) + ")[- ]")
        private val RESERVED_ALIAS_COMPONENT: Pattern =
            Pattern.compile("(^|-)(" + String.join("|", Sets.union<kotlin.String>(DefaultVersionCatalogBuilder.RESERVED_ALIAS_NAMES, DefaultVersionCatalogBuilder.RESERVED_JAVA_NAMES)) + ")($|[- ])")

        private fun pluginIdToAlias(pluginId: kotlin.String): kotlin.String {
            val pluginIdComponents = pluginId.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val pluginIdLastComponent = pluginIdComponents[pluginIdComponents.size - 1]
            return coordinatesToAlias(pluginIdLastComponent)
        }

        private fun moduleToAlias(module: kotlin.String): kotlin.String {
            return coordinatesToAlias(module.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1])
        }

        private fun coordinatesToAlias(coordinates: kotlin.String): kotlin.String {
            // not required but Groovy and Kotlin slightly differ in the handling of uppercase letters of alias parts so make everything lowercase to avoid lookup failures
            val alias = coordinates.replace("[.:_]".toRegex(), "-").replace("-(\\d)".toRegex(), "-v$1").lowercase()
            val resultingAlias = StringBuffer()
            val reservedComponentsMatcher: Matcher = RESERVED_ALIAS_COMPONENT.matcher(alias)
            while (reservedComponentsMatcher.find()) {
                reservedComponentsMatcher.appendReplacement(resultingAlias, "$1my" + reservedComponentsMatcher.group(2) + "$3")
            }
            reservedComponentsMatcher.appendTail(resultingAlias)
            return resultingAlias.toString()
        }

        private fun findFreeAlias(reservedKeys: MutableSet<kotlin.String>, key: kotlin.String): kotlin.String {
            var nextKey = key
            var collisionCount = 0
            while (reservedKeys.contains(nextKey)) {
                collisionCount += 1
                nextKey = key + "-x" + collisionCount
            }
            return nextKey
        }
    }
}
