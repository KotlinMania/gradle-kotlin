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
package org.gradle.language.nativeplatform.internal.toolchains

import org.gradle.internal.Cast.uncheckedCast
import org.gradle.language.cpp.CppPlatform
import org.gradle.language.cpp.internal.DefaultCppPlatform
import org.gradle.language.swift.SwiftPlatform
import org.gradle.language.swift.SwiftVersion
import org.gradle.language.swift.internal.DefaultSwiftPlatform
import org.gradle.nativeplatform.TargetMachine
import org.gradle.nativeplatform.platform.internal.Architectures
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry
import org.gradle.nativeplatform.toolchain.internal.NativeLanguage
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.util.internal.VersionNumber
import javax.inject.Inject

class DefaultToolChainSelector @Inject constructor(private val registry: NativeToolChainRegistry?) : ToolChainSelector {
    private val host: DefaultNativePlatform

    init {
        this.host = DefaultNativePlatform.host()
    }

    override fun <T> select(platformType: Class<T?>, requestPlatform: T?): ToolChainSelector.Result<T?>? {
        if (CppPlatform::class.java.isAssignableFrom(platformType)) {
            return uncheckedCast<ToolChainSelector.Result<T?>?>(select((requestPlatform as org.gradle.language.cpp.CppPlatform?)!!))
        } else if (SwiftPlatform::class.java.isAssignableFrom(platformType)) {
            return uncheckedCast<ToolChainSelector.Result<T?>?>(select((requestPlatform as org.gradle.language.swift.SwiftPlatform?)!!))
        } else {
            throw IllegalArgumentException("Unknown type of platform " + platformType)
        }
    }

    fun select(requestPlatform: CppPlatform): ToolChainSelector.Result<CppPlatform?> {
        val targetNativePlatform = newNativePlatform(requestPlatform.getTargetMachine())

        // TODO - push all this stuff down to the tool chain and let it create the specific platform and provider
        val sourceLanguage = NativeLanguage.CPP
        val toolChain = getToolChain(sourceLanguage, targetNativePlatform)

        // TODO - don't select again here, as the selection is already performed to select the toolchain
        val toolProvider = toolChain.select(sourceLanguage, targetNativePlatform)

        val targetPlatform: CppPlatform = DefaultCppPlatform(requestPlatform.getTargetMachine(), targetNativePlatform)
        return DefaultResult<CppPlatform?>(toolChain, toolProvider, targetPlatform)
    }

    fun select(requestPlatform: SwiftPlatform): ToolChainSelector.Result<SwiftPlatform?> {
        val targetNativePlatform = newNativePlatform(requestPlatform.getTargetMachine())

        // TODO - push all this stuff down to the tool chain and let it create the specific platform and provider
        val sourceLanguage = NativeLanguage.SWIFT
        val toolChain = getToolChain(sourceLanguage, targetNativePlatform)

        // TODO - don't select again here, as the selection is already performed to select the toolchain
        val toolProvider = toolChain.select(sourceLanguage, targetNativePlatform)

        var sourceCompatibility = requestPlatform.getSourceCompatibility()
        if (sourceCompatibility == null && toolProvider.isAvailable()) {
            sourceCompatibility = toSwiftVersion(toolProvider.getCompilerMetadata(ToolType.SWIFT_COMPILER).getVersion())
        }
        val targetPlatform: SwiftPlatform = DefaultSwiftPlatform(requestPlatform.getTargetMachine(), sourceCompatibility, targetNativePlatform)
        return DefaultResult<SwiftPlatform?>(toolChain, toolProvider, targetPlatform)
    }

    private fun newNativePlatform(targetMachine: TargetMachine): DefaultNativePlatform? {
        return host.withArchitecture(Architectures.forInput(targetMachine.getArchitecture().getName()))
    }

    private fun getToolChain(sourceLanguage: NativeLanguage?, targetNativePlatform: NativePlatformInternal?): NativeToolChainInternal {
        val toolChain = uncheckedCast<NativeToolChainRegistryInternal?>(registry)!!.getForPlatform(sourceLanguage, targetNativePlatform)
        toolChain.assertSupported()

        return toolChain
    }

    internal class DefaultResult<T>(private val toolChain: NativeToolChainInternal?, private val platformToolProvider: PlatformToolProvider?, private val targetPlatform: T?) :
        ToolChainSelector.Result<T?> {
        override fun getToolChain(): NativeToolChainInternal? {
            return toolChain
        }

        override fun getTargetPlatform(): T? {
            return targetPlatform
        }

        override fun getPlatformToolProvider(): PlatformToolProvider? {
            return platformToolProvider
        }
    }

    companion object {
        fun toSwiftVersion(swiftCompilerVersion: VersionNumber): SwiftVersion {
            for (version in SwiftVersion.entries) {
                if (version.getVersion() == swiftCompilerVersion.getMajor()) {
                    return version
                }
            }
            throw IllegalArgumentException(String.format("Swift language version is unknown for the specified Swift compiler version (%s)", swiftCompilerVersion.toString()))
        }
    }
}
