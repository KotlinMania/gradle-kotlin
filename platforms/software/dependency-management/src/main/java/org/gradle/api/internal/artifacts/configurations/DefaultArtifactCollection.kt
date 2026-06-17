/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.internal.artifacts.ivyservice.ResolvedArtifactCollectingVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.provider.BuildableBackedProvider
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.provider.Provider
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.Factory
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.model.CalculatedValueFactory
import java.util.function.Supplier

class DefaultArtifactCollection(
    private val artifacts: SelectedArtifactSet,
    private val lenient: Boolean,
    private val resolutionHost: ResolutionHost,
    private val taskDependencyFactory: TaskDependencyFactory,
    calculatedValueFactory: CalculatedValueFactory,
    attributeDesugaring: AttributeDesugaring
) : ArtifactCollectionInternal {
    private val result: CalculatedValue<ArtifactSetResult>

    init {
        this.result = calculatedValueFactory.create<ArtifactSetResult>(resolutionHost.displayName("files"), Supplier {
            val visitor = ResolvedArtifactCollectingVisitor(attributeDesugaring)
            artifacts.visitArtifacts(visitor, lenient)

            val artifactResults = visitor.artifacts
            val failures = visitor.failures

            if (!lenient) {
                resolutionHost.rethrowFailuresAndReportProblems("artifacts", failures)
            }
            ArtifactSetResult(artifactResults, failures)
        })
    }

    override fun getResolutionHost(): ResolutionHost {
        return resolutionHost
    }

    override fun isLenient(): Boolean {
        return lenient
    }

    override fun getArtifactFiles(): FileCollectionInternal {
        return ResolutionBackedFileCollection(
            artifacts,
            lenient,
            resolutionHost,
            taskDependencyFactory
        )
    }

    override fun getArtifacts(): MutableSet<ResolvedArtifactResult> {
        ensureResolved()
        return result.get().artifactResults
    }

    override fun getResolvedArtifacts(): Provider<MutableSet<ResolvedArtifactResult>> {
        return BuildableBackedProvider<FileCollectionInternal, MutableSet<ResolvedArtifactResult?>?>(
            getArtifactFiles(),
            uncheckedCast<Class<MutableSet<ResolvedArtifactResult>>?>(MutableSet::class.java),
            ArtifactCollectionResolvedArtifactsFactory(this)
        )
    }

    override fun iterator(): MutableIterator<ResolvedArtifactResult> {
        ensureResolved()
        return result.get().artifactResults.iterator()
    }

    override fun getFailures(): MutableCollection<Throwable> {
        ensureResolved()
        return result.get().failures
    }

    override fun visitArtifacts(visitor: ArtifactVisitor) {
        // TODO - if already resolved, use the results
        artifacts.visitArtifacts(visitor, lenient)
    }

    private fun ensureResolved() {
        result.finalizeIfNotAlready()
    }

    private class ArtifactSetResult(private val artifactResults: MutableSet<ResolvedArtifactResult>, private val failures: MutableSet<Throwable>)

    private class ArtifactCollectionResolvedArtifactsFactory(private val artifactCollection: ArtifactCollection) : Factory<MutableSet<ResolvedArtifactResult>?> {
        override fun create(): MutableSet<ResolvedArtifactResult> {
            return artifactCollection.getArtifacts()
        }
    }
}
