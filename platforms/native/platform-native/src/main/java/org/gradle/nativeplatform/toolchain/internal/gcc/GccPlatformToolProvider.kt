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
package org.gradle.nativeplatform.toolchain.internal.gcc

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.api.Action
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.base.internal.compile.DefaultCompilerVersion
import org.gradle.language.base.internal.compile.VersionAwareCompiler
import org.gradle.nativeplatform.internal.BinaryToolSpec
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.internal.LinkerSpec
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal
import org.gradle.nativeplatform.toolchain.internal.AbstractPlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker
import org.gradle.nativeplatform.toolchain.internal.DefaultCommandLineToolInvocationWorker
import org.gradle.nativeplatform.toolchain.internal.DefaultMutableCommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.EmptySystemLibraries
import org.gradle.nativeplatform.toolchain.internal.MutableCommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.OutputCleaningCompiler
import org.gradle.nativeplatform.toolchain.internal.Stripper
import org.gradle.nativeplatform.toolchain.internal.SymbolExtractor
import org.gradle.nativeplatform.toolchain.internal.SystemLibraries
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.compilespec.AssembleSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.CCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.CPCHCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppPCHCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCPCHCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCppCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCppPCHCompileSpec
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadata
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProvider
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetadata
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal
import org.gradle.nativeplatform.toolchain.internal.tools.ToolRegistry
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.platform.base.internal.toolchain.ComponentNotFound
import org.gradle.platform.base.internal.toolchain.SearchResult
import org.gradle.process.internal.ExecActionFactory

