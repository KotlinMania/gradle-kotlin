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
package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.DisplayName
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.VariantIdentifier
import java.io.File

class ResolvedFilesCollectingVisitor : ArtifactVisitor {
    val files: MutableSet<File> = LinkedHashSet<File>()
    private val failures: MutableSet<Throwable> = LinkedHashSet<Throwable>()

    override fun visitArtifact(artifactSetName: DisplayName, sourceVariantId: VariantIdentifier, attributes: ImmutableAttributes, capabilities: ImmutableCapabilities, artifact: ResolvableArtifact) {
        try {
            val file = artifact.file
            this.files.add(file)
        } catch (t: Exception) {
            failures.add(t)
        }
    }

    override fun requireArtifactFiles(): Boolean {
        return true
    }

    override fun visitFailure(failure: Throwable) {
        failures.add(failure)
    }

    fun getFailures(): MutableCollection<Throwable> {
        return failures
    }
}
