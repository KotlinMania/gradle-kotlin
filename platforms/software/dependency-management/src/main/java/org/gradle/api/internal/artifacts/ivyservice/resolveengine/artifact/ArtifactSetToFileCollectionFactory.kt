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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.internal.artifacts.configurations.ResolutionHost
import org.gradle.api.internal.artifacts.ivyservice.TypedResolveException
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ParallelResolveArtifactSet.Companion.wrap
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.internal.DisplayName
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.DefaultIvyArtifactName.Companion.forFile
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.File
import java.util.Optional

@ServiceScope(Scope.BuildTree::class)
class ArtifactSetToFileCollectionFactory(private val buildOperationExecutor: BuildOperationExecutor, private val problemsService: ProblemsInternal) {
    fun resolutionHost(displayName: String): ResolutionHost {
        return NameBackedResolutionHost(problemsService, displayName)
    }

    /**
     * Presents the contents of the given artifacts as a partial [SelectedArtifactSet] implementation.
     *
     *
     * This produces only a minimal implementation to use for artifact sets loaded from the configuration cache
     * Over time, this should be merged with the FileCollection implementation in DefaultConfiguration
     */
    fun getSelectedArtifacts(elements: MutableList<*>): SelectedArtifactSet {
        return PartialSelectedArtifactSet(elements, buildOperationExecutor)
    }

    fun asResolvedArtifactSet(failure: Throwable?): ResolvedArtifactSet {
        return BrokenResolvedArtifactSet(failure)
    }

    fun asResolvedArtifactSet(
        id: ComponentArtifactIdentifier,
        sourceVariantId: VariantIdentifier?,
        variantAttributes: ImmutableAttributes?,
        capabilities: ImmutableCapabilities?,
        artifactSetName: DisplayName?,
        file: File
    ): ResolvedArtifactSet {
        return object : ResolvedArtifactSet {
            override fun visit(visitor: ResolvedArtifactSet.Visitor) {
                visitor.visitArtifacts(object : ResolvedArtifactSet.Artifacts {
                    override fun startFinalization(actions: BuildOperationQueue<RunnableBuildOperation?>?, requireFiles: Boolean) {
                        // Nothing to do
                    }

                    override fun visit(visitor: ArtifactVisitor) {
                        if (visitor.prepareForVisit(FileCollectionInternal.OTHER) == FileCollectionStructureVisitor.VisitType.Visit) {
                            visitor.visitArtifact(artifactSetName, sourceVariantId, variantAttributes, capabilities, object : ResolvableArtifact {
                                val id: ComponentArtifactIdentifier
                                    get() = id

                                val isResolveSynchronously: Boolean
                                    get() = true

                                val artifactName: IvyArtifactName
                                    get() = forFile(file, null)

                                val file: File
                                    get() = file

                                val fileSource: CalculatedValue<File?>?
                                    get() {
                                        throw UnsupportedOperationException()
                                    }

                                override fun transformedTo(file: File?): ResolvableArtifact? {
                                    throw UnsupportedOperationException()
                                }

                                override fun toPublicView(): ResolvedArtifact? {
                                    throw UnsupportedOperationException()
                                }

                                override fun visitDependencies(context: TaskDependencyResolveContext?) {
                                }
                            })
                            visitor.endVisitCollection(FileCollectionInternal.OTHER)
                        }
                    }
                })
            }

            override fun visitTransformSources(visitor: ResolvedArtifactSet.TransformSourceVisitor?) {
                throw UnsupportedOperationException()
            }

            override fun visitExternalArtifacts(visitor: Action<ResolvableArtifact?>?) {
                throw UnsupportedOperationException()
            }

            override fun visitDependencies(context: TaskDependencyResolveContext?) {
                throw UnsupportedOperationException()
            }
        }
    }

    private class NameBackedResolutionHost(private val problemsService: ProblemsInternal, private val displayName: String) : ResolutionHost, DisplayName {
        override fun getDisplayName(): String {
            return displayName
        }

        override fun getCapitalizedDisplayName(): String {
            return StringUtils.capitalize(displayName)
        }

        override fun displayName(): DisplayName {
            return this
        }

        override fun getProblems(): ProblemsInternal {
            return problemsService
        }

        override fun consolidateFailures(resolutionType: String, failures: MutableCollection<Throwable?>): Optional<TypedResolveException?> {
            if (failures.isEmpty()) {
                return Optional.empty<TypedResolveException?>()
            } else {
                reportProblems(failures)
                return Optional.of<TypedResolveException?>(TypedResolveException(resolutionType, displayName, failures))
            }
        }
    }

    private class FileBackedArtifactSet(private val file: File?) : ResolvedArtifactSet {
        override fun visit(visitor: ResolvedArtifactSet.Visitor) {
            visitor.visitArtifacts(object : ResolvedArtifactSet.Artifacts {
                override fun startFinalization(actions: BuildOperationQueue<RunnableBuildOperation?>?, requireFiles: Boolean) {
                    // Nothing to do
                }

                override fun visit(visitor: ArtifactVisitor) {
                    if (visitor.prepareForVisit(FileCollectionInternal.OTHER) == FileCollectionStructureVisitor.VisitType.Visit) {
                        (visitor as ArtifactVisitorToResolvedFileVisitorAdapter).visitFile(file)
                        visitor.endVisitCollection(FileCollectionInternal.OTHER)
                    }
                }
            })
        }

        override fun visitTransformSources(visitor: ResolvedArtifactSet.TransformSourceVisitor?) {
            throw UnsupportedOperationException()
        }

        override fun visitExternalArtifacts(visitor: Action<ResolvableArtifact?>?) {
            throw UnsupportedOperationException()
        }

        override fun visitDependencies(context: TaskDependencyResolveContext?) {
            throw UnsupportedOperationException()
        }
    }

    // "partial" in the sense that some artifacts are only available as a File, and have no metadata
    private class PartialSelectedArtifactSet(private val elements: MutableList<*>, private val buildOperationExecutor: BuildOperationExecutor) : SelectedArtifactSet {
        override fun visitArtifacts(visitor: ArtifactVisitor, continueOnSelectionFailure: Boolean) {
            val artifactSets: MutableList<ResolvedArtifactSet?> = ArrayList<ResolvedArtifactSet?>()
            for (element in elements) {
                if (element is ResolvedArtifactSet) {
                    artifactSets.add(element)
                } else {
                    // Should not be used, and cannot be provided as the artifact metadata may have been discarded.
                    throw UnsupportedOperationException()
                }
            }
            wrap(CompositeResolvedArtifactSet.Companion.of(artifactSets), buildOperationExecutor).visit(visitor)
        }

        override fun visitFiles(visitor: ResolvedFileVisitor?, continueOnSelectionFailure: Boolean) {
            val artifactSets: MutableList<ResolvedArtifactSet?> = ArrayList<ResolvedArtifactSet?>()
            for (element in elements) {
                if (element is ResolvedArtifactSet) {
                    artifactSets.add(element)
                } else {
                    val file = element as File?
                    artifactSets.add(FileBackedArtifactSet(file))
                }
            }
            wrap(CompositeResolvedArtifactSet.Companion.of(artifactSets), buildOperationExecutor).visit(ArtifactVisitorToResolvedFileVisitorAdapter(visitor))
        }

        override fun visitDependencies(context: TaskDependencyResolveContext?) {
            // No dependencies
        }
    }
}
