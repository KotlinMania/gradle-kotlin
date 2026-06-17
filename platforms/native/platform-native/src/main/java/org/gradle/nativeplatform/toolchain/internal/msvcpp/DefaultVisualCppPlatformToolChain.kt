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

import org.gradle.internal.reflect.Instantiator
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.CommandLineToolConfiguration
import org.gradle.nativeplatform.toolchain.VisualCppPlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolConfigurationInternal
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultCommandLineToolConfiguration

class DefaultVisualCppPlatformToolChain(val platform: NativePlatform, instantiator: Instantiator) : VisualCppPlatformToolChain {
    protected val tools: MutableMap<ToolType?, CommandLineToolConfigurationInternal?>

    init {
        tools = HashMap<ToolType?, CommandLineToolConfigurationInternal?>()
        tools.put(ToolType.C_COMPILER, instantiator.newInstance<DefaultCommandLineToolConfiguration?>(DefaultCommandLineToolConfiguration::class.java, ToolType.C_COMPILER))
        tools.put(ToolType.CPP_COMPILER, instantiator.newInstance<DefaultCommandLineToolConfiguration?>(DefaultCommandLineToolConfiguration::class.java, ToolType.CPP_COMPILER))
        tools.put(ToolType.LINKER, instantiator.newInstance<DefaultCommandLineToolConfiguration?>(DefaultCommandLineToolConfiguration::class.java, ToolType.LINKER))
        tools.put(ToolType.STATIC_LIB_ARCHIVER, instantiator.newInstance<DefaultCommandLineToolConfiguration?>(DefaultCommandLineToolConfiguration::class.java, ToolType.STATIC_LIB_ARCHIVER))
        tools.put(ToolType.ASSEMBLER, instantiator.newInstance<DefaultCommandLineToolConfiguration?>(DefaultCommandLineToolConfiguration::class.java, ToolType.ASSEMBLER))
        tools.put(
            ToolType.WINDOW_RESOURCES_COMPILER,
            instantiator.newInstance<DefaultCommandLineToolConfiguration?>(DefaultCommandLineToolConfiguration::class.java, ToolType.WINDOW_RESOURCES_COMPILER)
        )
    }

    override fun getcCompiler(): CommandLineToolConfiguration? {
        return tools.get(ToolType.C_COMPILER)
    }

    val cppCompiler: CommandLineToolConfiguration?
        get() = tools.get(ToolType.CPP_COMPILER)

    val rcCompiler: CommandLineToolConfiguration?
        get() = tools.get(ToolType.WINDOW_RESOURCES_COMPILER)

    val assembler: CommandLineToolConfiguration?
        get() = tools.get(ToolType.ASSEMBLER)

    val linker: CommandLineToolConfiguration?
        get() = tools.get(ToolType.LINKER)

    val staticLibArchiver: CommandLineToolConfiguration?
        get() = tools.get(ToolType.STATIC_LIB_ARCHIVER)
}