internal class GccPlatformToolProvider(
    buildOperationExecutor: BuildOperationExecutor?,
    targetOperatingSystem: OperatingSystemInternal,
    private val toolSearchPath: ToolSearchPath,
    private val toolRegistry: ToolRegistry,
    private val execActionFactory: ExecActionFactory,
    private val compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
    private val useCommandFile: Boolean,
    private val workerLeaseService: WorkerLeaseService?,
    private val metadataProvider: CompilerMetaDataProvider<GccMetadata?>
) : AbstractPlatformToolProvider(buildOperationExecutor, targetOperatingSystem) {
    override fun locateTool(compilerType: ToolType): CommandLineToolSearchResult {
        return toolSearchPath.locate(compilerType, toolRegistry.getTool(compilerType)!!.executable!!)
    }

    override fun createCppCompiler(): Compiler<CppCompileSpec?> {
        val cppCompilerTool = toolRegistry.getTool(ToolType.CPP_COMPILER)
        val cppCompiler = CppCompiler(
            buildOperationExecutor,
            compilerOutputFileNamingSchemeFactory,
            commandLineTool(cppCompilerTool!!),
            context(cppCompilerTool),
            objectFileExtension,
            useCommandFile,
            workerLeaseService
        )
        val outputCleaningCompiler = OutputCleaningCompiler<CppCompileSpec?>(cppCompiler, compilerOutputFileNamingSchemeFactory, objectFileExtension)
        return
        CppCompileSpec > versionAwareCompiler<CppCompileSpec?>(outputCleaningCompiler, ToolType.CPP_COMPILER)
    }

    override fun createCppPCHCompiler(): Compiler<*> {
        val cppCompilerTool = toolRegistry.getTool(ToolType.CPP_COMPILER)
        val cppPCHCompiler = CppPCHCompiler(
            buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(cppCompilerTool!!), context(cppCompilerTool),
            this.pCHFileExtension, useCommandFile, workerLeaseService
        )
        val outputCleaningCompiler = OutputCleaningCompiler<CppPCHCompileSpec?>(
            cppPCHCompiler, compilerOutputFileNamingSchemeFactory,
            this.pCHFileExtension
        )
        return
        CppPCHCompileSpec > versionAwareCompiler<CppPCHCompileSpec?>(outputCleaningCompiler, ToolType.CPP_COMPILER)
    }

    private fun <T : BinaryToolSpec?> versionAwareCompiler(compiler: Compiler<T?>?, toolType: ToolType): VersionAwareCompiler<T?> {
        val gccMetadata = getGccMetadata(toolType)
        return VersionAwareCompiler<T?>(
            compiler, DefaultCompilerVersion(
                metadataProvider.getCompilerType().getIdentifier(),
                gccMetadata.component!!.getVendor(),
                gccMetadata.component!!.getVersion()
            )
        )
    }

    override fun createCCompiler(): Compiler<CCompileSpec?> {
        val cCompilerTool = toolRegistry.getTool(ToolType.C_COMPILER)
        val cCompiler = CCompiler(
            buildOperationExecutor,
            compilerOutputFileNamingSchemeFactory,
            commandLineTool(cCompilerTool!!),
            context(cCompilerTool),
            objectFileExtension,
            useCommandFile,
            workerLeaseService
        )
        val outputCleaningCompiler = OutputCleaningCompiler<CCompileSpec?>(cCompiler, compilerOutputFileNamingSchemeFactory, objectFileExtension)
        return
        CCompileSpec > versionAwareCompiler<CCompileSpec?>(outputCleaningCompiler, ToolType.C_COMPILER)
    }

    override fun createCPCHCompiler(): Compiler<*> {
        val cCompilerTool = toolRegistry.getTool(ToolType.C_COMPILER)
        val cpchCompiler = CPCHCompiler(
            buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(cCompilerTool!!), context(cCompilerTool),
            this.pCHFileExtension, useCommandFile, workerLeaseService
        )
        val outputCleaningCompiler = OutputCleaningCompiler<CPCHCompileSpec?>(
            cpchCompiler, compilerOutputFileNamingSchemeFactory,
            this.pCHFileExtension
        )
        return
        CPCHCompileSpec > versionAwareCompiler<CPCHCompileSpec?>(outputCleaningCompiler, ToolType.C_COMPILER)
    }

    override fun createObjectiveCppCompiler(): Compiler<ObjectiveCppCompileSpec?> {
        val objectiveCppCompilerTool = toolRegistry.getTool(ToolType.OBJECTIVECPP_COMPILER)
        val objectiveCppCompiler = ObjectiveCppCompiler(
            buildOperationExecutor,
            compilerOutputFileNamingSchemeFactory,
            commandLineTool(objectiveCppCompilerTool!!),
            context(objectiveCppCompilerTool),
            objectFileExtension,
            useCommandFile,
            workerLeaseService
        )
        val outputCleaningCompiler = OutputCleaningCompiler<ObjectiveCppCompileSpec?>(objectiveCppCompiler, compilerOutputFileNamingSchemeFactory, objectFileExtension)
        return
        ObjectiveCppCompileSpec > versionAwareCompiler<ObjectiveCppCompileSpec?>(outputCleaningCompiler, ToolType.OBJECTIVECPP_COMPILER)
    }

    override fun createObjectiveCppPCHCompiler(): Compiler<*> {
        val objectiveCppCompilerTool = toolRegistry.getTool(ToolType.OBJECTIVECPP_COMPILER)
        val objectiveCppPCHCompiler = ObjectiveCppPCHCompiler(
            buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(objectiveCppCompilerTool!!), context(objectiveCppCompilerTool),
            this.pCHFileExtension, useCommandFile, workerLeaseService
        )
        val outputCleaningCompiler = OutputCleaningCompiler<ObjectiveCppPCHCompileSpec?>(
            objectiveCppPCHCompiler, compilerOutputFileNamingSchemeFactory,
            this.pCHFileExtension
        )
        return
        ObjectiveCppPCHCompileSpec > versionAwareCompiler<ObjectiveCppPCHCompileSpec?>(outputCleaningCompiler, ToolType.OBJECTIVECPP_COMPILER)
    }

    override fun createObjectiveCCompiler(): Compiler<ObjectiveCCompileSpec?> {
        val objectiveCCompilerTool = toolRegistry.getTool(ToolType.OBJECTIVEC_COMPILER)
        val objectiveCCompiler = ObjectiveCCompiler(
            buildOperationExecutor,
            compilerOutputFileNamingSchemeFactory,
            commandLineTool(objectiveCCompilerTool!!),
            context(objectiveCCompilerTool),
            objectFileExtension,
            useCommandFile,
            workerLeaseService
        )
        val outputCleaningCompiler = OutputCleaningCompiler<ObjectiveCCompileSpec?>(objectiveCCompiler, compilerOutputFileNamingSchemeFactory, objectFileExtension)
        return
        ObjectiveCCompileSpec > versionAwareCompiler<ObjectiveCCompileSpec?>(outputCleaningCompiler, ToolType.OBJECTIVEC_COMPILER)
    }

    override fun createObjectiveCPCHCompiler(): Compiler<*> {
        val objectiveCCompilerTool = toolRegistry.getTool(ToolType.OBJECTIVEC_COMPILER)
        val objectiveCPCHCompiler = ObjectiveCPCHCompiler(
            buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(objectiveCCompilerTool!!), context(objectiveCCompilerTool),
            this.pCHFileExtension, useCommandFile, workerLeaseService
        )
        val outputCleaningCompiler = OutputCleaningCompiler<ObjectiveCPCHCompileSpec?>(
            objectiveCPCHCompiler, compilerOutputFileNamingSchemeFactory,
            this.pCHFileExtension
        )
        return
        ObjectiveCPCHCompileSpec > versionAwareCompiler<ObjectiveCPCHCompileSpec?>(outputCleaningCompiler, ToolType.OBJECTIVEC_COMPILER)
    }

    override fun createAssembler(): Compiler<AssembleSpec?>? {
        val assemblerTool = toolRegistry.getTool(ToolType.ASSEMBLER)
        // Disable command line file for now because some custom assemblers
        // don't understand the same arguments as GCC.
        return Assembler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(assemblerTool!!), context(assemblerTool), objectFileExtension, false, workerLeaseService)
    }

    override fun createLinker(): Compiler<LinkerSpec?> {
        val linkerTool = toolRegistry.getTool(ToolType.LINKER)
        return
        LinkerSpec > versionAwareCompiler<LinkerSpec?>(GccLinker(buildOperationExecutor, commandLineTool(linkerTool!!), context(linkerTool), useCommandFile, workerLeaseService), ToolType.LINKER)
    }

    override fun createStaticLibraryArchiver(): Compiler<StaticLibraryArchiverSpec?>? {
        val staticLibArchiverTool = toolRegistry.getTool(ToolType.STATIC_LIB_ARCHIVER)
        return ArStaticLibraryArchiver(buildOperationExecutor, commandLineTool(staticLibArchiverTool!!), context(staticLibArchiverTool), workerLeaseService)
    }

    override fun createSymbolExtractor(): Compiler<*>? {
        val symbolExtractor = toolRegistry.getTool(ToolType.SYMBOL_EXTRACTOR)
        return SymbolExtractor(buildOperationExecutor, commandLineTool(symbolExtractor!!), context(symbolExtractor), workerLeaseService)
    }

    override fun createStripper(): Compiler<*>? {
        val stripper = toolRegistry.getTool(ToolType.STRIPPER)
        return Stripper(buildOperationExecutor, commandLineTool(stripper!!), context(stripper), workerLeaseService)
    }

    private fun commandLineTool(tool: GccCommandLineToolConfigurationInternal): CommandLineToolInvocationWorker {
        val key: ToolType = tool.toolType
        val exeName: String = tool.executable!!
        return DefaultCommandLineToolInvocationWorker(key.toolName!!, toolSearchPath.locate(key, exeName).tool, execActionFactory)
    }

    private fun context(toolConfiguration: GccCommandLineToolConfigurationInternal): CommandLineToolContext {
        val baseInvocation: MutableCommandLineToolContext = DefaultMutableCommandLineToolContext()
        // MinGW requires the path to be set
        baseInvocation.addPath(toolSearchPath.path)
        baseInvocation.addEnvironmentVar("CYGWIN", "nodosfilewarning")
        baseInvocation.setArgAction(toolConfiguration.argAction)

        val developerDir = System.getenv("DEVELOPER_DIR")
        if (developerDir != null) {
            baseInvocation.addEnvironmentVar("DEVELOPER_DIR", developerDir)
        }
        return baseInvocation
    }

    private val pCHFileExtension: String
        get() = ".h.gch"

    private fun getGccMetadata(compilerType: ToolType): SearchResult<GccMetadata?> {
        val compiler = toolRegistry.getTool(compilerType)
        if (compiler == null) {
            return ComponentNotFound<GccMetadata?>("Tool " + compilerType.toolName + " is not available")
        }
        val searchResult = toolSearchPath.locate(compiler.toolType, compiler.executable!!)
        val language: String? = LANGUAGE_FOR_COMPILER.get(compilerType)
        val languageArgs = if (language == null) mutableListOf<String?>() else ImmutableList.of<String?>("-x", language)

        return metadataProvider.getCompilerMetaData(toolSearchPath.path, Action { spec: CompilerMetaDataProvider.CompilerExecSpec? -> spec!!.executable(searchResult.tool).args(languageArgs) })
    }

    override fun getSystemLibraries(compilerType: ToolType): SystemLibraries? {
        val gccMetadata = getGccMetadata(compilerType)
        if (gccMetadata.isAvailable) {
            return gccMetadata.component!!.getSystemLibraries()
        }
        return EmptySystemLibraries()
    }

    public override fun getCompilerMetadata(toolType: ToolType): CompilerMetadata {
        return getGccMetadata(toolType).component!!
    }

    companion object {
        private val LANGUAGE_FOR_COMPILER: MutableMap<ToolType?, String?> = ImmutableMap.of<ToolType?, String?>(
            ToolType.C_COMPILER, "c",
            ToolType.CPP_COMPILER, "c++",
            ToolType.OBJECTIVEC_COMPILER, "objective-c",
            ToolType.OBJECTIVECPP_COMPILER, "objective-c++"
        )
    }
}
