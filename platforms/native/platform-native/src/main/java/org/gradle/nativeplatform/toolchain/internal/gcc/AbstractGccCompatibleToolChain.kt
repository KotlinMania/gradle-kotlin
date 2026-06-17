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

import org.gradle.api.Action
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.Actions
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.GccCompatibleToolChain
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain
import org.gradle.nativeplatform.toolchain.NativePlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.ExtendableToolChain
import org.gradle.nativeplatform.toolchain.internal.NativeLanguage
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.SymbolExtractorOsConfig
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.UnavailablePlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.UnsupportedPlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadata
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.SystemLibraryDiscovery
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProvider
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerType
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultGccCommandLineToolConfiguration
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.platform.base.internal.toolchain.SearchResult
import org.gradle.platform.base.internal.toolchain.ToolChainAvailability
import org.gradle.platform.base.internal.toolchain.ToolSearchResult
import org.gradle.process.internal.ExecActionFactory
import org.jspecify.annotations.NullMarked
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Arrays

/**
 * A tool chain that has GCC semantics.
 */
@NullMarked
abstract class AbstractGccCompatibleToolChain internal constructor(
    name: String,
    buildOperationExecutor: BuildOperationExecutor,
    operatingSystem: OperatingSystem,
    fileResolver: FileResolver,
    private val execActionFactory: ExecActionFactory,
    private val compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
    private val toolSearchPath: ToolSearchPath,
    protected val metaDataProvider: CompilerMetaDataProvider<GccMetadata>,
    private val standardLibraryDiscovery: SystemLibraryDiscovery,
    private val instantiator: Instantiator,
    private val workerLeaseService: WorkerLeaseService
) : ExtendableToolChain<GccPlatformToolChain?>(name, buildOperationExecutor, operatingSystem, fileResolver), GccCompatibleToolChain {
    private val platformConfigs: MutableList<TargetPlatformConfiguration> = ArrayList<TargetPlatformConfiguration>()
    private val toolProviders: MutableMap<NativePlatform, PlatformToolProvider> = HashMap<NativePlatform, PlatformToolProvider>()
    private var configInsertLocation: Int

    constructor(
        name: String,
        buildOperationExecutor: BuildOperationExecutor,
        operatingSystem: OperatingSystem,
        fileResolver: FileResolver,
        execActionFactory: ExecActionFactory,
        compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
        metaDataProvider: CompilerMetaDataProvider<GccMetadata>,
        standardLibraryDiscovery: SystemLibraryDiscovery,
        instantiator: Instantiator,
        workerLeaseService: WorkerLeaseService
    ) : this(
        name,
        buildOperationExecutor,
        operatingSystem,
        fileResolver,
        execActionFactory,
        compilerOutputFileNamingSchemeFactory,
        ToolSearchPath(operatingSystem),
        metaDataProvider,
        standardLibraryDiscovery,
        instantiator,
        workerLeaseService
    )

    init {
        target(Intel32Architecture())
        target(Intel64Architecture())
        target(Arm64Architecture())
        configInsertLocation = 0
    }

    protected fun locate(tool: GccCommandLineToolConfigurationInternal): CommandLineToolSearchResult {
        return toolSearchPath.locate(tool.toolType, tool.executable!!)
    }

    val path: MutableList<File>
        get() = toolSearchPath.path

    override fun path(vararg pathEntries: Any) {
        for (path in pathEntries) {
            toolSearchPath.path(resolve(path))
        }
    }

    override fun target(platformName: String) {
        target(platformName, Actions.doNothing<NativePlatformToolChain>())
    }

    override fun target(platformName: String, action: Action<in GccPlatformToolChain>) {
        target(AbstractGccCompatibleToolChain.DefaultTargetPlatformConfiguration(TODO("Cannot convert element"))<String> java . util . Arrays . asList < kotlin . String >(platformName), action)
    }

    fun target(platformNames: MutableList<String>, action: Action<in GccPlatformToolChain>) {
        target(DefaultTargetPlatformConfiguration(platformNames, action))
    }

    private fun target(targetPlatformConfiguration: TargetPlatformConfiguration) {
        platformConfigs.add(configInsertLocation, targetPlatformConfiguration)
        configInsertLocation++
    }

    override fun setTargets(vararg platformNames: String) {
        platformConfigs.clear()
        configInsertLocation = 0
        for (platformName in platformNames) {
            target(platformName)
        }
    }

    override fun select(targetPlatform: NativePlatformInternal): PlatformToolProvider {
        return select(NativeLanguage.ANY, targetPlatform)
    }

    private fun getProviderForPlatform(targetPlatform: NativePlatformInternal): PlatformToolProvider {
        var toolProvider = toolProviders.get(targetPlatform)
        if (toolProvider == null) {
            toolProvider = createPlatformToolProvider(targetPlatform)
            toolProviders.put(targetPlatform, toolProvider)
        }
        return toolProvider
    }

    override fun select(sourceLanguage: NativeLanguage, targetMachine: NativePlatformInternal): PlatformToolProvider {
        val toolProvider = getProviderForPlatform(targetMachine)
        when (sourceLanguage) {
            NativeLanguage.CPP -> {
                if (toolProvider is UnsupportedPlatformToolProvider) {
                    return toolProvider
                }
                val cppCompiler: ToolSearchResult? = toolProvider.locateTool(ToolType.CPP_COMPILER)
                if (cppCompiler!!.isAvailable) {
                    return toolProvider
                }
                // No C++ compiler, complain about it
                return UnavailablePlatformToolProvider(targetMachine.getOperatingSystem(), cppCompiler)
            }

            NativeLanguage.ANY -> {
                if (toolProvider is UnsupportedPlatformToolProvider) {
                    return toolProvider
                }
                val cCompiler: ToolSearchResult? = toolProvider.locateTool(ToolType.C_COMPILER)
                if (cCompiler!!.isAvailable) {
                    return toolProvider
                }
                val compiler: ToolSearchResult? = toolProvider.locateTool(ToolType.CPP_COMPILER)
                if (compiler!!.isAvailable) {
                    return toolProvider
                }
                compiler = toolProvider.locateTool(ToolType.OBJECTIVEC_COMPILER)
                if (compiler!!.isAvailable) {
                    return toolProvider
                }
                compiler = toolProvider.locateTool(ToolType.OBJECTIVECPP_COMPILER)
                if (compiler!!.isAvailable) {
                    return toolProvider
                }
                // No compilers available, complain about the missing C compiler
                return UnavailablePlatformToolProvider(targetMachine.getOperatingSystem(), cCompiler)
            }

            else -> return UnsupportedPlatformToolProvider(targetMachine.getOperatingSystem(), String.format("Don't know how to compile language %s.", sourceLanguage))
        }
    }

    private fun createPlatformToolProvider(targetPlatform: NativePlatformInternal): PlatformToolProvider {
        val targetPlatformConfigurationConfiguration = getPlatformConfiguration(targetPlatform)
        if (targetPlatformConfigurationConfiguration == null) {
            return UnsupportedPlatformToolProvider(targetPlatform.getOperatingSystem(), java.lang.String.format("Don't know how to build for %s.", targetPlatform.displayName))
        }

        val configurableToolChain = instantiator.newInstance<DefaultGccPlatformToolChain>(DefaultGccPlatformToolChain::class.java, targetPlatform)
        addDefaultTools(configurableToolChain)
        configureDefaultTools(configurableToolChain)
        targetPlatformConfigurationConfiguration.apply(configurableToolChain)
        configureActions.execute(configurableToolChain)
        configurableToolChain.compilerProbeArgs(*standardLibraryDiscovery.compilerProbeArgs(targetPlatform))

        val result = ToolChainAvailability()
        initTools(configurableToolChain, result)
        if (!result.isAvailable()) {
            return UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), result)
        }

        return GccPlatformToolProvider(
            buildOperationExecutor,
            targetPlatform.getOperatingSystem(),
            toolSearchPath,
            configurableToolChain,
            execActionFactory,
            compilerOutputFileNamingSchemeFactory,
            configurableToolChain.isCanUseCommandFile(),
            workerLeaseService,
            CompilerMetaDataProviderWithDefaultArgs(configurableToolChain.getCompilerProbeArgs(), metaDataProvider)
        )
    }

    protected fun initTools(platformToolChain: DefaultGccPlatformToolChain, availability: ToolChainAvailability) {
        // Attempt to determine whether the compiler is the correct implementation
        for (tool in platformToolChain.getCompilers()) {
            val compiler = locate(tool)
            if (compiler.isAvailable) {
                val gccMetadata = this.metaDataProvider.getCompilerMetaData(
                    toolSearchPath.path,
                    Action { spec: CompilerMetaDataProvider.CompilerExecSpec? -> spec!!.executable(compiler.tool).args(platformToolChain.getCompilerProbeArgs()) })
                availability.mustBeAvailable(gccMetadata)
                if (!gccMetadata.isAvailable) {
                    return
                }
                // Assume all the other compilers are ok, if they happen to be installed
                LOGGER.debug("Found {} with version {}", tool.toolType.toolName, gccMetadata)
                initForImplementation(platformToolChain, gccMetadata.component!!)
                break
            }
        }
    }

    protected open fun initForImplementation(platformToolChain: DefaultGccPlatformToolChain, versionResult: GccMetadata) {
    }

    private fun addDefaultTools(toolChain: DefaultGccPlatformToolChain) {
        toolChain.add(instantiator.newInstance<DefaultGccCommandLineToolConfiguration>(DefaultGccCommandLineToolConfiguration::class.java, ToolType.C_COMPILER, "gcc"))
        toolChain.add(instantiator.newInstance<DefaultGccCommandLineToolConfiguration>(DefaultGccCommandLineToolConfiguration::class.java, ToolType.CPP_COMPILER, "g++"))
        toolChain.add(instantiator.newInstance<DefaultGccCommandLineToolConfiguration>(DefaultGccCommandLineToolConfiguration::class.java, ToolType.LINKER, "g++"))
        toolChain.add(instantiator.newInstance<DefaultGccCommandLineToolConfiguration>(DefaultGccCommandLineToolConfiguration::class.java, ToolType.STATIC_LIB_ARCHIVER, "ar"))
        toolChain.add(instantiator.newInstance<DefaultGccCommandLineToolConfiguration>(DefaultGccCommandLineToolConfiguration::class.java, ToolType.OBJECTIVECPP_COMPILER, "g++"))
        toolChain.add(instantiator.newInstance<DefaultGccCommandLineToolConfiguration>(DefaultGccCommandLineToolConfiguration::class.java, ToolType.OBJECTIVEC_COMPILER, "gcc"))
        toolChain.add(instantiator.newInstance<DefaultGccCommandLineToolConfiguration>(DefaultGccCommandLineToolConfiguration::class.java, ToolType.ASSEMBLER, "gcc"))
        toolChain.add(
            instantiator.newInstance<DefaultGccCommandLineToolConfiguration>(
                DefaultGccCommandLineToolConfiguration::class.java,
                ToolType.SYMBOL_EXTRACTOR,
                SymbolExtractorOsConfig.current().executableName
            )
        )
        toolChain.add(instantiator.newInstance<DefaultGccCommandLineToolConfiguration>(DefaultGccCommandLineToolConfiguration::class.java, ToolType.STRIPPER, "strip"))
    }

    protected open fun configureDefaultTools(toolChain: DefaultGccPlatformToolChain) {
    }

    protected fun getPlatformConfiguration(targetPlatform: NativePlatformInternal): TargetPlatformConfiguration? {
        for (platformConfig in platformConfigs) {
            if (platformConfig.supportsPlatform(targetPlatform)) {
                return platformConfig
            }
        }
        return null
    }

    private class Intel32Architecture : TargetPlatformConfiguration {
        override fun supportsPlatform(targetPlatform: NativePlatformInternal): Boolean {
            return targetPlatform.getOperatingSystem().isCurrent && targetPlatform.getArchitecture().isI386()
        }

        override fun apply(gccToolChain: DefaultGccPlatformToolChain) {
            gccToolChain.compilerProbeArgs("-m32")
            val m32args: Action<MutableList<String>> = object : Action<MutableList<String>> {
                override fun execute(args: MutableList<String>) {
                    args.add("-m32")
                }
            }
            gccToolChain.cppCompiler.withArguments(m32args)
            gccToolChain.getcCompiler().withArguments(m32args)
            gccToolChain.objcCompiler.withArguments(m32args)
            gccToolChain.objcppCompiler.withArguments(m32args)
            gccToolChain.linker.withArguments(m32args)
            gccToolChain.assembler.withArguments(m32args)
        }
    }

    private class Intel64Architecture : TargetPlatformConfiguration {
        override fun supportsPlatform(targetPlatform: NativePlatformInternal): Boolean {
            return targetPlatform.getOperatingSystem().isCurrent
                    && targetPlatform.getArchitecture().isAmd64()
        }

        override fun apply(gccToolChain: DefaultGccPlatformToolChain) {
            val isMacOsX = gccToolChain.platform.operatingSystem.isMacOsX
            val compilerArgs: Array<String>
            if (isMacOsX) {
                compilerArgs = arrayOf<String>("-arch", "x86_64")
            } else {
                compilerArgs = arrayOf<String>("-m64")
            }
            gccToolChain.compilerProbeArgs(*compilerArgs)

            val m64args: Action<MutableList<String>> = object : Action<MutableList<String>> {
                override fun execute(args: MutableList<String>) {
                    args.addAll(Arrays.asList<String>(*compilerArgs))
                }
            }
            gccToolChain.cppCompiler.withArguments(m64args)
            gccToolChain.getcCompiler().withArguments(m64args)
            gccToolChain.objcCompiler.withArguments(m64args)
            gccToolChain.objcppCompiler.withArguments(m64args)
            gccToolChain.linker.withArguments(m64args)
            gccToolChain.assembler.withArguments(m64args)
        }
    }

    private class Arm64Architecture : TargetPlatformConfiguration {
        override fun supportsPlatform(targetPlatform: NativePlatformInternal): Boolean {
            return targetPlatform.getOperatingSystem().isCurrent
                    && (targetPlatform.getOperatingSystem().isMacOsX
                    || (targetPlatform.getOperatingSystem().isLinux && DefaultNativePlatform.getCurrentArchitecture().isArm64()))
                    && targetPlatform.getArchitecture().isArm()
        }

        override fun apply(gccToolChain: DefaultGccPlatformToolChain) {
            val isMacOsX = gccToolChain.platform.operatingSystem.isMacOsX
            val compilerArgs: Array<String>?
            if (isMacOsX) {
                compilerArgs = arrayOf<String>("-arch", "arm64")
            } else {
                compilerArgs = arrayOf<String>("-march=native")
            }
            val architectureArgs: Action<MutableList<String>> = object : Action<MutableList<String>> {
                override fun execute(args: MutableList<String>) {
                    args.addAll(Arrays.asList<String>(*compilerArgs))
                }
            }
            gccToolChain.cppCompiler.withArguments(architectureArgs)
            gccToolChain.getcCompiler().withArguments(architectureArgs)
            gccToolChain.objcCompiler.withArguments(architectureArgs)
            gccToolChain.objcppCompiler.withArguments(architectureArgs)
            gccToolChain.linker.withArguments(architectureArgs)
            gccToolChain.assembler.withArguments(architectureArgs)
        }
    }

    private class DefaultTargetPlatformConfiguration(//TODO this should be a container of platforms
        private val platformNames: MutableCollection<String>, private val configurationAction: Action<in GccPlatformToolChain>
    ) : TargetPlatformConfiguration {
        override fun supportsPlatform(targetPlatform: NativePlatformInternal): Boolean {
            return platformNames.contains(targetPlatform.getName())
        }

        override fun apply(platformToolChain: DefaultGccPlatformToolChain) {
            configurationAction.execute(platformToolChain)
        }
    }

    private class CompilerMetaDataProviderWithDefaultArgs(private val compilerProbeArgs: MutableList<String>, private val delegate: CompilerMetaDataProvider<GccMetadata>) :
        CompilerMetaDataProvider<GccMetadata> {
        override fun getCompilerMetaData(searchPath: MutableList<File>, configureAction: Action<in CompilerMetaDataProvider.CompilerExecSpec>): SearchResult<GccMetadata?> {
            return delegate.getCompilerMetaData(searchPath, Action { execSpec: CompilerMetaDataProvider.CompilerExecSpec? ->
                execSpec!!.args(compilerProbeArgs)
                configureAction.execute(execSpec)
            })
        }

        override fun getCompilerType(): CompilerType {
            return delegate.getCompilerType()
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(AbstractGccCompatibleToolChain::class.java)
    }
}
