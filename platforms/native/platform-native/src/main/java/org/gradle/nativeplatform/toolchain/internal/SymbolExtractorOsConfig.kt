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
package org.gradle.nativeplatform.toolchain.internal

import com.google.common.collect.ImmutableList
import org.gradle.internal.os.OperatingSystem

enum class SymbolExtractorOsConfig(executable: String, arguments: ImmutableList<String?>, extension: String) {
    OBJCOPY("objcopy", ImmutableList.of<String?>("--only-keep-debug"), ".debug"),
    DSYMUTIL("dsymutil", ImmutableList.of<String?>("-f"), ".dwarf") {
        override fun getInputOutputFileArguments(inputFilePath: String, outputFilePath: String): MutableList<String?> {
            return ImmutableList.of<String?>("-o", outputFilePath, inputFilePath)
        }
    };

    val executableName: String?
    private val arguments: ImmutableList<String?>?
    @JvmField
    val extension: String?

    init {
        this.executableName = executable
        this.arguments = arguments
        this.extension = extension
    }

    fun getArguments(): MutableList<String?>? {
        return arguments
    }

    open fun getInputOutputFileArguments(inputFilePath: String, outputFilePath: String): MutableList<String?> {
        return ImmutableList.of<String?>(inputFilePath, outputFilePath)
    }

    companion object {
        private val OS = OperatingSystem.current()

        @JvmStatic
        fun current(): SymbolExtractorOsConfig {
            if (OS!!.isMacOsX) {
                return SymbolExtractorOsConfig.DSYMUTIL
            } else {
                return SymbolExtractorOsConfig.OBJCOPY
            }
        }
    }
}
