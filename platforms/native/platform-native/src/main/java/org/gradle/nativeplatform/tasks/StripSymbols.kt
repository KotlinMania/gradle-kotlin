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
import org.gradle.nativeplatform.internal.DefaultStripperSpec
import org.gradle.nativeplatform.internal.StripperSpec
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.work.DisableCachingByDefault

/**
 * Strips the debug symbols from a binary
 *
 * @since 4.5
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class StripSymbols : DefaultTask() {
    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFile
    abstract val binaryFile: RegularFileProperty?

    @get:OutputFile
    abstract val outputFile: RegularFileProperty?

    @get:Internal
    abstract val toolChain: Property<NativeToolChain?>?

    @get:Nested
    abstract val targetPlatform: Property<NativePlatform?>?

    // TODO: Need to track version/implementation of symbol strip tool.
    @TaskAction
    protected fun stripSymbols() {
        val operationLogger = getServices().get<BuildOperationLoggerFactory?>(BuildOperationLoggerFactory::class.java)!!.newOperationLogger(getName(), getTemporaryDir())

        val spec: StripperSpec = DefaultStripperSpec()
        spec.setBinaryFile(this.binaryFile.get().getAsFile())
        spec.setOutputFile(this.outputFile.get().getAsFile())
        spec.setOperationLogger(operationLogger)

        var symbolStripper = createCompiler()
        symbolStripper = BuildOperationLoggingCompilerDecorator.wrap<StripperSpec?>(symbolStripper)
        val result = symbolStripper.execute(spec)
        setDidWork(result.getDidWork())
    }

    private fun createCompiler(): Compiler<StripperSpec?>? {
        val targetPlatform = Cast.cast<NativePlatformInternal?, NativePlatform?>(NativePlatformInternal::class.java, this.targetPlatform.get())
        val toolChain = Cast.cast<NativeToolChainInternal?, NativeToolChain?>(NativeToolChainInternal::class.java, this.toolChain.get())
        val toolProvider = toolChain!!.select(targetPlatform)
        return toolProvider!!.newCompiler<StripperSpec?>(StripperSpec::class.java)
    }
}
