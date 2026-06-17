/*
 * Copyright 2021 the original author or authors.
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

import com.google.common.collect.ImmutableList
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.RelativePath
import java.io.File
import java.util.function.Consumer

/**
 * The result of running a single transform action on a single input artifact.
 *
 * The result of running a transform is a list of outputs.
 * There are two kinds of outputs for a transform:
 * - Produced outputs in the workspace. Those are relative paths depending on the workspace root, independent of the input artifact.
 * - Selected parts of the input artifact. These are relative paths of locations selected in the input artifact, independent of the workspace directory.
 *
 * The workspace can be relocated for immutable transform executions, and the input artifact can change.
 * Therefore, to get the absolute path of the output files they need to be resolved against both a workspace root and an input artifact.
 */
abstract class TransformExecutionResult protected constructor(protected val executionOutputs: ImmutableList<Builder.TransformExecutionOutput>) {
    /**
     * Transform results bound to a workspace.
     */
    interface TransformWorkspaceResult {
        /**
         * Resolves location of the outputs of this result for a given input artifact.
         *
         * Produced outputs don't need to be resolved to locations, since they are already resolved to absolute paths in the workspace.
         * The relative paths of selected parts of the input artifact need to resolved based on the provided input artifact location.
         */
        fun resolveForInputArtifact(inputArtifact: File): ImmutableList<File>?
    }

    abstract fun resolveForWorkspace(workspaceDir: File): TransformWorkspaceResult?

    fun visitOutputs(visitor: OutputVisitor) {
        executionOutputs.forEach(Consumer { output: Builder.TransformExecutionOutput? -> output!!.visitOutput(visitor) })
    }

    fun size(): Int {
        return executionOutputs.size
    }

    class Builder {
        private val builder = ImmutableList.builder<TransformExecutionOutput>()
        private var onlyProducedOutputs = true

        fun addEntireInputArtifact() {
            onlyProducedOutputs = false
            builder.add(EntireInputArtifact.Companion.INSTANCE)
        }

        fun addPartOfInputArtifact(relativePath: String) {
            onlyProducedOutputs = false
            builder.add(PartOfInputArtifact(relativePath))
        }

        fun addProducedOutput(relativePath: String) {
            builder.add(ProducedExecutionOutput(relativePath))
        }

        fun build(): TransformExecutionResult {
            val transformOutputs = builder.build()
            return if (onlyProducedOutputs)
                ProducedOutputOnlyResult(transformOutputs)
            else
                MixedInputAndProducedOutputResult(transformOutputs)
        }

        /**
         * Optimized variant for a transform whose results are all produced by the transform,
         * and don't include any of its input artifact.
         */
        private class ProducedOutputOnlyResult(executionOutputs: ImmutableList<TransformExecutionOutput>) : TransformExecutionResult(executionOutputs) {
            override fun resolveForWorkspace(workspaceDir: File): TransformWorkspaceResult {
                val resolvedOutputs = executionOutputs.stream()
                    .map<ProducedExecutionOutput> { obj: TransformExecutionOutput? -> ProducedExecutionOutput::class.java.cast(obj) }
                    .map<File> { output: ProducedExecutionOutput? -> output!!.resolveForWorkspaceDirectly(workspaceDir) }
                    .collect(ImmutableList.toImmutableList<File>())
                return TransformExecutionResult.TransformWorkspaceResult { inputArtifact: File -> resolvedOutputs }
            }
        }

        /**
         * Results of a transform that includes parts or the whole of its input artifact.
         * It might also include outputs produced by the transform.
         */
        private class MixedInputAndProducedOutputResult(executionOutputs: ImmutableList<TransformExecutionOutput>) : TransformExecutionResult(executionOutputs) {
            override fun resolveForWorkspace(workspaceDir: File): TransformWorkspaceResult {
                val resolvedOutputs = executionOutputs.stream()
                    .map<TransformWorkspaceOutput> { output: TransformExecutionOutput? -> output!!.resolveForWorkspace(workspaceDir) }
                    .collect(ImmutableList.toImmutableList<TransformWorkspaceOutput>())
                return TransformExecutionResult.TransformWorkspaceResult { inputArtifact: File ->
                    resolvedOutputs.stream()
                        .map<File> { output: TransformWorkspaceOutput? -> output!!.resolveForInputArtifact(inputArtifact) }
                        .collect(ImmutableList.toImmutableList<File>())
                }
            }
        }

