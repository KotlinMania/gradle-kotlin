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

import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.internal.execution.ImmutableUnitOfWork
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.InputVisitor
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.snapshot.ValueSnapshot
import java.io.File

internal class ImmutableTransformExecution(
    transform: Transform,
    inputArtifact: File,
    dependencies: TransformDependencies,
    subject: TransformStepSubject,

    transformExecutionListener: TransformExecutionListener,
    buildOperationRunner: BuildOperationRunner,
    progressEventEmitter: BuildOperationProgressEventEmitter,
    fileCollectionFactory: FileCollectionFactory,
    inputFingerprinter: InputFingerprinter,
    private val workspaceProvider: ImmutableWorkspaceProvider,

    disableCachingByProperty: Boolean
) : AbstractTransformExecution(
    transform, inputArtifact, dependencies, subject,
    transformExecutionListener, buildOperationRunner, progressEventEmitter, fileCollectionFactory, inputFingerprinter,
    disableCachingByProperty
), ImmutableUnitOfWork {
    override fun getWorkspaceProvider(): ImmutableWorkspaceProvider {
        return workspaceProvider
    }

    override fun createIdentity(scalarInputs: MutableMap<String, ValueSnapshot>, fileInputs: MutableMap<String, CurrentFileCollectionFingerprint>): TransformWorkspaceIdentity {
        return TransformWorkspaceIdentity.Companion.createNormalizedImmutable(
            scalarInputs.get(AbstractTransformExecution.Companion.INPUT_ARTIFACT_PATH_PROPERTY_NAME),
            fileInputs.get(AbstractTransformExecution.Companion.INPUT_ARTIFACT_PROPERTY_NAME),
            scalarInputs.get(AbstractTransformExecution.Companion.SECONDARY_INPUTS_HASH_PROPERTY_NAME),
            fileInputs.get(AbstractTransformExecution.Companion.DEPENDENCIES_PROPERTY_NAME)!!.getHash()
        )
    }

    override fun visitImmutableInputs(visitor: InputVisitor) {
        super.visitImmutableInputs(visitor)
        visitInputArtifact(visitor)
    }
}
