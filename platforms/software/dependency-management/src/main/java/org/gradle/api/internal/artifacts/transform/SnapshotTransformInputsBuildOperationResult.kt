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
package org.gradle.api.internal.artifacts.transform

import com.google.common.collect.ImmutableSortedMap
import org.gradle.api.internal.tasks.BaseSnapshotInputsBuildOperationResult
import org.gradle.api.internal.tasks.FilePropertyVisitState
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec
import org.gradle.internal.execution.caching.CachingState
import org.gradle.internal.execution.history.BeforeExecutionState
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.hash.HashCode
import org.gradle.operations.dependencies.transforms.SnapshotTransformInputsBuildOperationType
import org.gradle.operations.execution.FilePropertyVisitor
import java.util.function.Consumer
import java.util.function.Function

class SnapshotTransformInputsBuildOperationResult(cachingState: CachingState, private val inputFilePropertySpecs: MutableSet<InputFilePropertySpec>) :
    BaseSnapshotInputsBuildOperationResult(cachingState), SnapshotTransformInputsBuildOperationType.Result {
    override fun visitInputFileProperties(visitor: FilePropertyVisitor) {
        getBeforeExecutionState()
            .map<ImmutableSortedMap<String, CurrentFileCollectionFingerprint>>(Function { obj: BeforeExecutionState? -> obj!!.getInputFileProperties() })
            .ifPresent(Consumer { inputFileProperties: ImmutableSortedMap<String?, CurrentFileCollectionFingerprint?>? ->
                FilePropertyVisitState.visitInputFileProperties(
                    inputFileProperties,
                    visitor,
                    inputFilePropertySpecs
                )
            })
    }

    override fun fileProperties(): MutableMap<String, Any> {
        val visitor = FilePropertyCollectingVisitor()
        visitInputFileProperties(visitor)
        return visitor.getFileProperties()
    }

    private class FilePropertyCollectingVisitor : BaseFilePropertyCollectingVisitor<FilePropertyVisitor.VisitState>(), FilePropertyVisitor {
        override fun createProperty(state: FilePropertyVisitor.VisitState): Property {
            return BaseFilePropertyCollectingVisitor.Property(HashCode.fromBytes(state.propertyHashBytes!!).toString(), state.propertyAttributes!!)
        }
    }
}
