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
package org.gradle.nativeplatform.toolchain.internal.msvcpp

import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.VisualCpp
import org.gradle.nativeplatform.toolchain.VisualCppPlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.EmptySystemLibraries
import org.gradle.nativeplatform.toolchain.internal.ExtendableToolChain
import org.gradle.nativeplatform.toolchain.internal.NativeLanguage
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.UnavailablePlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.UnsupportedPlatformToolProvider
import org.gradle.platform.base.internal.toolchain.ToolChainAvailability
import org.gradle.platform.base.internal.toolchain.ToolSearchResult
import org.gradle.process.internal.ExecActionFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import javax.inject.Inject

class VisualCppToolChain @Inject constructor(
    private val name: String?,
    buildOperationExecutor: BuildOperationExecutor?,
    operatingSystem: OperatingSystem,
    fileResolver: FileResolver,
    private val execActionFactory: ExecActionFactory,
    private val compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
    private val visualStudioLocator: VisualStudioLocator,
    private val windowsSdkLocator: WindowsSdkLocator,
    private val ucrtLocator: UcrtLocator,
    private val instantiator: Instantiator,
    private val workerLeaseService: WorkerLeaseService
) : ExtendableToolChain<VisualCppPlatformToolChain?>(
    name, buildOperationExecutor, operatingSystem, fileResolver
), VisualCpp {
    private var installDir: File? = null
    private var ucrtDir: File? = null
    private var windowsSdkDir: File? = null
    private var ucrt: UcrtInstall? = null
    private var visualStudio: VisualStudioInstall? = null
    private var visualCpp: VisualCppInstall? = null
    private var windowsSdk: WindowsSdkInstall? = null
    private var availability: ToolChainAvailability? = null
        get() {
            if (field == null) {
                field = ToolChainAvailability()
                checkAvailable(field!!)
            }

            return field
        }

    val typeName: String?
        get() = "Visual Studio"

    override fun getInstallDir(): File {
        return installDir!!
    }

    public override fun setInstallDir(installDirPath: Any?) {
        this.installDir = resolve(installDirPath)
    }

    override fun getWindowsSdkDir(): File {
        return windowsSdkDir!!
    }

    public override fun setWindowsSdkDir(windowsSdkDirPath: Any?) {
        this.windowsSdkDir = resolve(windowsSdkDirPath)
    }

    fun getUcrtDir(): File? {
        return ucrtDir
    }

    fun setUcrtDir(ucrtDirPath: Any?) {
        this.ucrtDir = resolve(ucrtDirPath)
    }

    override fun select(targetPlatform: NativePlatformInternal): PlatformToolProvider? {
        val result = ToolChainAvailability()
        result.mustBeAvailable(this.availability!!)
        if (!result.isAvailable()) {
            return UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), result)
        }

        val platformVisualCpp = if (visualCpp == null) null else visualCpp!!.forPlatform(targetPlatform)
        if (platformVisualCpp == null) {
            return UnsupportedPlatformToolProvider(targetPlatform.getOperatingSystem(), java.lang.String.format("Don't know how to build for %s.", targetPlatform.displayName))
        }
        val platformSdk = windowsSdk!!.forPlatform(targetPlatform)
        val cRuntime = if (ucrt == null) EmptySystemLibraries() else ucrt!!.getCRuntime(targetPlatform)

        val configurableToolChain = instantiator.newInstance<DefaultVisualCppPlatformToolChain>(DefaultVisualCppPlatformToolChain::class.java, targetPlatform, instantiator)
        configureActions.execute(configurableToolChain)

        return VisualCppPlatformToolProvider(
            buildOperationExecutor!!,
            targetPlatform.getOperatingSystem(),
            configurableToolChain.tools,
            visualStudio!!,
            platformVisualCpp,
            platformSdk,
            cRuntime,
            execActionFactory,
            compilerOutputFileNamingSchemeFactory,
            workerLeaseService
        )
    }

    override fun select(sourceLanguage: NativeLanguage, targetMachine: NativePlatformInternal): PlatformToolProvider? {
        when (sourceLanguage) {
            NativeLanguage.CPP -> {
                val toolProvider = select(targetMachine)
                if (!toolProvider!!.isAvailable) {
                    return toolProvider
                }
                val cppCompiler: ToolSearchResult? = toolProvider.locateTool(ToolType.CPP_COMPILER)
                if (!cppCompiler!!.isAvailable) {
                    return UnavailablePlatformToolProvider(targetMachine.getOperatingSystem(), cppCompiler)
                }
                return toolProvider
            }

            NativeLanguage.ANY -> return select(targetMachine)
            else -> return UnsupportedPlatformToolProvider(targetMachine.getOperatingSystem(), String.format("Don't know how to compile language %s.", sourceLanguage))
        }
    }

    private fun checkAvailable(availability: ToolChainAvailability) {
        if (!operatingSystem.isWindows) {
            availability.unavailable("Visual Studio is not available on this operating system.")
            return
        }

        // TODO - this selection should happen per target platform
        val visualStudioSearchResult = visualStudioLocator.locateComponent(installDir)
        availability.mustBeAvailable(visualStudioSearchResult)
        if (visualStudioSearchResult.isAvailable) {
            visualStudio = visualStudioSearchResult.component
            visualCpp = visualStudioSearchResult.component!!.getVisualCpp()
        }

        val windowsSdkSearchResult = windowsSdkLocator.locateComponent(windowsSdkDir)
        availability.mustBeAvailable(windowsSdkSearchResult)
        if (windowsSdkSearchResult.isAvailable) {
            windowsSdk = windowsSdkSearchResult.component
        }

        // Universal CRT is required only for VS2015
        if (this.isVisualCpp2015) {
            val ucrtSearchResult = ucrtLocator.locateComponent(ucrtDir)
            availability.mustBeAvailable(ucrtSearchResult)
            if (ucrtSearchResult.isAvailable) {
                ucrt = ucrtSearchResult.component
            }
        }
    }

    override fun getName(): String? {
        return name
    }

    val displayName: String?
        get() = "Tool chain '" + getName() + "' (" + typeName + ")"

    val isVisualCpp2015: Boolean
        get() = visualCpp != null && visualCpp!!.getVersion().getMajor() >= 14

    companion object {
        val LOGGER: Logger? = LoggerFactory.getLogger(VisualCppToolChain::class.java)

        const val DEFAULT_NAME: String = "visualCpp"
    }
}
