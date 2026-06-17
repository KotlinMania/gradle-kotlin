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
package org.gradle.nativeplatform

import org.gradle.api.Incubating
import org.gradle.internal.HasInternalProtocol
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.Variant

/**
 * Represents a binary artifact that is the result of building a native component.
 */
@Incubating
@HasInternalProtocol
interface NativeBinarySpec : BinarySpec {
    /**
     * The component that this binary was built from.
     */
    val component: NativeComponentSpec?

    @get:Variant
    val flavor: Flavor?

    @get:Variant
    val targetPlatform: NativePlatform?

    @get:Variant
    val buildType: BuildType?

    /**
     * The libraries that should be linked into this binary.
     */
    val libs: MutableCollection<NativeDependencySet?>?

    /**
     * Adds a library as input to this binary.
     *
     *
     * This method accepts the following types:
     *
     *
     *  * A [NativeLibrarySpec]
     *  * A [NativeDependencySet]
     *  * A [java.util.Map] containing the library selector.
     *
     *
     * The Map notation supports the following String attributes:
     *
     *
     *  * project: the path to the project containing the library (optional, defaults to current project)
     *  * library: the name of the library (required)
     *  * linkage: the library linkage required ['shared'/'static'] (optional, defaults to 'shared')
     *
     */
    fun lib(library: Any?)

    /**
     * Returns the [org.gradle.nativeplatform.toolchain.NativeToolChain] that will be used to build this binary.
     */
    val toolChain: NativeToolChain?

    // TODO It would be better if these were added via a separate managed view, rather than hard coded.
    /**
     * The configuration of the linker used when linking this binary.
     *
     * Valid for [SharedLibraryBinarySpec] and [NativeExecutableBinarySpec].
     */
    val linker: Tool?

    /**
     * The configuration of the static library archiver used when creating this binary.
     *
     * Valid for [StaticLibraryBinarySpec].
     */
    val staticLibArchiver: Tool?

    /**
     * The configuration of the assembler used when compiling assembly sources this binary.
     *
     * Valid for [SharedLibraryBinarySpec], [StaticLibraryBinarySpec] and
     * [NativeExecutableBinarySpec] when the 'assembler' plugin is applied.
     */
    val assembler: Tool?

    /**
     * The configuration of the C compiler used when compiling C sources for this binary.
     *
     * Valid for [SharedLibraryBinarySpec], [StaticLibraryBinarySpec] and
     * [NativeExecutableBinarySpec] when the 'c' plugin is applied.
     */
    fun getcCompiler(): PreprocessingTool?

    /**
     * The configuration of the C++ compiler used when compiling C++ sources for this binary.
     *
     * Valid for [SharedLibraryBinarySpec], [StaticLibraryBinarySpec] and
     * [NativeExecutableBinarySpec] when the 'cpp' plugin is applied.
     */
    val cppCompiler: PreprocessingTool?

    /**
     * The configuration of the Objective-C compiler used when compiling Objective-C sources for this binary.
     *
     * Valid for [SharedLibraryBinarySpec], [StaticLibraryBinarySpec] and
     * [NativeExecutableBinarySpec] when the 'objective-c' plugin is applied.
     */
    val objcCompiler: PreprocessingTool?

    /**
     * The configuration of the Objective-C++ compiler used when compiling Objective-C++ sources for this binary.
     *
     * Valid for [SharedLibraryBinarySpec], [StaticLibraryBinarySpec] and
     * [NativeExecutableBinarySpec] when the 'objective-cpp' plugin is applied.
     */
    val objcppCompiler: PreprocessingTool?

    /**
     * The configuration of the Resource compiler used when compiling resources for this binary.
     *
     * Valid for [SharedLibraryBinarySpec], [StaticLibraryBinarySpec] and
     * [NativeExecutableBinarySpec] when the 'windows-resources' plugin is applied.
     */
    val rcCompiler: PreprocessingTool?
}
