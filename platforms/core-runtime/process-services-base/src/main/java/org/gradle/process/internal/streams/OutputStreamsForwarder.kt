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
package org.gradle.process.internal.streams

import org.gradle.internal.UncheckedException
import org.gradle.internal.operations.CurrentBuildOperationRef
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import kotlin.concurrent.Volatile

/**
 * Reads from the process' stdout and stderr (if not merged into stdout) and forwards to [OutputStream].
 */
class OutputStreamsForwarder(private val standardOutput: OutputStream?, private val errorOutput: OutputStream?, private val readErrorStream: Boolean) : StreamsHandler {
    private val completed: CountDownLatch
    private var executor: Executor? = null

    @Volatile
    private var standardOutputReader: ExecOutputHandleRunner? = null

    @Volatile
    private var standardErrorReader: ExecOutputHandleRunner? = null

    init {
        this.completed = CountDownLatch(if (readErrorStream) 2 else 1)
    }

    override fun connectStreams(process: Process, processName: String?, executor: Executor) {
        this.executor = executor
        standardOutputReader = ExecOutputHandleRunner("read standard output of " + processName, process.getInputStream(), standardOutput, completed)
        if (readErrorStream) {
            standardErrorReader = ExecOutputHandleRunner("read error output of " + processName, process.getErrorStream(), errorOutput, completed)
        }
    }

    override fun start() {
        if (readErrorStream) {
            standardErrorReader!!.associateBuildOperation(CurrentBuildOperationRef.instance().get())
            executor!!.execute(standardErrorReader)
        }
        standardOutputReader!!.associateBuildOperation(CurrentBuildOperationRef.instance().get())
        executor!!.execute(standardOutputReader)
    }

    override fun removeStartupContext() {
        standardOutputReader!!.clearAssociatedBuildOperation()
        if (readErrorStream) {
            standardErrorReader!!.clearAssociatedBuildOperation()
        }
    }

    override fun stop() {
        try {
            completed.await()
        } catch (e: InterruptedException) {
            throw UncheckedException.throwAsUncheckedException(e)
        }
    }

    override fun disconnect() {
        standardOutputReader!!.disconnect()
        if (readErrorStream) {
            standardErrorReader!!.disconnect()
        }
    }
}
