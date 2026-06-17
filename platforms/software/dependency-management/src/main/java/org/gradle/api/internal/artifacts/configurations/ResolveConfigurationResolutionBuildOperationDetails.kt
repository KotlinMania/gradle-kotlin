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
package org.gradle.api.internal.artifacts.configurations

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.internal.artifacts.repositories.descriptor.RepositoryDescriptor
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization
import org.gradle.util.internal.CollectionUtils.collect
import java.io.File
import java.net.URI
import java.util.function.Function

internal class ResolveConfigurationResolutionBuildOperationDetails(
    private val configurationName: String,
    private val isScriptConfiguration: Boolean,
    private val configurationDescription: String?,
    private val buildPath: String,
    private val projectPath: String?,
    private val isConfigurationVisible: Boolean,
    private val isConfigurationTransitive: Boolean,
    repositories: MutableList<ResolutionAwareRepository>
) : ResolveConfigurationDependenciesBuildOperationType.Details, CustomOperationTraceSerialization {
    private val repositories: MutableList<ResolveConfigurationDependenciesBuildOperationType.Repository>

    init {
        this.repositories = RepositoryImpl.Companion.transform(repositories)
    }

    override fun getConfigurationName(): String {
        return configurationName
    }

    override fun getProjectPath(): String? {
        return projectPath
    }

    override fun isScriptConfiguration(): Boolean {
        return isScriptConfiguration
    }

    override fun getConfigurationDescription(): String {
        return configurationDescription!!
    }

    override fun getBuildPath(): String {
        return buildPath
    }

    override fun isConfigurationVisible(): Boolean {
        return isConfigurationVisible
    }

    override fun isConfigurationTransitive(): Boolean {
        return isConfigurationTransitive
    }

    override fun getRepositories(): MutableList<ResolveConfigurationDependenciesBuildOperationType.Repository> {
        return repositories
    }

    override fun getCustomOperationTraceSerializableModel(): Any {
        val model: MutableMap<String, Any> = HashMap<String, Any>()
        model.put("configurationName", configurationName)
        model.put("scriptConfiguration", isScriptConfiguration)
        model.put("configurationDescription", configurationDescription!!)
        model.put("buildPath", buildPath)
        model.put("projectPath", projectPath!!)
        model.put("configurationVisible", isConfigurationVisible)
        model.put("configurationTransitive", isConfigurationTransitive)
        val repoBuilder = ImmutableList.Builder<Any>()
        for (repository in repositories) {
            val repoMapBuilder = ImmutableMap.Builder<String, Any>()
            repoMapBuilder.put("id", repository.getId())
            repoMapBuilder.put("name", repository.getName())
            repoMapBuilder.put("type", repository.getType())
            val propertiesMapBuilder = ImmutableMap.Builder<String, Any>()
            for (property in repository.getProperties().entries) {
                val propertyValue: Any
                if (property.value is MutableCollection<*>) {
                    val listBuilder = ImmutableList.Builder<Any>()
                    for (inner in property.value as MutableCollection<*>) {
                        doSerialize(inner!!, listBuilder)
                    }
                    propertyValue = listBuilder.build()
                } else if (property.value is File) {
                    propertyValue = (property.value as File).getAbsolutePath()
                } else if (property.value is URI) {
                    propertyValue = (property.value as URI).toASCIIString()
                } else {
                    propertyValue = property.value!!
                }

                propertiesMapBuilder.put(property.key, propertyValue)
            }
            repoMapBuilder.put("properties", propertiesMapBuilder.build())
            repoBuilder.add(repoMapBuilder.build())
        }
        model.put("repositories", repoBuilder.build())
        return model
    }

    private fun doSerialize(value: Any, listBuilder: ImmutableList.Builder<Any>) {
        if (value is File) {
            listBuilder.add(value.getAbsolutePath())
        } else if (value is URI) {
            listBuilder.add(value.toASCIIString())
        } else {
            listBuilder.add(value)
        }
    }

    private class RepositoryImpl(private val descriptor: RepositoryDescriptor) : ResolveConfigurationDependenciesBuildOperationType.Repository {
        override fun getId(): String {
            return descriptor.name!!
        }

        override fun getType(): String {
            return descriptor.type.name()
        }

        override fun getName(): String {
            return descriptor.name!!
        }

        override fun getProperties(): MutableMap<String, *> {
            return descriptor.properties
        }

        companion object {
            private fun transform(repositories: MutableList<ResolutionAwareRepository>): MutableList<ResolveConfigurationDependenciesBuildOperationType.Repository> {
                return collect<ResolveConfigurationDependenciesBuildOperationType.Repository?, ResolutionAwareRepository?>(
                    repositories,
                    Function { repository: ResolutionAwareRepository? -> RepositoryImpl(repository!!.descriptor) })
            }
        }
    }
}
