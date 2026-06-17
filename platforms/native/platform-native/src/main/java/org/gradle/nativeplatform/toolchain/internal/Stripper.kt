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
package org.gradle.nativeplatform.toolchain.internal

import org.gradle.api.Action
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.StripperSpec
import java.io.File

class Stripper(
    buildOperationExecutor: BuildOperationExecutor?,
    commandLineToolInvocationWorker: CommandLineToolInvocationWorker?,
    invocationContext: CommandLineToolContext?,
    workerLeaseService: WorkerLeaseService?
) : AbstractCompiler<StripperSpec?>(buildOperationExecutor, commandLineToolInvocationWorker, invocationContext, StripperArgsTransformer(), false, workerLeaseService) {
    override fun newInvocationAction(spec: StripperSpec, args: MutableList<String?>?): Action<BuildOperationQueue<CommandLineToolInvocation?>?> {
        val invocation = newInvocation(
            "Stripping " + spec.getOutputFile().getName(), args, spec.getOperationLogger()
        )

        return object : Action<BuildOperationQueue<CommandLineToolInvocation?>?> {
            override fun execute(buildQueue: BuildOperationQueue<CommandLineToolInvocation?>) {
                buildQueue.setLogLocation(spec.getOperationLogger().getLogLocation())
                buildQueue.add(invocation)
            }
        }
    }

    override fun addOptionsFileArgs(args: MutableList<String?>?, tempDir: File?) {}

    private class StripperArgsTransformer : ArgsTransformer<StripperSpec?> {
        override fun transform(spec: StripperSpec): MutableList<String?> {
            val args: MutableList<String?> = ArrayList<String?>()
            args.addAll(spec.getArgs())
            args.add("-S")
            args.add("-o")
            args.add(spec.getOutputFile().getAbsolutePath())
            args.add(spec.getBinaryFile().getAbsolutePath())
            return args
        }
    }
}
