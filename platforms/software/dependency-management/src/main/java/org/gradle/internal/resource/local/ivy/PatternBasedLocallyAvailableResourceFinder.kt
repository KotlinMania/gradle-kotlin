/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.resource.local.ivy

import org.gradle.api.file.EmptyFileVisitor
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern
import org.gradle.api.internal.file.collections.MinimalFileTree
import org.gradle.api.internal.file.collections.SingleIncludePatternFileTree
import org.gradle.internal.Factory
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.resource.local.AbstractLocallyAvailableResourceFinder
import java.io.File
import java.util.LinkedList
import java.util.function.Function

class PatternBasedLocallyAvailableResourceFinder(baseDir: File, pattern: ResourcePattern, checksumService: ChecksumService?) : AbstractLocallyAvailableResourceFinder<ModuleComponentArtifactMetadata?>(
    createProducer(baseDir, pattern), checksumService
) {
    companion object {
        private fun createProducer(baseDir: File, pattern: ResourcePattern): Function<ModuleComponentArtifactMetadata?, Factory<MutableList<File?>?>?> {
            return object : Function<ModuleComponentArtifactMetadata?, Factory<MutableList<File?>?>?> {
                override fun apply(artifact: ModuleComponentArtifactMetadata?): Factory<MutableList<File?>?> {
                    return org.gradle.internal.Factory {
                        val files: MutableList<File?> = LinkedList<File?>()
                        if (artifact != null) {
                            getMatchingFiles(artifact).visit(object : EmptyFileVisitor() {
                                override fun visitFile(fileDetails: FileVisitDetails) {
                                    files.add(fileDetails.getFile())
                                }
                            })
                        }
                        files
                    }
                }

                fun getMatchingFiles(artifact: ModuleComponentArtifactMetadata): MinimalFileTree {
                    val patternString = pattern.getLocation(artifact).getPath()
                    return SingleIncludePatternFileTree(baseDir, patternString)
                }
            }
        }
    }
}
