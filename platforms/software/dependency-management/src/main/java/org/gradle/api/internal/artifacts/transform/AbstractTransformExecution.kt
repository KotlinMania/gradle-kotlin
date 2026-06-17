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

import com.google.common.collect.ImmutableSortedSet
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.file.DefaultFileSystemLocation
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.tasks.properties.DefaultInputFilePropertySpec
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec
import org.gradle.api.provider.Provider
import org.gradle.internal.execution.ExecutionContext
import org.gradle.internal.execution.Identity
import org.gradle.internal.execution.ImplementationVisitor
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.InputVisitor
import org.gradle.internal.execution.OutputVisitor
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.WorkOutput
import org.gradle.internal.execution.caching.CachingDisabledReason
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory
import org.gradle.internal.execution.caching.CachingState
import org.gradle.internal.execution.history.OverlappingOutputs
import org.gradle.internal.execution.model.InputNormalizer
import org.gradle.internal.file.TreeType
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.hash.Hashing
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.internal.operations.UncategorizedBuildOperations
import org.gradle.internal.properties.InputBehavior
import org.gradle.internal.properties.PropertyValue
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.operations.dependencies.transforms.ExecuteTransformActionBuildOperationType
import org.gradle.operations.dependencies.transforms.IdentifyTransformExecutionProgressDetails
import org.gradle.operations.dependencies.transforms.SnapshotTransformInputsBuildOperationType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Optional
import java.util.function.Supplier
import javax.annotation.OverridingMethodsMustInvokeSuper

