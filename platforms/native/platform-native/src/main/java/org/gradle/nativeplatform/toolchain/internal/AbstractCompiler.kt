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
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.logging.BuildOperationLogger
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.nativeplatform.internal.BinaryToolSpec
import java.io.File

abstract class AbstractCompiler<T : BinaryToolSpec?> protected constructor(
    private val buildOperationExecutor: BuildOperationExecutor,
    private val commandLineToolInvocationWorker: CommandLineToolInvocationWorker?,
    private val invocationContext: CommandLineToolContext,
    private val argsTransformer: ArgsTransformer<T?>,
    private val useCommandFile: Boolean,
    private val workerLeaseService: WorkerLeaseService
) : Compiler<T?> {
    override fun execute(spec: T?): WorkResult? {
        val commonArguments = getArguments(spec)
        val invocationAction: Action<BuildOperationQueue<CommandLineToolInvocation?>?>? = newInvocationAction(spec, commonArguments)

        workerLeaseService.runAsIsolatedTask(object : Runnable {
            override fun run() {
                buildOperationExecutor.runAll<CommandLineToolInvocation?>(commandLineToolInvocationWorker, invocationAction)
            }
        })

        return WorkResults.didWork(true)
    }

    // TODO(daniel): Should support in a better way multi file invocation.
    // Override this method to have multi file invocation
    protected abstract fun newInvocationAction(spec: T?, commonArguments: MutableList<String?>?): Action<BuildOperationQueue<CommandLineToolInvocation?>?>?

    protected fun getArguments(spec: T?): MutableList<String?>? {
        val args = argsTransformer.transform(spec)

        val userArgTransformer = invocationContext.getArgAction()
        // modifies in place
        userArgTransformer.execute(args)

        if (useCommandFile) {
            // Shorten args and write out an options.txt file
            // This must be called only once per execute()
            addOptionsFileArgs(args, spec!!.getTempDir())
        }
        return args
    }

    protected abstract fun addOptionsFileArgs(args: MutableList<String?>?, tempDir: File?)

    protected fun newInvocation(name: String?, workingDirectory: File?, args: Iterable<String?>?, operationLogger: BuildOperationLogger?): CommandLineToolInvocation? {
        return invocationContext.createInvocation(name, workingDirectory, args, operationLogger)
    }

    protected fun newInvocation(name: String?, args: Iterable<String?>?, operationLogger: BuildOperationLogger?): CommandLineToolInvocation? {
        return invocationContext.createInvocation(name, args, operationLogger)
    }
}
