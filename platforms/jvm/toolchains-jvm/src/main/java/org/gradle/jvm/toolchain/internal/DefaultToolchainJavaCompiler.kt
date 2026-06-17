/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.jvm.toolchain.internal

import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.WorkResult
import org.gradle.jvm.toolchain.JavaCompiler
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.language.base.internal.compile.CompileSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DefaultToolchainJavaCompiler(private val javaToolchain: JavaToolchain, private val compilerFactory: JavaCompilerFactory) : JavaCompiler {
    @Nested
    override fun getMetadata(): JavaInstallationMetadata {
        return javaToolchain
    }

    @Internal
    override fun getExecutablePath(): RegularFile {
        return javaToolchain.findExecutable("javac")
    }

    fun <T : CompileSpec?> execute(spec: T?): WorkResult {
        LOGGER.info("Compiling with toolchain '{}'.", javaToolchain.getDisplayName())
        val specType = spec!!.javaClass as Class<T?>
        return compilerFactory.create<T?>(specType).execute(spec)
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultToolchainJavaCompiler::class.java)
    }
}
