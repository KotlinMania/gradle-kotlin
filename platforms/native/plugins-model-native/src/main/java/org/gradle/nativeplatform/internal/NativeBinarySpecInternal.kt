/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativeplatform.internal

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.language.nativeplatform.DependentSourceSet
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.Flavor
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeDependencySet
import org.gradle.nativeplatform.Tool
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.platform.base.internal.BinarySpecInternal

interface NativeBinarySpecInternal : NativeBinarySpec, BinarySpecInternal {
    fun setFlavor(flavor: Flavor?)

    fun setToolChain(toolChain: NativeToolChain?)

    fun setTargetPlatform(targetPlatform: NativePlatform?)

    fun setBuildType(buildType: BuildType?)

    fun getToolByName(name: String?): Tool?

    var platformToolProvider: PlatformToolProvider?

    fun setResolver(resolver: NativeDependencyResolver?)

    fun setFileCollectionFactory(fileCollectionFactory: FileCollectionFactory?)

    val primaryOutput: File?

    fun getLibs(sourceSet: DependentSourceSet?): MutableCollection<NativeDependencySet?>?

    val dependentBinaries: MutableCollection<NativeLibraryBinary?>?

    /**
     * Adds some files to include as input to the link/assemble step of this binary.
     */
    fun binaryInputs(files: FileCollection?)

    val allResolutions: MutableCollection<NativeBinaryRequirementResolveResult?>?

    val prefixFileToPCH: MutableMap<File?, PreCompiledHeader?>?

    fun addPreCompiledHeaderFor(sourceSet: DependentSourceSet?)
}
