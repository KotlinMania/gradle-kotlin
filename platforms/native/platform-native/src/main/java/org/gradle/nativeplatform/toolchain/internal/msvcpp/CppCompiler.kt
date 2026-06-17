/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.Transformer
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppCompileSpec
import java.util.Optional

internal class CppCompiler(
    buildOperationExecutor: BuildOperationExecutor?,
    compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
    commandLineToolInvocationWorker: CommandLineToolInvocationWorker?,
    invocationContext: CommandLineToolContext?,
    specTransformer: Transformer<CppCompileSpec?, CppCompileSpec?>,
    objectFileExtension: String?,
    useCommandFile: Boolean,
    workerLeaseService: WorkerLeaseService?
) : VisualCppNativeCompiler<CppCompileSpec?>(
    buildOperationExecutor,
    compilerOutputFileNamingSchemeFactory,
    commandLineToolInvocationWorker,
    invocationContext,
    CppCompilerArgsTransformer(),
    specTransformer,
    objectFileExtension,
    useCommandFile,
    workerLeaseService
) {
    private class CppCompilerArgsTransformer : VisualCppCompilerArgsTransformer<CppCompileSpec?>() {
        val languageOption: Optional<String?>
            get() = Optional.of<String?>("/TP")
    }
}
