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

import org.gradle.api.GradleException
import org.gradle.internal.FileUtils
import org.gradle.internal.logging.text.DiagnosticsVisitor
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.language.base.internal.compile.CompileSpec
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetadata
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult
import org.gradle.platform.base.internal.toolchain.ToolSearchResult
import java.io.File

open class UnavailablePlatformToolProvider(private val targetOperatingSystem: OperatingSystemInternal, private val failure: ToolSearchResult) : PlatformToolProvider, CommandLineToolSearchResult {
    val isAvailable: Boolean
        get() = false

    override fun isSupported(): Boolean {
        return true
    }

    override fun explain(visitor: DiagnosticsVisitor) {
        failure.explain(visitor)
    }

    private fun failure(): RuntimeException {
        val formatter = TreeFormatter()
        this.explain(formatter)
        return GradleException(formatter.toString())
    }

    override fun requiresDebugBinaryStripping(): Boolean {
        // Doesn't really make sense
        return true
    }

    override fun getObjectFileExtension(): String? {
        throw failure()
    }

    override fun getExecutableName(executablePath: String?): String {
        return targetOperatingSystem.internalOs.getExecutableName(executablePath)
    }

    override fun getSharedLibraryName(libraryPath: String?): String? {
        return targetOperatingSystem.internalOs.getSharedLibraryName(libraryPath)
    }

    override fun producesImportLibrary(): Boolean {
        // Doesn't really make sense
        return targetOperatingSystem.isWindows
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

    override fun getLibrarySymbolFileName(libraryPath: String?): String {
        return FileUtils.withExtension(getSharedLibraryName(libraryPath)!!, SymbolExtractorOsConfig.Companion.current().getExtension())
    }

    override fun getExecutableSymbolFileName(executablePath: String?): String {
        return FileUtils.withExtension(getExecutableName(executablePath), SymbolExtractorOsConfig.Companion.current().getExtension())
    }

    override fun <T> get(toolType: Class<T?>): T? {
        throw IllegalArgumentException(String.format("Don't know how to provide tool of type %s.", toolType.getSimpleName()))
    }

    override fun <T : CompileSpec?> newCompiler(specType: Class<T?>?): Compiler<T?>? {
        throw failure()
    }

    override fun locateTool(compilerType: ToolType?): CommandLineToolSearchResult {
        return this
    }

    override fun getTool(): File? {
        throw UnsupportedOperationException()
    }

    override fun getSystemLibraries(compilerType: ToolType?): SystemLibraries {
        return EmptySystemLibraries()
    }

    override fun getCompilerMetadata(compilerType: ToolType?): CompilerMetadata? {
        throw failure()
    }
}