        /**
         * A single output in a transform result.
         *
         * Can be either
         * - the entire input artifact [EntireInputArtifact]
         * - a part of the input artifact [PartOfInputArtifact]
         * - a produced output in the workspace [ProducedExecutionOutput]
         *
         * Only outputs related to the input artifact need resolving.
         */
        interface TransformExecutionOutput {
            fun resolveForWorkspace(workspaceDir: File): TransformWorkspaceOutput?

            fun visitOutput(visitor: OutputVisitor)
        }

        protected interface TransformWorkspaceOutput {
            fun resolveForInputArtifact(inputArtifact: File): File?
        }

        private class PartOfInputArtifact(private val relativePath: String) : TransformExecutionOutput, TransformWorkspaceOutput {
            override fun resolveForWorkspace(workspaceDir: File): TransformWorkspaceOutput {
                return this
            }

            override fun resolveForInputArtifact(inputArtifact: File): File {
                return File(inputArtifact, relativePath)
            }

            override fun visitOutput(visitor: OutputVisitor) {
                visitor.visitPartOfInputArtifact(relativePath)
            }
        }

        private class EntireInputArtifact : TransformExecutionOutput, TransformWorkspaceOutput {
            override fun resolveForWorkspace(workspaceDir: File): TransformWorkspaceOutput {
                return this
            }

            override fun resolveForInputArtifact(inputArtifact: File): File {
                return inputArtifact
            }

            override fun visitOutput(visitor: OutputVisitor) {
                visitor.visitEntireInputArtifact()
            }

            companion object {
                val INSTANCE: EntireInputArtifact = EntireInputArtifact()
            }
        }

        private class ProducedExecutionOutput(private val relativePath: String) : TransformExecutionOutput {
            override fun resolveForWorkspace(workspaceDir: File): TransformWorkspaceOutput {
                val workspacePath = resolveForWorkspaceDirectly(workspaceDir)
                return Builder.TransformWorkspaceOutput { inputArtifact: File -> workspacePath }
            }

            fun resolveForWorkspaceDirectly(workspaceDir: File): File {
                return File(workspaceDir, relativePath)
            }

            override fun visitOutput(visitor: OutputVisitor) {
                visitor.visitProducedOutput(relativePath)
            }
        }
    }

    interface OutputVisitor {
        /**
         * Called when the result is the full input artifact.
         */
        fun visitEntireInputArtifact()

        /**
         * Called when the result is inside the input artifact.
         *
         * @param relativePath the relative path from the input artifact to the selected location in the input artifact.
         */
        fun visitPartOfInputArtifact(relativePath: String)

        /**
         * Called when the result is a produced output in the workspace.
         *
         * @param relativePath the relative path of the output in the workspace.
         */
        fun visitProducedOutput(relativePath: String)
    }

    /**
     * A [TransformExecutionResult] builder which accepts absolute locations of results.
     *
     *
     * The builder then infers if the result is (in) the input artifact or a produced output in the workspace.
     */
    class OutputTypeInferringBuilder(private val inputArtifact: File, private val outputDir: File) {
        private val inputArtifactPrefix: String
        private val outputDirPrefix: String
        private val delegate: Builder = builder()

        init {
            this.inputArtifactPrefix = inputArtifact.getPath() + File.separator
            this.outputDirPrefix = outputDir.getPath() + File.separator
        }

        /**
         * Adds an output location to the result.
         *
         * @param workspaceAction an action to run when the output is a produced output in the workspace.
         */
        fun addOutput(output: File, workspaceAction: Consumer<File>) {
            if (output == inputArtifact) {
                delegate.addEntireInputArtifact()
            } else if (output == outputDir) {
                delegate.addProducedOutput("")
                workspaceAction.accept(output)
            } else if (output.getPath().startsWith(outputDirPrefix)) {
                val relativePath = RelativePath.parse(true, output.getPath().substring(outputDirPrefix.length)).getPathString()
                delegate.addProducedOutput(relativePath)
                workspaceAction.accept(output)
            } else if (output.getPath().startsWith(inputArtifactPrefix)) {
                val relativePath = RelativePath.parse(true, output.getPath().substring(inputArtifactPrefix.length)).getPathString()
                delegate.addPartOfInputArtifact(relativePath)
            } else {
                throw InvalidUserDataException("Transform output " + output.getPath() + " must be a part of the input artifact or refer to a relative path.")
            }
        }

        fun build(): TransformExecutionResult {
            return delegate.build()
        }
    }

    companion object {
        fun builderFor(inputArtifact: File, outputDir: File): OutputTypeInferringBuilder {
            return OutputTypeInferringBuilder(inputArtifact, outputDir)
        }

        fun builder(): Builder {
            return Builder()
        }
    }
}
