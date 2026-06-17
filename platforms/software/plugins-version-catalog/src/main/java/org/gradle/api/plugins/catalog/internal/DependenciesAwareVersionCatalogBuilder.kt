/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins.catalog.internal

import com.google.common.collect.Interner
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyConstraintSet
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint
import org.gradle.api.internal.catalog.DefaultVersionCatalog
import org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder
import org.gradle.api.internal.catalog.parser.DependenciesModelHelper
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.model.ObjectFactory
import java.util.function.Supplier
import javax.inject.Inject

abstract class DependenciesAwareVersionCatalogBuilder @Inject constructor(
    name: String,
    strings: Interner<String>,
    versionConstraintInterner: Interner<ImmutableVersionConstraint>,
    objects: ObjectFactory,
    dependencyResolutionServicesSupplier: Supplier<DependencyResolutionServices>,
    private val dependenciesConfiguration: Configuration
) : DefaultVersionCatalogBuilder(name, strings, versionConstraintInterner, objects, dependencyResolutionServicesSupplier) {
    private val explicitAliases: MutableMap<ModuleIdentifier, String> = HashMap<ModuleIdentifier, String>()
    private var shouldAmendModel = true

    public override fun build(): DefaultVersionCatalog {
        if (shouldAmendModel) {
            val allDependencies = dependenciesConfiguration.getAllDependencies()
            val allDependencyConstraints = dependenciesConfiguration.getAllDependencyConstraints()
            val seen: MutableSet<ModuleIdentifier> = HashSet<ModuleIdentifier>()
            collectDependencies(allDependencies, seen)
            collectConstraints(allDependencyConstraints, seen)
        }
        shouldAmendModel = false
        return super.build()
    }

    fun tryGenericAlias(group: String, name: String, versionSpec: Action<in MutableVersionConstraint>) {
        val alias: String = normalizeName(name)
        if (containsLibraryAlias(alias)) {
            throw InvalidUserDataException("A dependency with alias '" + alias + "' already exists for module '" + group + ":" + name + "'. Please configure an explicit alias for this dependency.")
        }
        if (!DependenciesModelHelper.ALIAS_PATTERN.matcher(alias).matches()) {
            throw InvalidUserDataException("Unable to generate an automatic alias for '" + group + ":" + name + "'. Please configure an explicit alias for this dependency.")
        }
        library(alias, group, name).version(versionSpec)
    }

    private fun collectDependencies(allDependencies: DependencySet, seen: MutableSet<ModuleIdentifier>) {
        for (dependency in allDependencies) {
            val group = dependency.getGroup()
            val name = dependency.getName()
            if (group != null) {
                val id: ModuleIdentifier? = DefaultModuleIdentifier.newId(group, name)
                if (seen.add(id!!)) {
                    val alias = explicitAliases.get(id)
                    if (alias != null) {
                        library(alias, group, name).version(Action { v: MutableVersionConstraint -> copyDependencyVersion(dependency, group, name, v) })
                    } else {
                        tryGenericAlias(group, name, Action { v: MutableVersionConstraint -> copyDependencyVersion(dependency, group, name, v) })
                    }
                } else {
                    LOGGER.warn("Duplicate entry for dependency " + group + ":" + name)
                }
            }
        }
    }

    private fun collectConstraints(allConstraints: DependencyConstraintSet, seen: MutableSet<ModuleIdentifier>) {
        for (constraint in allConstraints) {
            val group = constraint.getGroup()
            val name = constraint.getName()
            val id: ModuleIdentifier? = DefaultModuleIdentifier.newId(group, name)
            if (seen.add(id!!)) {
                val alias = explicitAliases.get(id)
                if (alias != null) {
                    library(alias, group, name).version(Action { into: MutableVersionConstraint -> copyConstraint(constraint.getVersionConstraint(), into) })
                } else {
                    tryGenericAlias(group, name, Action { into: MutableVersionConstraint -> copyConstraint(constraint.getVersionConstraint(), into) })
                }
            } else {
                LOGGER.warn("Duplicate entry for constraint " + group + ":" + name)
            }
        }
    }

    fun configureExplicitAlias(id: ModuleIdentifier, alias: String) {
        explicitAliases.put(id, alias)
    }

    companion object {
        private val LOGGER: Logger = getLogger(DependenciesAwareVersionCatalogBuilder::class.java)!!

        private fun normalizeName(name: String): String {
            return name.replace('.', '-')
        }

        private fun copyDependencyVersion(dependency: Dependency, group: String, name: String, v: MutableVersionConstraint) {
            if (dependency is ExternalModuleDependency) {
                val vc = dependency.getVersionConstraint()
                copyConstraint(vc, v)
            } else {
                val version = dependency.getVersion()
                if (version == null || version.isEmpty()) {
                    throw InvalidUserDataException("Version for dependency " + group + ":" + name + " must not be empty")
                }
                v.require(version)
            }
        }

        private fun copyConstraint(from: VersionConstraint, into: MutableVersionConstraint) {
            if (!from.getRequiredVersion().isEmpty()) {
                into.require(from.getRequiredVersion())
            }
            if (!from.getStrictVersion().isEmpty()) {
                into.strictly(from.getStrictVersion())
            }
            if (!from.getPreferredVersion().isEmpty()) {
                into.prefer(from.getPreferredVersion())
            }
            if (!from.getRejectedVersions().isEmpty()) {
                into.reject(*from.getRejectedVersions().toTypedArray<String>())
            }
        }
    }
}
