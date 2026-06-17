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
package org.gradle.nativeplatform.toolchain.internal.msvcpp

import com.google.common.collect.ImmutableList
import org.gradle.api.Transformer
import org.gradle.internal.FileUtils
import org.gradle.internal.Transformers
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.logging.text.DiagnosticsVisitor
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
import org.gradle.nativeplatform.toolchain.internal.MutableCommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec
import org.gradle.nativeplatform.toolchain.internal.OutputCleaningCompiler
import org.gradle.nativeplatform.toolchain.internal.PCHUtils.getHeaderToSourceFileTransformer
import org.gradle.nativeplatform.toolchain.internal.SystemLibraries
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.compilespec.AssembleSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.CCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.CPCHCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppPCHCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.WindowsResourceCompileSpec
import org.gradle.nativeplatform.toolchain.internal.msvcpp.metadata.VisualCppMetadata
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolConfigurationInternal
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.internal.VersionNumber
import org.jspecify.annotations.NullMarked
import java.io.File

@NullMarked
internal class VisualCppPlatformToolProvider(
    buildOperationExecutor: BuildOperationExecutor,
    operatingSystem: OperatingSystemInternal,
    private val commandLineToolConfigurations: MutableMap<ToolType, CommandLineToolConfigurationInternal>,
    private val visualStudio: VisualStudioInstall,
    private val visualCpp: VisualCpp,
    private val sdk: WindowsSdk,
    ucrt: SystemLibraries,
    private val execActionFactory: ExecActionFactory,
    private val compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
    private val workerLeaseService: WorkerLeaseService
) : AbstractPlatformToolProvider(buildOperationExecutor, operatingSystem) {
    private val libraries: WindowsSdkLibraries

    init {
        this.libraries = CompositeLibraries(visualCpp, sdk, ucrt)
    }

    public override fun producesImportLibrary(): Boolean {
        return true
    }

    public override fun requiresDebugBinaryStripping(): Boolean {
        return false
    }

    public override fun getSharedLibraryLinkFileName(libraryName: String): String {
        return FileUtils.withExtension(getSharedLibraryName(libraryName)!!, ".lib")
    }

    override fun locateTool(compilerType: ToolType): CommandLineToolSearchResult {
        when (compilerType) {
            ToolType.C_COMPILER, ToolType.CPP_COMPILER -> return object : CommandLineToolSearchResult {
                val tool: File
                    get() = visualCpp.compilerExecutable

                val isAvailable: Boolean
                    get() = true

                override fun explain(visitor: DiagnosticsVisitor) {
                }
            }

            else -> throw UnsupportedOperationException()
        }
    }

    override fun createCppCompiler(): Compiler<CppCompileSpec> {
        val commandLineTool = tool("C++ compiler", visualCpp.compilerExecutable)
        val cppCompiler: CppCompiler = (CppCompiler(
            buildOperationExecutor,
            compilerOutputFileNamingSchemeFactory,
            commandLineTool,
            context(commandLineToolConfigurations.get(ToolType.CPP_COMPILER)!!),
            TODO("Cannot convert element")
        ))<CppCompileSpec> addDefinitions < org . gradle . nativeplatform . toolchain . internal . NativeCompileSpec >()
        val getObjectFileExtension: CppCompiler
        TODO(
            """
            |Cannot convert element
            |With text:
            |(), true, workerLeaseService
            """.trimMargin()
        )

        val outputCleaningCompiler = OutputCleaningCompiler<CppCompileSpec?>(cppCompiler, compilerOutputFileNamingSchemeFactory, objectFileExtension)
        return
        CppCompileSpec > versionAwareCompiler<CppCompileSpec>(outputCleaningCompiler)
    }

    override fun createCppPCHCompiler(): Compiler<*> {
        val commandLineTool = tool("C++ PCH compiler", visualCpp.compilerExecutable)
        val cppPCHCompiler: CppPCHCompiler = (CppPCHCompiler(
            buildOperationExecutor,
            compilerOutputFileNamingSchemeFactory,
            commandLineTool,
            context(commandLineToolConfigurations.get(ToolType.CPP_COMPILER)!!),
            TODO("Cannot convert element")
        ))<CppPCHCompileSpec> pchSpecTransforms < org . gradle . nativeplatform . toolchain . internal . compilespec . CppPCHCompileSpec >(
            CppPCHCompileSpec::class.java
        )
        val getPCHFileExtension: CppPCHCompiler
        TODO(
            """
            |Cannot convert element
            |With text:
            |(), true, workerLeaseService
            """.trimMargin()
        )

        val outputCleaningCompiler = OutputCleaningCompiler<CppPCHCompileSpec?>(
            cppPCHCompiler, compilerOutputFileNamingSchemeFactory,
            this.pCHFileExtension
        )
        return
        CppPCHCompileSpec > versionAwareCompiler<CppPCHCompileSpec>(outputCleaningCompiler)
    }

    override fun createCCompiler(): Compiler<CCompileSpec> {
        val commandLineTool = tool("C compiler", visualCpp.compilerExecutable)
        val cCompiler: CCompiler = (CCompiler(
            buildOperationExecutor,
            compilerOutputFileNamingSchemeFactory,
            commandLineTool,
            context(commandLineToolConfigurations.get(ToolType.C_COMPILER)!!),
            TODO("Cannot convert element")
        ))<CCompileSpec> addDefinitions < org . gradle . nativeplatform . toolchain . internal . NativeCompileSpec >()
        val getObjectFileExtension: CCompiler
        TODO(
            """
            |Cannot convert element
            |With text:
            |(), true, workerLeaseService
            """.trimMargin()
        )

        val outputCleaningCompiler = OutputCleaningCompiler<CCompileSpec?>(cCompiler, compilerOutputFileNamingSchemeFactory, objectFileExtension)
        return
        CCompileSpec > versionAwareCompiler<CCompileSpec>(outputCleaningCompiler)
    }

    override fun createCPCHCompiler(): Compiler<*> {
        val commandLineTool = tool("C PCH compiler", visualCpp.compilerExecutable)
        val cpchCompiler: CPCHCompiler = (CPCHCompiler(
            buildOperationExecutor,
            compilerOutputFileNamingSchemeFactory,
            commandLineTool,
            context(commandLineToolConfigurations.get(ToolType.C_COMPILER)!!),
            TODO("Cannot convert element")
        ))<CPCHCompileSpec> pchSpecTransforms < org . gradle . nativeplatform . toolchain . internal . compilespec . CPCHCompileSpec >(
            CPCHCompileSpec::class.java
        )
        val getPCHFileExtension: CPCHCompiler
        TODO(
            """
            |Cannot convert element
            |With text:
            |(), true, workerLeaseService
            """.trimMargin()
        )

        val outputCleaningCompiler = OutputCleaningCompiler<CPCHCompileSpec?>(
            cpchCompiler, compilerOutputFileNamingSchemeFactory,
            this.pCHFileExtension
        )
        return
        CPCHCompileSpec > versionAwareCompiler<CPCHCompileSpec>(outputCleaningCompiler)
    }

    private fun <T : BinaryToolSpec?> versionAwareCompiler(outputCleaningCompiler: Compiler<T?>): VersionAwareCompiler<T?> {
        return VersionAwareCompiler<T?>(outputCleaningCompiler, DefaultCompilerVersion(VisualCppToolChain.Companion.DEFAULT_NAME, "Microsoft", visualCpp.implementationVersion))
    }

    override fun createAssembler(): Compiler<AssembleSpec> {
        val commandLineTool = tool("Assembler", visualCpp.assemblerExecutable)
        return (Assembler(
            buildOperationExecutor,
            compilerOutputFileNamingSchemeFactory,
            commandLineTool,
            context(commandLineToolConfigurations.get(ToolType.ASSEMBLER)!!),
            TODO("Cannot convert element")
        ))<AssembleSpec> addDefinitions < org . gradle . nativeplatform . toolchain . internal . NativeCompileSpec >()
        TODO(
            """
            |Cannot convert element
            |With text:
            |getObjectFileExtension(), false, workerLeaseService
            """.trimMargin()
        )
    }

    override fun createObjectiveCppCompiler(): Compiler<*>? {
        throw unavailableTool("Objective-C++ is not available on the Visual C++ toolchain")
    }

    override fun createObjectiveCCompiler(): Compiler<*>? {
        throw unavailableTool("Objective-C is not available on the Visual C++ toolchain")
    }

    override fun createWindowsResourceCompiler(): Compiler<WindowsResourceCompileSpec?> {
        val commandLineTool = tool("Windows resource compiler", sdk.getResourceCompiler())
        val objectFileExtension = ".res"
        val windowsResourceCompiler: WindowsResourceCompiler = (WindowsResourceCompiler(
            buildOperationExecutor,
            compilerOutputFileNamingSchemeFactory,
            commandLineTool,
            context(commandLineToolConfigurations.get(ToolType.WINDOW_RESOURCES_COMPILER)!!),
            TODO("Cannot convert element")
        ))<WindowsResourceCompileSpec> addDefinitions < org . gradle . nativeplatform . toolchain . internal . NativeCompileSpec >()
        val objectFileExtension: WindowsResourceCompiler
        TODO(
            """
            |Cannot convert element
            |With text:
            |false, workerLeaseService
            """.trimMargin()
        )

        return OutputCleaningCompiler<WindowsResourceCompileSpec?>(windowsResourceCompiler, compilerOutputFileNamingSchemeFactory, objectFileExtension)
    }

    override fun createLinker(): Compiler<LinkerSpec> {
        val commandLineTool = tool("Linker", visualCpp.linkerExecutable)
        return
        LinkerSpec > versionAwareCompiler<LinkerSpec>(
            LinkExeLinker(
                buildOperationExecutor,
                commandLineTool,
                context(commandLineToolConfigurations.get(ToolType.LINKER)!!),
                addLibraryPath(),
                workerLeaseService
            )
        )
    }

    override fun createStaticLibraryArchiver(): Compiler<StaticLibraryArchiverSpec> {
        val commandLineTool = tool("Static library archiver", visualCpp.archiverExecutable)
        return LibExeStaticLibraryArchiver(
            buildOperationExecutor,
            commandLineTool,
            context(commandLineToolConfigurations.get(ToolType.STATIC_LIB_ARCHIVER)!!),
            Transformers.noOpTransformer<StaticLibraryArchiverSpec>(),
            workerLeaseService
        )
    }

    private fun tool(toolName: String, exe: File): CommandLineToolInvocationWorker {
        return DefaultCommandLineToolInvocationWorker(toolName, exe, execActionFactory)
    }

    private fun context(commandLineToolConfiguration: CommandLineToolConfigurationInternal): CommandLineToolContext {
        val invocationContext: MutableCommandLineToolContext = DefaultMutableCommandLineToolContext()
        // The visual C++ tools use the path to find other executables
        // TODO:ADAM - restrict this to the specific path for the target tool
        invocationContext.addPath(visualCpp.path)
        invocationContext.addPath(sdk.path)
        // Clear environment variables that might effect cl.exe & link.exe
        clearEnvironmentVars(invocationContext, "INCLUDE", "CL", "LIBPATH", "LINK", "LIB")

        invocationContext.setArgAction(commandLineToolConfiguration.argAction)
        return invocationContext
    }

    private fun clearEnvironmentVars(invocation: MutableCommandLineToolContext, vararg names: String) {
        // TODO: This check should really be done in the compiler process
        val environmentVariables = Jvm.getInheritableEnvironmentVariables(System.getenv())
        for (name in names) {
            val value: Any? = environmentVariables.get(name)
            if (value != null) {
                VisualCppToolChain.Companion.LOGGER.debug("Ignoring value '{}' set for environment variable '{}'.", value, name)
                invocation.addEnvironmentVar(name, "")
            }
        }
    }

    private fun <T : NativeCompileSpec?> pchSpecTransforms(type: Class<T?>): Transformer<T?, T?> {
        return object : Transformer<T?, T?> {
            override fun transform(original: T?): T? {
                val transformers: MutableList<Transformer<T?, T?>> = ArrayList<Transformer<T?, T?>>()
                transformers.add(getHeaderToSourceFileTransformer<T?>(type))
                transformers.add(TODO("Cannot convert element"))<T> addDefinitions < org . gradle . nativeplatform . toolchain . internal . NativeCompileSpec >()


                val next = original
                for (transformer in transformers) {
                    next = transformer.transform(next)
                }
                return next
            }
        }
    }

    override fun getSystemLibraries(compilerType: ToolType): WindowsSdkLibraries {
        return libraries
    }

    private fun <T : NativeCompileSpec?> addDefinitions(): Transformer<T?, T?> {
        return object : Transformer<T?, T?> {
            override fun transform(original: T?): T? {
                for (definition in libraries.preprocessorMacros.entrySet()) {
                    original!!.define(definition.key, definition.value)
                }
                return original
            }
        }
    }

    private fun addLibraryPath(): Transformer<LinkerSpec, LinkerSpec> {
        return object : Transformer<LinkerSpec, LinkerSpec> {
            override fun transform(original: LinkerSpec): LinkerSpec {
                original.libraryPath(libraries.libDirs)
                return original
            }
        }
    }

    val pCHFileExtension: String
        get() = ".pch"

    public override fun getLibrarySymbolFileName(libraryPath: String): String {
        return FileUtils.withExtension(getSharedLibraryName(libraryPath)!!, ".pdb")
    }

    public override fun getExecutableSymbolFileName(executablePath: String): String {
        return FileUtils.withExtension(getExecutableName(executablePath)!!, ".pdb")
    }

    public override fun getCompilerMetadata(toolType: ToolType): VisualCppMetadata {
        return object : VisualCppMetadata {
            override fun getVisualStudioVersion(): VersionNumber {
                return visualStudio.getVersion()
            }

            override fun getVendor(): String {
                return "Microsoft"
            }

            override fun getVersion(): VersionNumber {
                return visualCpp.implementationVersion
            }
        }
    }

    private class CompositeLibraries(private val visualCpp: VisualCpp, private val sdk: WindowsSdk, private val ucrt: SystemLibraries) : WindowsSdkLibraries {
        override fun getSdkVersion(): VersionNumber {
            return sdk.getSdkVersion()
        }

        val includeDirs: MutableList<File>
            get() {
                val builder = ImmutableList.builder<File>()
                builder.addAll(visualCpp.includeDirs)
                builder.addAll(sdk.includeDirs)
                builder.addAll(ucrt.includeDirs)
                return builder.build()
            }

        val libDirs: MutableList<File>
            get() {
                val builder = ImmutableList.builder<File>()
                builder.addAll(visualCpp.libDirs)
                builder.addAll(sdk.libDirs)
                builder.addAll(ucrt.libDirs)
                return builder.build()
            }

        val preprocessorMacros: MutableMap<String, String>
            get() = visualCpp.preprocessorMacros
    }
}
