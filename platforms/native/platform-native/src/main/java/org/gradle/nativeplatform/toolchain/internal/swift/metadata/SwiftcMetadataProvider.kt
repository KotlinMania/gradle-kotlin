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
package org.gradle.nativeplatform.toolchain.internal.swift.metadata

import com.google.common.collect.ImmutableList
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.nativeplatform.toolchain.internal.metadata.AbstractMetadataProvider
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerType
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.internal.VersionNumber
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.StringReader

class SwiftcMetadataProvider(execActionFactory: ExecActionFactory?) : AbstractMetadataProvider<SwiftcMetadata?>(execActionFactory) {
    override fun compilerArgs(): MutableList<String?> {
        return ImmutableList.of<String?>("--version")
    }

    override fun getCompilerType(): CompilerType {
        return SWIFTC_COMPILER_TYPE
    }

    override fun parseCompilerOutput(stdout: String, stderr: String?, swiftc: File, path: MutableList<File?>?): SwiftcMetadata {
        val reader = BufferedReader(StringReader(stdout))
        try {
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                if (line!!.contains("Swift version")) {
                    val tokens: Array<String?> = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    // Assuming format: 'Swift version 4.0.2 (...)'
                    var i = 2
                    if ("Apple" == tokens[0]) {
                        // Actual format: 'Apple Swift version 4.0.2 (...)'
                        i++
                    }
                    val version = VersionNumber.parse(tokens[i])
                    return DefaultSwiftcMetadata(line, version)
                }
            }
            throw BrokenResultException(String.format("Could not determine %s metadata: %s produced unexpected output.", getCompilerType().description, swiftc.getName()))
        } catch (e: IOException) {
            // Should not happen when reading from a StringReader
            throw throwAsUncheckedException(e)
        }
    }

    private class DefaultSwiftcMetadata(private val versionString: String?, private val version: VersionNumber?) : SwiftcMetadata {
        override fun getVendor(): String? {
            return versionString
        }

        override fun getVersion(): VersionNumber? {
            return version
        }
    }

    companion object {
        private val SWIFTC_COMPILER_TYPE: CompilerType = object : CompilerType {
            override fun getIdentifier(): String {
                return "swiftc"
            }

            override fun getDescription(): String {
                return "SwiftC"
            }
        }
    }
}
