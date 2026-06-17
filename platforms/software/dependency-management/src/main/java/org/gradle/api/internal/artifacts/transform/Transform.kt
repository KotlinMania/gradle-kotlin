/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.Describable
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.provider.Provider
import org.gradle.work.InputChanges
import java.io.File

/**
 * The actual code which needs to be executed to transform a file.
 *
 * This encapsulates the public interface [org.gradle.api.artifacts.transform.TransformAction] into an internal type.
 */
interface Transform : Describable, TaskDependencyContainer {
    @JvmField
    val implementationClass: Class<out TransformAction<*>>?

    @JvmField
    val fromAttributes: ImmutableAttributes?

    @JvmField
    val toAttributes: ImmutableAttributes?

    /**
     * Whether the transformer requires dependencies of the transformed artifact to be injected.
     */
    fun requiresDependencies(): Boolean

    /**
     * Whether the transformer requires [InputChanges] to be injected.
     */
    fun requiresInputChanges(): Boolean

    /**
     * Whether the transformer is cacheable.
     */
    @JvmField
    val isCacheable: Boolean

    fun transform(inputArtifactProvider: Provider<FileSystemLocation>, outputDir: File, dependencies: TransformDependencies, inputChanges: InputChanges?): TransformExecutionResult?

    /**
     * The hash of the secondary inputs of the transformer.
     *
     * This includes the parameters and the implementation.
     */
    val secondaryInputHash: HashCode?

    fun isolateParametersIfNotAlready()

    val inputArtifactNormalizer: FileNormalizer?

    val inputArtifactDependenciesNormalizer: FileNormalizer?

    val isIsolated: Boolean

    val inputArtifactDirectorySensitivity: DirectorySensitivity?

    val inputArtifactDependenciesDirectorySensitivity: DirectorySensitivity?

    val inputArtifactLineEndingNormalization: LineEndingSensitivity?

    val inputArtifactDependenciesLineEndingNormalization: LineEndingSensitivity?
}
