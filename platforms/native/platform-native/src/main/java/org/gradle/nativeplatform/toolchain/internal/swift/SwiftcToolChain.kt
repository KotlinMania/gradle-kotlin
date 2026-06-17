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

import org.gradle.api.Action
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.Swiftc
import org.gradle.nativeplatform.toolchain.SwiftcPlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.ExtendableToolChain
import org.gradle.nativeplatform.toolchain.internal.NativeLanguage
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.UnavailablePlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.UnsupportedPlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProvider
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProviderFactory
import org.gradle.nativeplatform.toolchain.internal.swift.metadata.SwiftcMetadata
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultCommandLineToolConfiguration
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.platform.base.internal.toolchain.ToolChainAvailability
import org.gradle.process.internal.ExecActionFactory
import javax.inject.Inject

class SwiftcToolChain internal constructor(
    name: String?,
    buildOperationExecutor: BuildOperationExecutor?,
    operatingSystem: OperatingSystem,
    fileResolver: PathToFileResolver,
    private val execActionFactory: ExecActionFactory?,
    private val compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory?,
    private val toolSearchPath: ToolSearchPath,
    compilerMetaDataProvider: CompilerMetaDataProviderFactory,
    private val instantiator: Instantiator,
    private val workerLeaseService: WorkerLeaseService?
) : ExtendableToolChain<SwiftcPlatformToolChain?>(name, buildOperationExecutor, operatingSystem, fileResolver), Swiftc {
    private val compilerMetaDataProvider: CompilerMetaDataProvider<SwiftcMetadata?>
    private val toolProviders: MutableMap<NativePlatform?, PlatformToolProvider?> = HashMap<NativePlatform?, PlatformToolProvider?>()

    @Inject
    constructor(
        name: String?,
        buildOperationExecutor: BuildOperationExecutor?,
        operatingSystem: OperatingSystem,
        fileResolver: PathToFileResolver,
        execActionFactory: ExecActionFactory?,
        compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory?,
        compilerMetaDataProvider: CompilerMetaDataProviderFactory,
        instantiator: Instantiator,
        workerLeaseService: WorkerLeaseService?
    ) : this(
        name,
        buildOperationExecutor,
        operatingSystem,
        fileResolver,
        execActionFactory,
        compilerOutputFileNamingSchemeFactory,
        ToolSearchPath(operatingSystem),
        compilerMetaDataProvider,
        instantiator,
        workerLeaseService
    )

    init {
        this.compilerMetaDataProvider = compilerMetaDataProvider.swiftc()
    }

    val path: MutableList<File?>?
        get() = toolSearchPath.path

    override fun path(vararg pathEntries: Any?) {
        for (path in pathEntries) {
            toolSearchPath.path(resolve(path))
        }
    }

    private fun createPlatformToolProvider(targetPlatform: NativePlatformInternal): PlatformToolProvider {
        val configurableToolChain = instantiator.newInstance<DefaultSwiftcPlatformToolChain>(DefaultSwiftcPlatformToolChain::class.java, targetPlatform)
        addDefaultTools(configurableToolChain)
        configureActions.execute(configurableToolChain)

        // TODO: this is an approximation as we know swift currently supports only 64-bit runtimes - eventually, we'll want to query for this
        if (!isCurrentArchitecture(targetPlatform)) {
            return UnsupportedPlatformToolProvider(targetPlatform.getOperatingSystem(), java.lang.String.format("Don't know how to build for %s.", targetPlatform.displayName))
        }

        val compiler = toolSearchPath.locate(ToolType.SWIFT_COMPILER, "swiftc")
        val result = ToolChainAvailability()
        result.mustBeAvailable(compiler)
        if (!result.isAvailable()) {
            return UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), result)
        }
        val swiftcMetaData = compilerMetaDataProvider.getCompilerMetaData(toolSearchPath.path, Action { spec: CompilerMetaDataProvider.CompilerExecSpec? -> spec!!.executable(compiler.tool) })
        result.mustBeAvailable(swiftcMetaData)
        if (!result.isAvailable()) {
            return UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), result)
        }

        return SwiftPlatformToolProvider(
            buildOperationExecutor,
            targetPlatform.getOperatingSystem(),
            toolSearchPath,
            configurableToolChain,
            execActionFactory,
            compilerOutputFileNamingSchemeFactory,
            workerLeaseService,
            swiftcMetaData.component
        )
    }

    private fun isCurrentArchitecture(targetPlatform: NativePlatformInternal): Boolean {
        return targetPlatform.getArchitecture() == DefaultNativePlatform.getCurrentArchitecture()
    }

    override fun select(sourceLanguage: NativeLanguage, targetMachine: NativePlatformInternal): PlatformToolProvider? {
        when (sourceLanguage) {
            NativeLanguage.SWIFT, NativeLanguage.ANY -> return select(targetMachine)
            else -> return UnsupportedPlatformToolProvider(targetMachine.getOperatingSystem(), String.format("Don't know how to compile language %s.", sourceLanguage))
        }
    }

    override fun select(targetPlatform: NativePlatformInternal): PlatformToolProvider? {
        var toolProvider = toolProviders.get(targetPlatform)
        if (toolProvider == null) {
            toolProvider = createPlatformToolProvider(targetPlatform)
            toolProviders.put(targetPlatform, toolProvider)
        }
        return toolProvider
    }

    private fun addDefaultTools(toolChain: DefaultSwiftcPlatformToolChain) {
        toolChain.add(instantiator.newInstance<DefaultCommandLineToolConfiguration?>(DefaultCommandLineToolConfiguration::class.java, ToolType.SWIFT_COMPILER))
        toolChain.add(instantiator.newInstance<DefaultCommandLineToolConfiguration?>(DefaultCommandLineToolConfiguration::class.java, ToolType.LINKER))
        toolChain.add(instantiator.newInstance<DefaultCommandLineToolConfiguration?>(DefaultCommandLineToolConfiguration::class.java, ToolType.STATIC_LIB_ARCHIVER))
        toolChain.add(instantiator.newInstance<DefaultCommandLineToolConfiguration?>(DefaultCommandLineToolConfiguration::class.java, ToolType.SYMBOL_EXTRACTOR))
        toolChain.add(instantiator.newInstance<DefaultCommandLineToolConfiguration?>(DefaultCommandLineToolConfiguration::class.java, ToolType.STRIPPER))
    }

    val typeName: String?
        get() = "Swift Compiler"

    companion object {
        const val DEFAULT_NAME: String = "swiftc"
    }
}
