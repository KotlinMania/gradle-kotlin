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
package org.gradle.nativeplatform.toolchain.internal.swift

import org.gradle.api.Action
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.BundleLinkerSpec
import org.gradle.nativeplatform.internal.LinkerSpec
import org.gradle.nativeplatform.internal.SharedLibraryLinkerSpec
import org.gradle.nativeplatform.toolchain.internal.AbstractCompiler
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker
import java.io.File

// TODO(daniel): Swift compiler should extends from an abstraction of NativeCompiler (most of is applies to SwiftCompiler)
internal class SwiftLinker(
    buildOperationExecutor: BuildOperationExecutor,
    commandLineToolInvocationWorker: CommandLineToolInvocationWorker?,
    invocationContext: CommandLineToolContext,
    workerLeaseService: WorkerLeaseService
) : AbstractCompiler<LinkerSpec?>(buildOperationExecutor, commandLineToolInvocationWorker, invocationContext, SwiftCompileArgsTransformer(), false, workerLeaseService) {
    override fun addOptionsFileArgs(args: MutableList<String?>?, tempDir: File?) {
    }

    override fun newInvocationAction(spec: LinkerSpec, args: MutableList<String?>?): Action<BuildOperationQueue<CommandLineToolInvocation?>?>? {
        val invocation = newInvocation(
            "linking " + spec.getOutputFile().getName(), spec.getOutputFile().getParentFile(), args, spec.getOperationLogger()
        )

        return object : Action<BuildOperationQueue<CommandLineToolInvocation?>?> {
            override fun execute(buildQueue: BuildOperationQueue<CommandLineToolInvocation?>) {
                buildQueue.setLogLocation(spec.getOperationLogger().getLogLocation())
                buildQueue.add(invocation)
            }
        }
    }

    private class SwiftCompileArgsTransformer : ArgsTransformer<LinkerSpec?> {
        override fun transform(spec: LinkerSpec): MutableList<String?> {
            val args: MutableList<String?> = ArrayList<String?>()

            args.addAll(spec.getSystemArgs())

            if (spec is SharedLibraryLinkerSpec) {
                args.add("-emit-library")
            } else if (spec is BundleLinkerSpec) {
                args.add("-Xlinker")
                args.add("-bundle")
            } else {
                args.add("-emit-executable")
            }
            args.add("-o")
            args.add(spec.getOutputFile().getAbsolutePath())
            for (file in spec.getObjectFiles()) {
                args.add(file.getAbsolutePath())
            }
            for (file in spec.getLibraries()) {
                args.add(file.getAbsolutePath())
            }
            if (!spec.getLibraryPath().isEmpty()) {
                throw UnsupportedOperationException("Library Path not yet supported on Swiftc")
            }

            for (userArg in spec.getArgs()) {
                args.add(userArg)
            }

            return args
        }
    }
}
