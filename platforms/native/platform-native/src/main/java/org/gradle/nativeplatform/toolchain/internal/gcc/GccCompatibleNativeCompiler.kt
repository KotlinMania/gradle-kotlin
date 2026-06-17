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

import org.gradle.api.Transformer
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec
import org.gradle.nativeplatform.toolchain.internal.NativeCompiler
import org.gradle.nativeplatform.toolchain.internal.OptionsFileArgsWriter
import java.io.File
import java.util.Arrays

internal open class GccCompatibleNativeCompiler<T : NativeCompileSpec?>(
    buildOperationExecutor: BuildOperationExecutor?,
    compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
    commandLineTool: CommandLineToolInvocationWorker?,
    invocationContext: CommandLineToolContext?,
    argsTransformer: ArgsTransformer<T?>?,
    specTransformer: Transformer<T?, T?>,
    objectFileExtension: String?,
    useCommandFile: Boolean,
    workerLeaseService: WorkerLeaseService?
) : NativeCompiler<T?>(
    buildOperationExecutor,
    compilerOutputFileNamingSchemeFactory,
    commandLineTool,
    invocationContext,
    argsTransformer,
    specTransformer,
    objectFileExtension,
    useCommandFile,
    workerLeaseService
) {
    override fun getOutputArgs(spec: T?, outputFile: File): MutableList<String?> {
        return Arrays.asList<String?>("-o", outputFile.getAbsolutePath())
    }

    protected override fun addOptionsFileArgs(args: MutableList<String?>, tempDir: File?) {
        val writer: OptionsFileArgsWriter = GccOptionsFileArgsWriter(tempDir)
        // modifies args in place
        writer.execute(args)
    }

    override fun getPCHArgs(spec: T?): MutableList<String?> {
        val pchArgs: MutableList<String?> = ArrayList<String?>()
        if (spec!!.prefixHeaderFile != null) {
            pchArgs.add("-include")
            pchArgs.add(spec.prefixHeaderFile!!.getAbsolutePath())
        }
        return pchArgs
    }
}
