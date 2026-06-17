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
package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.api.artifacts.maven.PomModuleDescriptor
import org.gradle.api.internal.artifacts.DefaultPomModuleDescriptor
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.ivy.IvyModuleResolveMetadata
import org.gradle.internal.component.external.model.maven.MavenModuleResolveMetadata

internal class MetadataDescriptorFactory(private val metadata: ModuleComponentResolveMetadata) {
    fun <T> createDescriptor(descriptorClass: Class<T?>): T? {
        if (isIvyMetadata(descriptorClass, metadata)) {
            val ivyMetadata = metadata as IvyModuleResolveMetadata
            val descriptor: IvyModuleDescriptor = DefaultIvyModuleDescriptor(ivyMetadata.extraAttributes, ivyMetadata.branch, ivyMetadata.status!!)
            return descriptorClass.cast(descriptor)
        } else if (isPomMetadata(descriptorClass, metadata)) {
            val mavenMetadata = metadata as MavenModuleResolveMetadata
            val descriptor: PomModuleDescriptor = DefaultPomModuleDescriptor(mavenMetadata)
            return descriptorClass.cast(descriptor)
        }
        return null
    }

    companion object {
        fun isMatchingMetadata(descriptor: Class<*>, metadata: ModuleComponentResolveMetadata): Boolean {
            return isPomMetadata(descriptor, metadata) || isIvyMetadata(descriptor, metadata)
        }

        private fun isIvyMetadata(descriptor: Class<*>, metadata: ModuleComponentResolveMetadata): Boolean {
            return IvyModuleDescriptor::class.java.isAssignableFrom(descriptor) && metadata is IvyModuleResolveMetadata
        }

        private fun isPomMetadata(descriptor: Class<*>, metadata: ModuleComponentResolveMetadata): Boolean {
            return PomModuleDescriptor::class.java.isAssignableFrom(descriptor) && metadata is MavenModuleResolveMetadata
        }
    }
}
