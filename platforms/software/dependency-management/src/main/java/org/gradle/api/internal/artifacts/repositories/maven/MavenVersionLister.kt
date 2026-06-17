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
package org.gradle.api.internal.artifacts.repositories.maven

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern
import org.gradle.api.resources.MissingResourceException
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult
import org.gradle.internal.resource.ExternalResourceName

class MavenVersionLister(private val mavenMetadataLoader: MavenMetadataLoader) {
    fun listVersions(module: ModuleIdentifier, patterns: MutableList<ResourcePattern>, result: BuildableModuleVersionListingResolveResult) {
        val searched: MutableSet<ExternalResourceName?> = HashSet<ExternalResourceName?>()

        val versions: MutableList<String?> = ArrayList<String?>()
        var hasResult = false
        for (pattern in patterns) {
            val metadataLocation = pattern.toModulePath(module).resolve("maven-metadata.xml")

            if (searched.add(metadataLocation)) {
                result.attempted(metadataLocation)
                try {
                    val mavenMetaData = mavenMetadataLoader.load(metadataLocation)
                    versions.addAll(mavenMetaData.versions)
                    hasResult = true
                } catch (e: MissingResourceException) {
                    // Continue
                }
            }
        }
        if (hasResult) {
            result.listed(versions)
        }
    }
}
