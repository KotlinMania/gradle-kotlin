/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal

import org.gradle.nativeplatform.internal.BinaryToolSpec
import java.io.File

/**
 * A compile spec that will be used to generate object files for combining into a native binary.
 */
interface NativeCompileSpec : BinaryToolSpec {
    @JvmField
    var objectFileDir: File?

    @JvmField
    val includeRoots: MutableList<File?>?

    fun include(includeRoots: Iterable<File?>?)

    fun include(vararg includeRoots: File?)

    @JvmField
    val systemIncludeRoots: MutableList<File?>?

    fun systemInclude(systemIncludeRoots: Iterable<File?>?)

    @JvmField
    var sourceFiles: MutableList<File?>?

    fun source(sources: Iterable<File?>?)

    @JvmField
    var removedSourceFiles: MutableList<File?>?

    fun removedSource(sources: Iterable<File?>?)

    @JvmField
    var macros: MutableMap<String?, String?>?

    fun define(name: String?)

    fun define(name: String?, value: String?)

    @JvmField
    var isPositionIndependentCode: Boolean

    @JvmField
    var isDebuggable: Boolean

    @JvmField
    var isOptimized: Boolean

    @JvmField
    var isIncrementalCompile: Boolean

    @JvmField
    var prefixHeaderFile: File?

    @JvmField
    var preCompiledHeaderObjectFile: File?

    @JvmField
    var preCompiledHeader: String?

    var sourceFilesForPch: MutableList<File?>?
}
