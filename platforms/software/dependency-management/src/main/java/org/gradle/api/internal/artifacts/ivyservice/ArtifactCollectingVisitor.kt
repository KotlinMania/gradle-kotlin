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
package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.LocalDependencyFiles
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.internal.DisplayName
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.VariantIdentifier

class ArtifactCollectingVisitor @JvmOverloads constructor(val artifacts: MutableSet<ResolvedArtifact?> = LinkedHashSet<ResolvedArtifact?>()) : ArtifactVisitor {
    private var failures: MutableList<Throwable?>? = null

    override fun visitArtifact(artifactSetName: DisplayName, sourceVariantId: VariantIdentifier, attributes: ImmutableAttributes, capabilities: ImmutableCapabilities, artifact: ResolvableArtifact) {
        this.artifacts.add(artifact.toPublicView())
    }

    override fun visitFailure(failure: Throwable) {
        if (failures == null) {
            failures = ArrayList<Throwable?>()
        }
        failures!!.add(failure)
    }

    override fun prepareForVisit(source: FileCollectionInternal.Source): FileCollectionStructureVisitor.VisitType {
        if (source is LocalDependencyFiles) {
            return FileCollectionStructureVisitor.VisitType.NoContents
        }
        return FileCollectionStructureVisitor.VisitType.Visit
    }

    override fun requireArtifactFiles(): Boolean {
        return false
    }

    fun getFailures(): MutableList<Throwable?> {
        return (if (failures != null) failures else kotlin.collections.mutableListOf<kotlin.Throwable?>())!!
    }
}
