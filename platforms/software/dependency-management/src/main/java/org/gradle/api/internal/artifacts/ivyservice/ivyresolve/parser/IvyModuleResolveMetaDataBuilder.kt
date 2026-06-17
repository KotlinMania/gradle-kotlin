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

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import org.gradle.internal.component.external.model.ivy.MutableIvyModuleResolveMetadata
import org.gradle.internal.component.model.IvyArtifactName

internal class IvyModuleResolveMetaDataBuilder(
    private val ivyDescriptor: DefaultModuleDescriptor,
    private val converter: IvyModuleDescriptorConverter,
    private val metadataFactory: IvyMutableModuleMetadataFactory
) {
    val artifacts: MutableList<Artifact> = ArrayList<Artifact>()

    fun addArtifact(newArtifact: IvyArtifactName?, configurations: MutableSet<String?>) {
        require(!configurations.isEmpty()) { "Artifact should be attached to at least one configuration." }
        val artifact = findOrCreate(newArtifact)
        artifact.configurations!!.addAll(configurations)
    }

    private fun findOrCreate(artifactName: IvyArtifactName?): Artifact {
        for (existingArtifact in artifacts) {
            if (existingArtifact.artifactName == artifactName) {
                return existingArtifact
            }
        }
        val newArtifact = Artifact(artifactName)
        artifacts.add(newArtifact)
        return newArtifact
    }

    fun build(): MutableIvyModuleResolveMetadata {
        val moduleRevisionId = ivyDescriptor.getModuleRevisionId()
        val cid = newId(DefaultModuleIdentifier.newId(moduleRevisionId.getOrganisation(), moduleRevisionId.getName()), moduleRevisionId.getRevision())
        val configurations = converter.extractConfigurations(ivyDescriptor)
        val dependencies = converter.extractDependencies(ivyDescriptor)
        val excludes = converter.extractExcludes(ivyDescriptor)
        val extraAttributes = converter.extractExtraAttributes(ivyDescriptor)
        val metadata = metadataFactory.create(cid, dependencies, configurations, artifacts, excludes)
        metadata.status = ivyDescriptor.getStatus()
        metadata.extraAttributes = extraAttributes
        metadata.branch = ivyDescriptor.getModuleRevisionId().getBranch()
        return metadata
    }
}
