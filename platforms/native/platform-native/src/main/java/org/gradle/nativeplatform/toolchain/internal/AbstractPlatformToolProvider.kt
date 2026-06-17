/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.internal.FileUtils
import org.gradle.internal.logging.text.DiagnosticsVisitor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.language.base.internal.compile.CompileSpec
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.base.internal.compile.CompilerUtil
import org.gradle.nativeplatform.internal.LinkerSpec
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec
import org.gradle.nativeplatform.internal.StripperSpec
import org.gradle.nativeplatform.internal.SymbolExtractorSpec
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal
import org.gradle.nativeplatform.toolchain.internal.compilespec.AssembleSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.CCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.CPCHCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppPCHCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCPCHCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCppCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCppPCHCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.WindowsResourceCompileSpec
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetadata

abstract class AbstractPlatformToolProvider(@JvmField protected val buildOperationExecutor: BuildOperationExecutor?, protected val targetOperatingSystem: OperatingSystemInternal) : PlatformToolProvider {
    val isAvailable: Boolean
        get() = true

    val isSupported: Boolean
        get() = true

    override fun explain(visitor: DiagnosticsVisitor?) {
    }

    override fun getExecutableName(executablePath: String?): String? {
        return targetOperatingSystem.internalOs.getExecutableName(executablePath)
    }

    override fun getSharedLibraryName(libraryPath: String?): String? {
        return targetOperatingSystem.internalOs.getSharedLibraryName(libraryPath)
    }

    override fun producesImportLibrary(): Boolean {
        return false
    }

    override fun requiresDebugBinaryStripping(): Boolean {
        return true
    }

    override fun getImportLibraryName(libraryPath: String?): String? {
        return getSharedLibraryLinkFileName(libraryPath)
    }

    override fun getSharedLibraryLinkFileName(libraryPath: String?): String? {
        return targetOperatingSystem.internalOs.getSharedLibraryName(libraryPath)
    }

    override fun getStaticLibraryName(libraryPath: String?): String? {
        return targetOperatingSystem.internalOs.getStaticLibraryName(libraryPath)
    }

    override fun getExecutableSymbolFileName(libraryPath: String?): String? {
        return FileUtils.withExtension(getExecutableName(libraryPath)!!, SymbolExtractorOsConfig.current().extension!!)
    }

    override fun getLibrarySymbolFileName(libraryPath: String?): String? {
        return FileUtils.withExtension(getSharedLibraryName(libraryPath)!!, SymbolExtractorOsConfig.current().extension!!)
    }

    override fun <T> get(toolType: Class<T?>): T? {
        throw IllegalArgumentException(String.format("Don't know how to provide tool of type %s.", toolType.getSimpleName()))
    }

    override fun <T : CompileSpec?> newCompiler(spec: Class<T?>): Compiler<T?>? {
        if (CppCompileSpec::class.java.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler<T?>(createCppCompiler())
        }
        if (CppPCHCompileSpec::class.java.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler<T?>(createCppPCHCompiler())
        }
        if (CCompileSpec::class.java.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler<T?>(createCCompiler())
        }
        if (CPCHCompileSpec::class.java.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler<T?>(createCPCHCompiler())
        }
        if (ObjectiveCppCompileSpec::class.java.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler<T?>(createObjectiveCppCompiler())
        }
        if (ObjectiveCppPCHCompileSpec::class.java.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler<T?>(createObjectiveCppPCHCompiler())
        }
        if (ObjectiveCCompileSpec::class.java.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler<T?>(createObjectiveCCompiler())
        }
        if (ObjectiveCPCHCompileSpec::class.java.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler<T?>(createObjectiveCPCHCompiler())
        }
        if (WindowsResourceCompileSpec::class.java.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler<T?>(createWindowsResourceCompiler())
        }
        if (AssembleSpec::class.java.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler<T?>(createAssembler())
        }
        if (LinkerSpec::class.java.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler<T?>(createLinker())
        }
        if (StaticLibraryArchiverSpec::class.java.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler<T?>(createStaticLibraryArchiver())
        }
        if (SymbolExtractorSpec::class.java.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler<T?>(createSymbolExtractor())
        }
        if (StripperSpec::class.java.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler<T?>(createStripper())
        }
        throw IllegalArgumentException(String.format("Don't know how to compile from a spec of type %s.", spec.getSimpleName()))
    }

    protected fun unavailableTool(message: String?): RuntimeException {
        return RuntimeException(message)
    }

    protected open fun createCppCompiler(): Compiler<*>? {
        throw unavailableTool("C++ compiler is not available")
    }

    protected open fun createCppPCHCompiler(): Compiler<*>? {
        throw unavailableTool("C++ pre-compiled header compiler is not available")
    }

    protected open fun createCCompiler(): Compiler<*>? {
        throw unavailableTool("C compiler is not available")
    }

    protected open fun createCPCHCompiler(): Compiler<*>? {
        throw unavailableTool("C pre-compiled header compiler is not available")
    }

    protected open fun createObjectiveCppCompiler(): Compiler<*>? {
        throw unavailableTool("Objective-C++ compiler is not available")
    }

    protected open fun createObjectiveCppPCHCompiler(): Compiler<*>? {
        throw unavailableTool("Objective-C++ pre-compiled header compiler is not available")
    }

    protected open fun createObjectiveCCompiler(): Compiler<*>? {
        throw unavailableTool("Objective-C compiler is not available")
    }

    protected open fun createObjectiveCPCHCompiler(): Compiler<*>? {
        throw unavailableTool("Objective-C compiler is not available")
    }

    protected open fun createWindowsResourceCompiler(): Compiler<*>? {
        throw unavailableTool("Windows resource compiler is not available")
    }

    protected open fun createAssembler(): Compiler<*>? {
        throw unavailableTool("Assembler is not available")
    }

    protected open fun createLinker(): Compiler<*>? {
        throw unavailableTool("Linker is not available")
    }

    protected open fun createStaticLibraryArchiver(): Compiler<*>? {
        throw unavailableTool("Static library archiver is not available")
    }

    protected open fun createSymbolExtractor(): Compiler<*>? {
        throw unavailableTool("Symbol extractor is not available")
    }

    protected open fun createStripper(): Compiler<*>? {
        throw unavailableTool("Stripper is not available")
    }

    val objectFileExtension: String
        get() = if (targetOperatingSystem.isWindows) ".obj" else ".o"

    override fun getCompilerMetadata(compilerType: ToolType?): CompilerMetadata? {
        throw unavailableTool("Compiler is not available")
    }
}
