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

import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.api.tasks.WorkResult
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.LinkerSpec
import org.gradle.nativeplatform.internal.SharedLibraryLinkerSpec
import org.gradle.nativeplatform.toolchain.internal.AbstractCompiler
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker
import java.io.File

internal class LinkExeLinker(
    buildOperationExecutor: BuildOperationExecutor,
    commandLineToolInvocationWorker: CommandLineToolInvocationWorker?,
    private val invocationContext: CommandLineToolContext,
    private val specTransformer: Transformer<LinkerSpec?, LinkerSpec?>,
    workerLeaseService: WorkerLeaseService
) : AbstractCompiler<LinkerSpec?>(
    buildOperationExecutor, commandLineToolInvocationWorker,
    invocationContext, LinkerArgsTransformer(), true, workerLeaseService
) {
    override fun execute(spec: LinkerSpec?): WorkResult? {
        val transformedSpec = specTransformer.transform(spec)

        return super.execute(transformedSpec)
    }

    override fun newInvocationAction(spec: LinkerSpec, args: MutableList<String?>?): Action<BuildOperationQueue<CommandLineToolInvocation?>?>? {
        val invocation = invocationContext.createInvocation(
            "linking " + spec.getOutputFile().getName(), args, spec.getOperationLogger()
        )

        return object : Action<BuildOperationQueue<CommandLineToolInvocation?>?> {
            override fun execute(buildQueue: BuildOperationQueue<CommandLineToolInvocation?>) {
                buildQueue.setLogLocation(spec.getOperationLogger().getLogLocation())
                buildQueue.add(invocation)
            }
        }
    }

    override fun addOptionsFileArgs(args: MutableList<String?>, tempDir: File?) {
        VisualCppOptionsFileArgsWriter(tempDir).execute(args)
    }

    internal class LinkerArgsTransformer : ArgsTransformer<LinkerSpec?> {
        override fun transform(spec: LinkerSpec): MutableList<String?> {
            val args: MutableList<String?> = ArrayList<String?>()
            if (spec.isDebuggable()) {
                args.add("/DEBUG")
            }
            args.addAll(EscapeUserArgs.Companion.escapeUserArgs(spec.getAllArgs()))
            args.add("/OUT:" + spec.getOutputFile().getAbsolutePath())
            args.add("/NOLOGO")
            if (spec is SharedLibraryLinkerSpec) {
                val sharedLibSpec = spec
                args.add("/DLL")
                args.add("/IMPLIB:" + sharedLibSpec.getImportLibrary().getAbsolutePath())
            }
            for (pathEntry in spec.getLibraryPath()) {
                args.add("/LIBPATH:" + pathEntry.getAbsolutePath())
            }
            for (file in spec.getObjectFiles()) {
                args.add(file.getAbsolutePath())
            }
            for (file in spec.getLibraries()) {
                args.add(file.getAbsolutePath())
            }
            return args
        }
    }
}
