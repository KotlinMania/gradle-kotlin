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
package org.gradle.nativeplatform.toolchain.internal

import com.google.common.collect.Iterables
import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.internal.SafeFileLocationUtils
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import java.io.File

abstract class NativeCompiler<T : NativeCompileSpec?>(
    buildOperationExecutor: BuildOperationExecutor?,
    private val compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
    commandLineToolInvocationWorker: CommandLineToolInvocationWorker?,
    invocationContext: CommandLineToolContext?,
    argsTransformer: ArgsTransformer<T?>?,
    private val specTransformer: Transformer<T?, T?>,
    private val objectFileExtension: String?,
    useCommandFile: Boolean,
    workerLeaseService: WorkerLeaseService?
) : AbstractCompiler<T?>(buildOperationExecutor, commandLineToolInvocationWorker, invocationContext, argsTransformer, useCommandFile, workerLeaseService) {
    override fun execute(spec: T?): WorkResult {
        val transformedSpec = specTransformer.transform(spec)

        super.execute(spec)

        return WorkResults.didWork(!transformedSpec!!.getSourceFiles().isEmpty())
    }

    // TODO(daniel): Should support in a better way multi file invocation.
    override fun newInvocationAction(spec: T?, genericArgs: MutableList<String?>): Action<BuildOperationQueue<CommandLineToolInvocation?>?> {
        val objectDir = spec!!.getObjectFileDir()
        return object : Action<BuildOperationQueue<CommandLineToolInvocation?>?> {
            override fun execute(buildQueue: BuildOperationQueue<CommandLineToolInvocation?>) {
                buildQueue.setLogLocation(spec.getOperationLogger().getLogLocation())
                for (sourceFile in spec.getSourceFiles()) {
                    val perFileInvocation = createPerFileInvocation(genericArgs, sourceFile, objectDir, spec)
                    buildQueue.add(perFileInvocation)
                }
            }
        }
    }

    protected fun getSourceArgs(sourceFile: File): MutableList<String?> {
        return mutableListOf<String?>(sourceFile.getAbsolutePath())
    }

    protected abstract fun getOutputArgs(spec: T?, outputFile: File?): MutableList<String?>

    abstract override fun addOptionsFileArgs(args: MutableList<String?>?, tempDir: File?)

    protected abstract fun getPCHArgs(spec: T?): MutableList<String?>

    protected fun getOutputFileDir(sourceFile: File, objectFileDir: File?, fileSuffix: String?): File {
        val windowsPathLimitation = current()!!.isWindows

        val outputFile = compilerOutputFileNamingSchemeFactory.create()
            .withObjectFileNameSuffix(fileSuffix)
            .withOutputBaseFolder(objectFileDir)
            .map(sourceFile)
        val outputDirectory = outputFile.getParentFile()
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }
        return if (windowsPathLimitation) SafeFileLocationUtils.assertInWindowsPathLengthLimitation(outputFile) else outputFile
    }

    protected fun maybeGetPCHArgs(spec: T?, sourceFile: File?): MutableList<String?> {
        if (spec!!.getPreCompiledHeader() == null || !spec.getSourceFilesForPch().contains(sourceFile)) {
            return ArrayList<String?>()
        }

        return getPCHArgs(spec)
    }

    protected fun createPerFileInvocation(genericArgs: MutableList<String?>, sourceFile: File, objectDir: File?, spec: T?): CommandLineToolInvocation? {
        val sourceArgs = getSourceArgs(sourceFile)
        val outputArgs = getOutputArgs(spec, getOutputFileDir(sourceFile, objectDir, objectFileExtension))
        val pchArgs = maybeGetPCHArgs(spec, sourceFile)

        return newInvocation("compiling " + sourceFile.getName(), objectDir, buildPerFileArgs(genericArgs, sourceArgs, outputArgs, pchArgs), spec!!.getOperationLogger())
    }

    protected open fun buildPerFileArgs(genericArgs: MutableList<String?>, sourceArgs: MutableList<String?>, outputArgs: MutableList<String?>, pchArgs: MutableList<String?>): Iterable<String?>? {
        return Iterables.concat<String?>(genericArgs, pchArgs, sourceArgs, outputArgs)
    }
}
