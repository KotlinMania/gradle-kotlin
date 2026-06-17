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

import java.io.Serializable
import java.util.stream.Collectors

class DefaultVersionCatalog(
    val name: String,
// Intentionally not part of state
    val description: String,
    private val libraries: MutableMap<String, DependencyModel>,
    private val bundles: MutableMap<String, BundleModel>,
    private val versions: MutableMap<String, VersionModel>,
    private val plugins: MutableMap<String, PluginModel>
) : Serializable {
    private val hashCode: Int

    init {
        this.hashCode = doComputeHashCode()
    }

    val libraryAliases: MutableList<String>
        get() = libraries.keys
            .stream()
            .sorted()
            .collect(Collectors.toList())

    val bundleAliases: MutableList<String>
        get() = bundles.keys
            .stream()
            .sorted()
            .collect(Collectors.toList())

    fun getDependencyData(alias: String): DependencyModel {
        return libraries.get(AliasNormalizer.normalize(alias))!!
    }

    val versionAliases: MutableList<String>
        get() = versions.keys
            .stream()
            .sorted()
            .collect(Collectors.toList())

    val pluginAliases: MutableList<String>
        get() = plugins.keys
            .stream()
            .sorted()
            .collect(Collectors.toList())

    fun getBundle(name: String): BundleModel {
        return bundles.get(AliasNormalizer.normalize(name))!!
    }

    fun getVersion(name: String): VersionModel {
        return versions.get(AliasNormalizer.normalize(name))!!
    }

    fun getPlugin(name: String): PluginModel {
        return plugins.get(AliasNormalizer.normalize(name))!!
    }

    fun hasDependency(alias: String): Boolean {
        return libraries.containsKey(AliasNormalizer.normalize(alias))
    }

    fun hasBundle(alias: String): Boolean {
        return bundles.containsKey(AliasNormalizer.normalize(alias))
    }

    fun hasVersion(alias: String): Boolean {
        return versions.containsKey(AliasNormalizer.normalize(alias))
    }

    fun hasPlugin(alias: String): Boolean {
        return plugins.containsKey(AliasNormalizer.normalize(alias))
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultVersionCatalog

        if (libraries != that.libraries) {
            return false
        }
        if (bundles != that.bundles) {
            return false
        }
        if (versions != that.versions) {
            return false
        }
        return plugins == that.plugins
    }

    override fun hashCode(): Int {
        return hashCode
    }

    private fun doComputeHashCode(): Int {
        var result = libraries.hashCode()
        result = 31 * result + bundles.hashCode()
        result = 31 * result + versions.hashCode()
        result = 31 * result + plugins.hashCode()
        return result
    }

    val isNotEmpty: Boolean
        get() = !(libraries.isEmpty() && bundles.isEmpty() && versions.isEmpty() && plugins.isEmpty())
}
