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
package org.gradle.nativeplatform.toolchain.internal.gcc

import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultGccCommandLineToolConfiguration
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal
import org.gradle.nativeplatform.toolchain.internal.tools.ToolRegistry
import org.jspecify.annotations.NullMarked
import java.util.Arrays

@NullMarked
class DefaultGccPlatformToolChain(val platform: NativePlatform) : GccPlatformToolChain, ToolRegistry {
    var isCanUseCommandFile: Boolean = true
    val compilerProbeArgs: MutableList<String> = ArrayList<String>()
    private val tools: MutableMap<ToolType, GccCommandLineToolConfigurationInternal> = HashMap<ToolType, GccCommandLineToolConfigurationInternal>()

    fun compilerProbeArgs(vararg args: String) {
        this.compilerProbeArgs.addAll(Arrays.asList<String>(*args))
    }

    override fun getTool(toolType: ToolType): GccCommandLineToolConfigurationInternal? {
        return tools.get(toolType)
    }

    fun getTools(): MutableCollection<GccCommandLineToolConfigurationInternal> {
        return tools.values
    }

    val compilers: MutableCollection<GccCommandLineToolConfigurationInternal>
        get() = Arrays.asList<GccCommandLineToolConfigurationInternal>(
            tools.get(ToolType.C_COMPILER),
            tools.get(ToolType.CPP_COMPILER),
            tools.get(ToolType.OBJECTIVEC_COMPILER),
            tools.get(ToolType.OBJECTIVECPP_COMPILER)
        )

    fun add(tool: DefaultGccCommandLineToolConfiguration) {
        tools.put(tool.toolType!!, tool)
    }

    override fun getcCompiler(): GccCommandLineToolConfigurationInternal {
        return tools.get(ToolType.C_COMPILER)!!
    }

    val cppCompiler: GccCommandLineToolConfigurationInternal
        get() = tools.get(ToolType.CPP_COMPILER)

    val objcCompiler: GccCommandLineToolConfigurationInternal
        get() = tools.get(ToolType.OBJECTIVEC_COMPILER)

    val objcppCompiler: GccCommandLineToolConfigurationInternal
        get() = tools.get(ToolType.OBJECTIVECPP_COMPILER)

    val assembler: GccCommandLineToolConfigurationInternal
        get() = tools.get(ToolType.ASSEMBLER)

    val linker: GccCommandLineToolConfigurationInternal
        get() = tools.get(ToolType.LINKER)

    val staticLibArchiver: GccCommandLineToolConfigurationInternal
        get() = tools.get(ToolType.STATIC_LIB_ARCHIVER)

    val symbolExtractor: GccCommandLineToolConfigurationInternal
        get() = tools.get(ToolType.SYMBOL_EXTRACTOR)

    val stripper: GccCommandLineToolConfigurationInternal
        get() = tools.get(ToolType.STRIPPER)
}
