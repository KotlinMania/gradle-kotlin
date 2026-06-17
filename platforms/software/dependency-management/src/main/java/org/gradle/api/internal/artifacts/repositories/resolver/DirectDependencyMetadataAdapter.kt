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
package org.gradle.api.internal.artifacts.repositories.resolver

import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.DirectDependencyMetadata
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.model.GradleDependencyMetadata
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.component.external.model.ivy.IvyDependencyDescriptor
import org.gradle.internal.component.external.model.ivy.IvyDependencyMetadata
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor
import org.gradle.internal.component.external.model.maven.MavenDependencyMetadata
import org.gradle.internal.component.model.IvyArtifactName
import java.util.stream.Collectors

class DirectDependencyMetadataAdapter(attributesFactory: AttributesFactory, metadata: ModuleDependencyMetadata) :
    AbstractDependencyMetadataAdapter<DirectDependencyMetadata>(attributesFactory, metadata), DirectDependencyMetadata {
    override fun endorseStrictVersions() {
        updateMetadata(getMetadata().withEndorseStrictVersions(true)!!)
    }

    override fun doNotEndorseStrictVersions() {
        updateMetadata(getMetadata().withEndorseStrictVersions(false)!!)
    }

    override fun isEndorsingStrictVersions(): Boolean {
        return getMetadata().isEndorsingStrictVersions
    }

    override fun getArtifactSelectors(): MutableList<DependencyArtifact> {
        return this.ivyArtifacts.stream().map<DependencyArtifact> { ivyArtifactName: IvyArtifactName? -> this.asDependencyArtifact(ivyArtifactName!!) }.collect(Collectors.toList())
    }

    private fun asDependencyArtifact(ivyArtifactName: IvyArtifactName): DependencyArtifact {
        return DefaultDependencyArtifact(ivyArtifactName.name, ivyArtifactName.type, ivyArtifactName.extension, ivyArtifactName.classifier, null)
    }

    private val ivyArtifacts: MutableList<IvyArtifactName>
        get() {
            val originalMetadata = getMetadata()
            if (originalMetadata is MavenDependencyMetadata) {
                val mavenMetadata =
                    originalMetadata
                return fromMavenDescriptor(mavenMetadata.dependencyDescriptor)
            } else if (originalMetadata is IvyDependencyMetadata) {
                val ivyMetadata = originalMetadata
                return fromIvyDescriptor(ivyMetadata.dependencyDescriptor)
            } else if (originalMetadata is GradleDependencyMetadata) {
                return fromGradleMetadata(originalMetadata)
            }
            return mutableListOf<IvyArtifactName>()
        }

    private fun fromGradleMetadata(metadata: GradleDependencyMetadata): MutableList<IvyArtifactName> {
        val artifact = metadata.dependencyArtifact
        if (artifact != null) {
            return mutableListOf<IvyArtifactName>(artifact)
        }
        return mutableListOf<IvyArtifactName>()
    }

    private fun fromIvyDescriptor(descriptor: IvyDependencyDescriptor): MutableList<IvyArtifactName> {
        val artifacts = descriptor.dependencyArtifacts
        return artifacts.stream().map<Any>(Artifact::getArtifactName).collect(Collectors.toList())
    }

    private fun fromMavenDescriptor(descriptor: MavenDependencyDescriptor): MutableList<IvyArtifactName> {
        val artifact = descriptor.dependencyArtifact
        if (artifact != null) {
            return mutableListOf<IvyArtifactName>(artifact)
        }
        return mutableListOf<IvyArtifactName>()
    }
}
