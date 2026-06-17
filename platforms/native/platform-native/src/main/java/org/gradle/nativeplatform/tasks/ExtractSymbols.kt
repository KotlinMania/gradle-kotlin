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
package org.gradle.nativeplatform.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.Cast
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator
import org.gradle.nativeplatform.internal.DefaultSymbolExtractorSpec
import org.gradle.nativeplatform.internal.SymbolExtractorSpec
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.work.DisableCachingByDefault

/**
 * Extracts the debug symbols from a binary and stores them in a separate file.
 *
 * @since 4.5
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class ExtractSymbols : DefaultTask() {
    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFile
    abstract val binaryFile: RegularFileProperty?

    @get:OutputFile
    abstract val symbolFile: RegularFileProperty?

    @get:Internal
    abstract val toolChain: Property<NativeToolChain?>?

    @get:Nested
    abstract val targetPlatform: Property<NativePlatform?>?

    // TODO: Need to track version/implementation of symbol extraction tool.
    @TaskAction
    protected fun extractSymbols() {
        val operationLogger = getServices().get<BuildOperationLoggerFactory?>(BuildOperationLoggerFactory::class.java)!!.newOperationLogger(getName(), getTemporaryDir())

        val spec: SymbolExtractorSpec = DefaultSymbolExtractorSpec()
        spec.setBinaryFile(this.binaryFile.get().getAsFile())
        spec.setSymbolFile(this.symbolFile.get().getAsFile())
        spec.setOperationLogger(operationLogger)

        var symbolExtractor = createCompiler()
        symbolExtractor = BuildOperationLoggingCompilerDecorator.wrap<SymbolExtractorSpec?>(symbolExtractor)
        val result = symbolExtractor.execute(spec)
        setDidWork(result.getDidWork())
    }

    private fun createCompiler(): Compiler<SymbolExtractorSpec?>? {
        val targetPlatform = Cast.cast<NativePlatformInternal?, NativePlatform?>(NativePlatformInternal::class.java, this.targetPlatform.get())
        val toolChain = Cast.cast<NativeToolChainInternal?, NativeToolChain?>(NativeToolChainInternal::class.java, this.toolChain.get())
        val toolProvider = toolChain!!.select(targetPlatform)
        return toolProvider!!.newCompiler<SymbolExtractorSpec?>(SymbolExtractorSpec::class.java)
    }
}
