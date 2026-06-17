/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.collect.Iterables
import org.gradle.api.Transformer
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker
import org.gradle.nativeplatform.toolchain.internal.compilespec.WindowsResourceCompileSpec
import java.util.Optional
import java.util.function.Consumer

internal class WindowsResourceCompiler(
    buildOperationExecutor: BuildOperationExecutor?,
    compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory?,
    commandLineTool: CommandLineToolInvocationWorker?,
    invocationContext: CommandLineToolContext?,
    specTransformer: Transformer<WindowsResourceCompileSpec?, WindowsResourceCompileSpec?>?,
    objectFileExtension: String?,
    useCommandFile: Boolean,
    workerLeaseService: WorkerLeaseService?
) : VisualCppNativeCompiler<WindowsResourceCompileSpec?>(
    buildOperationExecutor,
    compilerOutputFileNamingSchemeFactory,
    commandLineTool,
    invocationContext,
    RcCompilerArgsTransformer(),
    specTransformer,
    objectFileExtension,
    useCommandFile,
    workerLeaseService
) {
    override fun buildPerFileArgs(genericArgs: MutableList<String?>, sourceArgs: MutableList<String?>, outputArgs: MutableList<String?>, pchArgs: MutableList<String?>?): Iterable<String?>? {
        if (pchArgs != null && !pchArgs.isEmpty()) {
            throw UnsupportedOperationException("Precompiled header arguments cannot be specified for a Windows Resource compiler.")
        }
        // RC has position sensitive arguments, the output args need to appear before the source file
        return Iterables.concat<String?>(genericArgs, outputArgs, sourceArgs)
    }

    private class RcCompilerArgsTransformer : VisualCppCompilerArgsTransformer<WindowsResourceCompileSpec?>() {
        override fun addToolSpecificArgs(spec: WindowsResourceCompileSpec?, args: MutableList<String?>) {
            getLanguageOption().ifPresent(Consumer { e: String? -> args.add(e) })
            args.add("/nologo")
        }

        override fun getLanguageOption(): Optional<String?> {
            return Optional.of<String?>("/r")
        }
    }
}
