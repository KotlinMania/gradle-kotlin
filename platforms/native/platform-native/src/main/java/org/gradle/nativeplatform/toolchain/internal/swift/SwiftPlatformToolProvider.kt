/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal.swift

import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.language.base.internal.compile.CompileSpec
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.base.internal.compile.CompilerUtil
import org.gradle.language.base.internal.compile.DefaultCompilerVersion
import org.gradle.language.base.internal.compile.VersionAwareCompiler
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.internal.LinkerSpec
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal
import org.gradle.nativeplatform.toolchain.SwiftcPlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.AbstractPlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker
import org.gradle.nativeplatform.toolchain.internal.DefaultCommandLineToolInvocationWorker
import org.gradle.nativeplatform.toolchain.internal.DefaultMutableCommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.EmptySystemLibraries
import org.gradle.nativeplatform.toolchain.internal.MutableCommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.Stripper
import org.gradle.nativeplatform.toolchain.internal.SymbolExtractor
import org.gradle.nativeplatform.toolchain.internal.SymbolExtractorOsConfig
import org.gradle.nativeplatform.toolchain.internal.SystemLibraries
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.compilespec.SwiftCompileSpec
import org.gradle.nativeplatform.toolchain.internal.gcc.ArStaticLibraryArchiver
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetadata
import org.gradle.nativeplatform.toolchain.internal.swift.metadata.SwiftcMetadata
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolConfigurationInternal
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.process.internal.ExecActionFactory

internal class SwiftPlatformToolProvider(
    buildOperationExecutor: BuildOperationExecutor?,
    targetOperatingSystem: OperatingSystemInternal,
    private val toolSearchPath: ToolSearchPath,
    private val toolRegistry: SwiftcPlatformToolChain,
    private val execActionFactory: ExecActionFactory,
    private val compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory?,
    private val workerLeaseService: WorkerLeaseService?,
    private val swiftcMetaData: SwiftcMetadata
) : AbstractPlatformToolProvider(buildOperationExecutor, targetOperatingSystem) {
    override fun locateTool(compilerType: ToolType?): CommandLineToolSearchResult? {
        if (compilerType == ToolType.SWIFT_COMPILER || compilerType == ToolType.LINKER) {
            return toolSearchPath.locate(compilerType, "swiftc")
        }
        if (compilerType == ToolType.STATIC_LIB_ARCHIVER) {
            return toolSearchPath.locate(compilerType, "ar")
        }
        if (compilerType == ToolType.SYMBOL_EXTRACTOR) {
            return toolSearchPath.locate(compilerType, SymbolExtractorOsConfig.current().executableName!!)
        }
        if (compilerType == ToolType.STRIPPER) {
            return toolSearchPath.locate(compilerType, "strip")
        }
        throw IllegalArgumentException()
    }

    public override fun <T : CompileSpec?> newCompiler(spec: Class<T?>): Compiler<T?>? {
        if (SwiftCompileSpec::class.java.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler<T?>(createSwiftCompiler())
        }
        return super.newCompiler<T?>(spec)
    }

    override fun createLinker(): Compiler<LinkerSpec?>? {
        val linkerTool = toolRegistry.linker as CommandLineToolConfigurationInternal
        val swiftLinker = SwiftLinker(buildOperationExecutor, commandLineTool(ToolType.LINKER, "swiftc"), context(linkerTool), workerLeaseService)
        return VersionAwareCompiler<LinkerSpec?>(swiftLinker, DefaultCompilerVersion("swiftc", swiftcMetaData.vendor, swiftcMetaData.version))
    }

    protected fun createSwiftCompiler(): Compiler<SwiftCompileSpec?> {
        val swiftCompilerTool = toolRegistry.swiftCompiler as CommandLineToolConfigurationInternal
        val swiftCompiler = SwiftCompiler(
            buildOperationExecutor,
            compilerOutputFileNamingSchemeFactory,
            commandLineTool(ToolType.SWIFT_COMPILER, "swiftc"),
            context(swiftCompilerTool),
            objectFileExtension,
            workerLeaseService,
            swiftcMetaData.version
        )
        return VersionAwareCompiler<SwiftCompileSpec?>(swiftCompiler, DefaultCompilerVersion("swiftc", swiftcMetaData.vendor, swiftcMetaData.version))
    }

    override fun createStaticLibraryArchiver(): Compiler<StaticLibraryArchiverSpec?>? {
        val staticLibArchiverTool = toolRegistry.staticLibArchiver as CommandLineToolConfigurationInternal
        return ArStaticLibraryArchiver(buildOperationExecutor, commandLineTool(ToolType.STATIC_LIB_ARCHIVER, "ar"), context(staticLibArchiverTool), workerLeaseService)
    }

    override fun createSymbolExtractor(): Compiler<*>? {
        val symbolExtractor = toolRegistry.symbolExtractor as CommandLineToolConfigurationInternal
        return SymbolExtractor(buildOperationExecutor, commandLineTool(ToolType.SYMBOL_EXTRACTOR, SymbolExtractorOsConfig.current().executableName!!), context(symbolExtractor), workerLeaseService)
    }

    override fun createStripper(): Compiler<*>? {
        val stripper = toolRegistry.stripper as CommandLineToolConfigurationInternal
        return Stripper(buildOperationExecutor, commandLineTool(ToolType.STRIPPER, "strip"), context(stripper), workerLeaseService)
    }

    private fun commandLineTool(key: ToolType, exeName: String): CommandLineToolInvocationWorker {
        return DefaultCommandLineToolInvocationWorker(key.toolName!!, toolSearchPath.locate(key, exeName).tool, execActionFactory)
    }

    private fun context(toolConfiguration: CommandLineToolConfigurationInternal): CommandLineToolContext {
        val baseInvocation: MutableCommandLineToolContext = DefaultMutableCommandLineToolContext()
        baseInvocation.setArgAction(toolConfiguration.argAction)

        val developerDir = System.getenv("DEVELOPER_DIR")
        if (developerDir != null) {
            baseInvocation.addEnvironmentVar("DEVELOPER_DIR", developerDir)
        }
        return baseInvocation
    }

    override fun getSystemLibraries(compilerType: ToolType?): SystemLibraries? {
        return EmptySystemLibraries()
    }

    public override fun getCompilerMetadata(compilerType: ToolType?): CompilerMetadata {
        return swiftcMetaData
    }
}
