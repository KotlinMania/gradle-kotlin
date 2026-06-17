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

import org.gradle.api.internal.artifacts.ivyservice.ResolvedFileCollectionVisitor
import org.gradle.api.internal.artifacts.ivyservice.TypedResolveException
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet
import org.gradle.api.internal.file.AbstractFileCollection
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.tasks.FailureCollectingTaskDependencyResolveContext
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.internal.logging.text.TreeFormatter
import java.util.function.Consumer

class ResolutionBackedFileCollection(
    private val artifacts: SelectedArtifactSet,
    val isLenient: Boolean,
    val resolutionHost: ResolutionHost,
    taskDependencyFactory: TaskDependencyFactory
) : AbstractFileCollection(taskDependencyFactory) {
    override fun visitDependencies(context: TaskDependencyResolveContext) {
        val collectingContext = FailureCollectingTaskDependencyResolveContext(context)
        artifacts.visitDependencies(collectingContext)
        if (!this.isLenient) {
            resolutionHost.consolidateFailures("dependencies", collectingContext.getFailures()).ifPresent(Consumer { consolidatedFailure: TypedResolveException? ->
                resolutionHost.reportProblems(consolidatedFailure!!)
                context.visitFailure(consolidatedFailure)
            })
        }
    }

    override fun getDisplayName(): String {
        return resolutionHost.displayName("files").getDisplayName()
    }

    override fun visitContents(visitor: FileCollectionStructureVisitor) {
        val collectingVisitor = ResolvedFileCollectionVisitor(visitor)
        artifacts.visitFiles(collectingVisitor, this.isLenient)
        maybeThrowResolutionFailures(collectingVisitor)
    }

    /**
     * If the file collection is not lenient, rethrow any failures that occurred during the visit.
     *
     * @throws ArtifactSelectionException subtypes
     */
    private fun maybeThrowResolutionFailures(collectingVisitor: ResolvedFileCollectionVisitor) {
        if (!this.isLenient) {
            resolutionHost.rethrowFailuresAndReportProblems("files", collectingVisitor.failures)
        }
    }

    override fun appendContents(formatter: TreeFormatter) {
        formatter.node("contains: " + getDisplayName())
    }
}