internal abstract class AbstractTransformExecution protected constructor(
    protected val transform: Transform,
    protected val inputArtifact: File,
    private val dependencies: TransformDependencies,
    private val subject: TransformStepSubject,
    private val transformExecutionListener: TransformExecutionListener,
    private val buildOperationRunner: BuildOperationRunner,
    private val progressEventEmitter: BuildOperationProgressEventEmitter,
    private val fileCollectionFactory: FileCollectionFactory,
    protected val inputFingerprinter: InputFingerprinter,
    private val disableCachingByProperty: Boolean
) : UnitOfWork {
    private val inputArtifactProvider: Provider<FileSystemLocation>

    private var operationContext: BuildOperationContext? = null

    init {
        this.inputArtifactProvider = Providers.of<FileSystemLocation>(DefaultFileSystemLocation(inputArtifact))
    }

    override fun getBuildOperationWorkType(): Optional<String> {
        return Optional.of<String>("TRANSFORM")
    }

    override fun identify(scalarInputs: MutableMap<String, ValueSnapshot>, fileInputs: MutableMap<String, CurrentFileCollectionFingerprint>): Identity {
        val transformWorkspaceIdentity = createIdentity(scalarInputs, fileInputs)
        emitIdentifyTransformExecutionProgressDetails(transformWorkspaceIdentity)
        return transformWorkspaceIdentity
    }

    protected abstract fun createIdentity(scalarInputs: MutableMap<String, ValueSnapshot>, fileInputs: MutableMap<String, CurrentFileCollectionFingerprint>): TransformWorkspaceIdentity

    override fun execute(executionContext: ExecutionContext): WorkOutput {
        transformExecutionListener.beforeTransformExecution(transform, subject)
        try {
            return executeWithinTransformerListener(executionContext)
        } finally {
            transformExecutionListener.afterTransformExecution(transform, subject)
        }
    }

    private fun executeWithinTransformerListener(executionRequest: ExecutionContext): WorkOutput {
        val result: TransformExecutionResult? = buildOperationRunner.call<TransformExecutionResult>(object : CallableBuildOperation<TransformExecutionResult> {
            override fun call(context: BuildOperationContext): TransformExecutionResult {
                try {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Transforming {} with {}", subject.getDisplayName(), transform.getDisplayName())
                    }
                    val workspace = executionRequest.getWorkspace()
                    val inputChanges = executionRequest.getInputChanges().orElse(null)
                    val result = transform.transform(inputArtifactProvider, getOutputDir(workspace), dependencies, inputChanges)
                    val resultSerializer = TransformExecutionResultSerializer()
                    resultSerializer.writeToFile(getResultsFile(workspace), result)
                    return result
                } finally {
                    context.setResult(ExecuteTransformActionBuildOperationType.RESULT_INSTANCE)
                }
            }

            override fun description(): BuildOperationDescriptor.Builder {
                val displayName = transform.getDisplayName() + " " + inputArtifact.getName()
                return BuildOperationDescriptor.displayName(displayName)
                    .details(ExecuteTransformActionBuildOperationType.DETAILS_INSTANCE)
                    .metadata(UncategorizedBuildOperations.TRANSFORM_ACTION)
                    .progressDisplayName(displayName)
            }
        })

        return object : WorkOutput {
            override fun getDidWork(): WorkOutput.WorkResult {
                return WorkOutput.WorkResult.DID_WORK
            }

            override fun getOutput(workspace: File): Any {
                return result!!.resolveForWorkspace(getOutputDir(workspace))
            }
        }
    }

    override fun loadAlreadyProducedOutput(workspace: File): Any {
        val resultSerializer = TransformExecutionResultSerializer()
        return resultSerializer.readResultsFile(getResultsFile(workspace)).resolveForWorkspace(getOutputDir(workspace))
    }

    override fun getInputFingerprinter(): InputFingerprinter {
        return inputFingerprinter
    }

    override fun visitImplementations(visitor: ImplementationVisitor) {
        visitor.visitImplementation(transform.getImplementationClass())
    }

    @OverridingMethodsMustInvokeSuper
    override fun visitImmutableInputs(visitor: InputVisitor) {
        // Emulate secondary inputs as a single property for now
        visitor.visitInputProperty(SECONDARY_INPUTS_HASH_PROPERTY_NAME, InputVisitor.ValueSupplier { transform.getSecondaryInputHash() })
        visitor.visitInputProperty(INPUT_ARTIFACT_PATH_PROPERTY_NAME, InputVisitor.ValueSupplier { // We always need the name as an input to the artifact transform,
            // since it is part of the ComponentArtifactIdentifier returned by the transform.
            // For absolute paths, the name is already part of the normalized path,
            // and for all the other normalization strategies we use the name directly.
            if (transform.getInputArtifactNormalizer() === InputNormalizer.ABSOLUTE_PATH)
                inputArtifact.getAbsolutePath()
            else
                inputArtifact.getName()
        })
        visitor.visitInputFileProperty(
            DEPENDENCIES_PROPERTY_NAME, InputBehavior.NON_INCREMENTAL,
            InputVisitor.InputFileValueSupplier(
                dependencies,
                transform.getInputArtifactDependenciesNormalizer(),
                transform.getInputArtifactDependenciesDirectorySensitivity(),
                transform.getInputArtifactDependenciesLineEndingNormalization(),
                Supplier {
                    dependencies.getFiles()
                        .orElse(FileCollectionFactory.empty())
                })
        )
    }

    protected fun emitIdentifyTransformExecutionProgressDetails(transformWorkspaceIdentity: TransformWorkspaceIdentity) {
        progressEventEmitter.emitNowIfCurrent(
            DefaultIdentifyTransformExecutionProgressDetails(
                inputArtifact,
                transformWorkspaceIdentity,
                transform,
                subject.getInitialComponentIdentifier()
            )
        )
    }

    protected fun visitInputArtifact(visitor: InputVisitor) {
        visitor.visitInputFileProperty(
            INPUT_ARTIFACT_PROPERTY_NAME, InputBehavior.INCREMENTAL,
            InputVisitor.InputFileValueSupplier(
                inputArtifactProvider,
                transform.getInputArtifactNormalizer(),
                transform.getInputArtifactDirectorySensitivity(),
                transform.getInputArtifactLineEndingNormalization(),
                Supplier { fileCollectionFactory.fixed(inputArtifact) })
        )
    }

    override fun visitOutputs(workspace: File, visitor: OutputVisitor) {
        val outputDir: File = getOutputDir(workspace)
        val resultsFile: File = getResultsFile(workspace)
        visitor.visitOutputProperty(
            OUTPUT_DIRECTORY_PROPERTY_NAME, TreeType.DIRECTORY,
            OutputVisitor.OutputFileValueSupplier.fromStatic(outputDir, fileCollectionFactory.fixed(outputDir))
        )
        visitor.visitOutputProperty(
            RESULTS_FILE_PROPERTY_NAME, TreeType.FILE,
            OutputVisitor.OutputFileValueSupplier.fromStatic(resultsFile, fileCollectionFactory.fixed(resultsFile))
        )
    }

    override fun markLegacySnapshottingInputsStarted() {
        this.operationContext = buildOperationRunner.start(
            BuildOperationDescriptor
                .displayName("Snapshot transform inputs")
                .name("Snapshot transform inputs")
                .details(SNAPSHOT_TRANSFORM_INPUTS_DETAILS)
        )
    }

    override fun markLegacySnapshottingInputsFinished(cachingState: CachingState) {
        if (operationContext != null) {
            val builder = ImmutableSortedSet.naturalOrder<InputFilePropertySpec>()
            builder.add(
                DefaultInputFilePropertySpec(
                    INPUT_ARTIFACT_PROPERTY_NAME,
                    transform.getInputArtifactNormalizer(),
                    FileCollectionFactory.empty(),
                    PropertyValue.ABSENT,
                    InputBehavior.INCREMENTAL,
                    transform.getInputArtifactDirectorySensitivity(),
                    transform.getInputArtifactLineEndingNormalization()
                )
            )
            builder.add(
                DefaultInputFilePropertySpec(
                    DEPENDENCIES_PROPERTY_NAME,
                    transform.getInputArtifactDependenciesNormalizer(),
                    FileCollectionFactory.empty(),
                    PropertyValue.ABSENT,
                    InputBehavior.NON_INCREMENTAL,
                    transform.getInputArtifactDependenciesDirectorySensitivity(),
                    transform.getInputArtifactDependenciesLineEndingNormalization()
                )
            )
            operationContext!!.setResult(SnapshotTransformInputsBuildOperationResult(cachingState, builder.build()))
            operationContext = null
        }
    }

    override fun shouldDisableCaching(detectedOverlappingOutputs: OverlappingOutputs?): Optional<CachingDisabledReason> {
        return if (transform.isCacheable())
            maybeDisableCachingByProperty()
        else
            Optional.of<CachingDisabledReason>(NOT_CACHEABLE)
    }

    private fun maybeDisableCachingByProperty(): Optional<CachingDisabledReason> {
        if (disableCachingByProperty) {
            return Optional.of<CachingDisabledReason>(CACHING_DISABLED_REASON)
        }

        return Optional.empty<CachingDisabledReason>()
    }

    override fun getDisplayName(): String {
        return transform.getDisplayName() + ": " + inputArtifact
    }

    private class DefaultIdentifyTransformExecutionProgressDetails(
        private val inputArtifact: File,
        private val transformWorkspaceIdentity: TransformWorkspaceIdentity,
        private val transform: Transform,
        private val componentIdentifier: ComponentIdentifier
    ) : IdentifyTransformExecutionProgressDetails {
        val identity: String
            get() = transformWorkspaceIdentity.getUniqueId()

        val fromAttributes: MutableMap<String, String>
            get() = AttributesToMapConverter.convertToMap(transform.getFromAttributes())

        val toAttributes: MutableMap<String, String>
            get() = AttributesToMapConverter.convertToMap(transform.getToAttributes())

        val componentId: ComponentIdentifier
            get() = ComponentToOperationConverter.convertComponentIdentifier(componentIdentifier)

        val artifactName: String
            get() = inputArtifact.getName()

        val transformActionClass: Class<*>
            get() = transform.getImplementationClass()

        val secondaryInputValueHashBytes: ByteArray
            get() = Hashing.hashHashable(transformWorkspaceIdentity.getSecondaryInputsSnapshot()).toByteArray()
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(AbstractTransformExecution::class.java)
        private val NOT_CACHEABLE = CachingDisabledReason(CachingDisabledReasonCategory.NOT_CACHEABLE, "Caching not enabled.")
        private val CACHING_DISABLED_REASON = CachingDisabledReason(CachingDisabledReasonCategory.NOT_CACHEABLE, "Caching disabled by property ('org.gradle.internal.transform-caching-disabled')")

        protected const val INPUT_ARTIFACT_PROPERTY_NAME: String = "inputArtifact"
        private const val OUTPUT_DIRECTORY_PROPERTY_NAME = "outputDirectory"
        private const val RESULTS_FILE_PROPERTY_NAME = "resultsFile"
        protected const val INPUT_ARTIFACT_PATH_PROPERTY_NAME: String = "inputArtifactPath"
        protected const val DEPENDENCIES_PROPERTY_NAME: String = "inputArtifactDependencies"
        protected const val SECONDARY_INPUTS_HASH_PROPERTY_NAME: String = "inputPropertiesHash"

        private val SNAPSHOT_TRANSFORM_INPUTS_DETAILS: SnapshotTransformInputsBuildOperationType.Details = object : SnapshotTransformInputsBuildOperationType.Details {}

        private fun getOutputDir(workspace: File): File {
            return File(workspace, "transformed")
        }

        private fun getResultsFile(workspace: File): File {
            return File(workspace, "results.bin")
        }
    }
}
