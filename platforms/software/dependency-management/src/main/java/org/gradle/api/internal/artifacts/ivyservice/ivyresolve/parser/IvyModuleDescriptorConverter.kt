/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableList
import com.google.common.collect.ListMultimap
import com.google.common.collect.Sets
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ExcludeRule
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ArtifactId
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.external.model.ivy.IvyDependencyDescriptor
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.component.model.IvyArtifactName
import java.lang.reflect.Field
import java.util.Arrays
import java.util.Map
import java.util.function.Function
import java.util.stream.Collectors

class IvyModuleDescriptorConverter(private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory) {
    @Suppress("deprecation")
    fun extractExtraAttributes(ivyDescriptor: ModuleDescriptor): MutableMap<NamespaceId?, String?> {
        val extraInfo = ivyDescriptor.getExtraInfo()
        return extraInfo.entries.stream().collect(
            Collectors.toMap(Function { e: MutableMap.MutableEntry<String?, String?>? -> NamespaceId.decode(e!!.key!!) }, Function { Map.Entry.value })
        )
    }

    fun extractExcludes(ivyDescriptor: ModuleDescriptor): MutableList<Exclude?> {
        val result: MutableList<Exclude?> = ArrayList<Exclude?>(ivyDescriptor.getAllExcludeRules().size)
        for (excludeRule in ivyDescriptor.getAllExcludeRules()) {
            result.add(forIvyExclude(excludeRule))
        }
        return result
    }

    fun extractDependencies(ivyDescriptor: ModuleDescriptor): MutableList<IvyDependencyDescriptor?> {
        val result: MutableList<IvyDependencyDescriptor?> = ArrayList<IvyDependencyDescriptor?>(ivyDescriptor.getDependencies().size)
        for (dependencyDescriptor in ivyDescriptor.getDependencies()) {
            addDependency(result, dependencyDescriptor)
        }
        return result
    }

    fun extractConfigurations(ivyDescriptor: ModuleDescriptor): MutableList<Configuration?> {
        val result: MutableList<Configuration?> = ArrayList<Configuration?>(ivyDescriptor.getConfigurations().size)
        for (ivyConfiguration in ivyDescriptor.getConfigurations()) {
            addConfiguration(result, ivyConfiguration)
        }
        return result
    }

    private fun addDependency(result: MutableList<IvyDependencyDescriptor?>, dependencyDescriptor: DependencyDescriptor) {
        val revisionId = dependencyDescriptor.getDependencyRevisionId()
        val requested =
            DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(revisionId.getOrganisation(), revisionId.getName()), DefaultImmutableVersionConstraint(revisionId.getRevision()))

        val configMappings: ListMultimap<String?, String?> = ArrayListMultimap.create<String?, String?>()
        for (entry in readConfigMappings(dependencyDescriptor)!!.entries) {
            configMappings.putAll(entry.key, entry.value!!)
        }

        val artifacts: MutableList<Artifact?> = ArrayList<Artifact?>()
        for (ivyArtifact in dependencyDescriptor.getAllDependencyArtifacts()) {
            val ivyArtifactName: IvyArtifactName = DefaultIvyArtifactName(ivyArtifact.getName(), ivyArtifact.getType(), ivyArtifact.getExt(), ivyArtifact.getExtraAttributes().get(CLASSIFIER))
            artifacts.add(Artifact(ivyArtifactName, Sets.newHashSet<String?>(*ivyArtifact.getConfigurations())))
        }

        val excludes: MutableList<Exclude?> = ArrayList<Exclude?>()
        for (excludeRule in dependencyDescriptor.getAllExcludeRules()) {
            excludes.add(forIvyExclude(excludeRule))
        }

        result.add(
            IvyDependencyDescriptor(
                requested,
                dependencyDescriptor.getDynamicConstraintDependencyRevisionId().getRevision(),
                dependencyDescriptor.isChanging(),
                dependencyDescriptor.isTransitive(),
                false,
                configMappings,
                artifacts,
                excludes
            )
        )
    }

    private fun forIvyExclude(excludeRule: ExcludeRule): Exclude {
        val id = excludeRule.getId()
        val artifactExclusion = artifactForIvyExclude(id)
        return DefaultExclude(
            moduleIdentifierFactory.module(id.getModuleId().getOrganisation(), id.getModuleId().getName())!!, artifactExclusion, excludeRule.getConfigurations(), excludeRule.getMatcher().getName()
        )
    }

    private fun artifactForIvyExclude(id: ArtifactId): IvyArtifactName? {
        if (PatternMatchers.ANY_EXPRESSION == id.getName()
            && PatternMatchers.ANY_EXPRESSION == id.getType()
            && PatternMatchers.ANY_EXPRESSION == id.getExt()
        ) {
            return null
        }
        return DefaultIvyArtifactName(id.getName(), id.getType(), id.getExt())
    }

    companion object {
        private const val CLASSIFIER = "classifier"
        private val DEPENDENCY_CONFIG_FIELD: Field

        init {
            try {
                DEPENDENCY_CONFIG_FIELD = DefaultDependencyDescriptor::class.java.getDeclaredField("confs")
                DEPENDENCY_CONFIG_FIELD.setAccessible(true)
            } catch (e: NoSuchFieldException) {
                throw throwAsUncheckedException(e)
            }
        }

        private fun addConfiguration(result: MutableList<Configuration?>, configuration: org.apache.ivy.core.module.descriptor.Configuration) {
            val name = configuration.getName()
            val transitive = configuration.isTransitive()
            val visible = configuration.getVisibility() == org.apache.ivy.core.module.descriptor.Configuration.Visibility.PUBLIC
            val extendsFrom: MutableList<String?> = ImmutableList.copyOf<String?>(configuration.getExtends())
            result.add(Configuration(name, transitive, visible, extendsFrom))
        }

        // TODO We should get rid of this reflection (will need to reimplement the parser to act on the metadata directly)
        private fun readConfigMappings(dependencyDescriptor: DependencyDescriptor): MutableMap<String?, MutableList<String?>?>? {
            if (dependencyDescriptor is DefaultDependencyDescriptor) {
                try {
                    return DEPENDENCY_CONFIG_FIELD.get(dependencyDescriptor) as MutableMap<String?, MutableList<String?>?>?
                } catch (e: IllegalAccessException) {
                    throw throwAsUncheckedException(e)
                }
            }

            val modConfs = dependencyDescriptor.getModuleConfigurations()
            val results: MutableMap<String?, MutableList<String?>?> = LinkedHashMap<String?, MutableList<String?>?>()
            for (modConf in modConfs) {
                results.put(modConf, Arrays.asList<String?>(*dependencyDescriptor.getDependencyConfigurations(modConfs)))
            }
            return results
        }
    }
}
