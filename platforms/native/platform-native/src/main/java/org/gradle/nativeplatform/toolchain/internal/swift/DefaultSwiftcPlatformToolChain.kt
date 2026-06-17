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

import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.SwiftcPlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolConfigurationInternal
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultCommandLineToolConfiguration

class DefaultSwiftcPlatformToolChain(val platform: NativePlatform) : SwiftcPlatformToolChain {
    private val tools: MutableMap<ToolType?, CommandLineToolConfigurationInternal?> = HashMap<ToolType?, CommandLineToolConfigurationInternal?>()

    fun add(tool: DefaultCommandLineToolConfiguration) {
        tools.put(tool.toolType, tool)
    }

    val swiftCompiler: CommandLineToolConfiguration?
        get() = tools.get(ToolType.SWIFT_COMPILER)

    val linker: CommandLineToolConfiguration?
        get() = tools.get(ToolType.LINKER)

    val staticLibArchiver: CommandLineToolConfiguration?
        get() = tools.get(ToolType.STATIC_LIB_ARCHIVER)

    val symbolExtractor: CommandLineToolConfiguration?
        get() = tools.get(ToolType.SYMBOL_EXTRACTOR)

    val stripper: CommandLineToolConfiguration?
        get() = tools.get(ToolType.STRIPPER)
}
