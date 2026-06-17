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

internal open class VisualCppNativeCompiler<T : NativeCompileSpec?>(
    buildOperationExecutor: BuildOperationExecutor?,
    compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
    commandLineToolInvocationWorker: CommandLineToolInvocationWorker?,
    invocationContext: CommandLineToolContext?,
    argsTransformer: ArgsTransformer<T?>?,
    specTransformer: Transformer<T?, T?>,
    objectFileExtension: String?,
    useCommandFile: Boolean,
    workerLeaseService: WorkerLeaseService?
) : NativeCompiler<T?>(
    buildOperationExecutor,
    compilerOutputFileNamingSchemeFactory,
    commandLineToolInvocationWorker,
    invocationContext,
    argsTransformer,
    specTransformer,
    objectFileExtension,
    useCommandFile,
    workerLeaseService
) {
    override fun getOutputArgs(spec: T?, outputFile: File): MutableList<String?> {
        val args: MutableList<String?> = ArrayList<String?>()
        if (spec!!.isDebuggable) {
            args.add("/Fd" + File(outputFile.getParentFile(), outputFile.getName() + ".pdb"))
        }
        // MSVC doesn't allow a space between Fo and the file name
        args.add("/Fo" + outputFile.getAbsolutePath())
        return args
    }

    protected override fun addOptionsFileArgs(args: MutableList<String?>, tempDir: File?) {
        val writer: OptionsFileArgsWriter = VisualCppOptionsFileArgsWriter(tempDir)
        // modifies args in place
        writer.execute(args)
    }

    override fun getPCHArgs(spec: T?): MutableList<String?> {
        val pchArgs: MutableList<String?> = ArrayList<String?>()
        if (spec!!.preCompiledHeader != null && spec.preCompiledHeaderObjectFile != null) {
            val lastHeader: String = spec.preCompiledHeader!!

            pchArgs.add("/Yu" + lastHeader)
            pchArgs.add("/Fp" + spec.preCompiledHeaderObjectFile!!.getAbsolutePath())
        }
        return pchArgs
    }
}
