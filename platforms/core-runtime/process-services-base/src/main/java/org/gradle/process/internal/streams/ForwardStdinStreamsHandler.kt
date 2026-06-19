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
package org.gradle.process.internal.streams

import org.gradle.internal.UncheckedException
import org.gradle.util.internal.DisconnectableInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor

/**
 * Forwards the contents of an [InputStream] to the process' stdin
 */
class ForwardStdinStreamsHandler(private val input: InputStream) : StreamsHandler {
    private val completed = CountDownLatch(1)
    private var executor: Executor? = null
    private var standardInputWriter: ExecOutputHandleRunner? = null

    override fun connectStreams(process: Process, processName: String?, executor: Executor) {
        this.executor = executor

        /*
            There's a potential problem here in that DisconnectableInputStream reads from input in the background.
            This won't automatically stop when the process is over. Therefore, if input is not closed then this thread
            will run forever. It would be better to ensure that this thread stops when the process does.
         */
        val instr: InputStream = DisconnectableInputStream(input)
        standardInputWriter = ExecOutputHandleRunner("write standard input to " + processName, instr, process.getOutputStream(), completed)
    }

    override fun start() {
        executor!!.execute(standardInputWriter)
    }

    override fun removeStartupContext() {
    }

    override fun stop() {
        disconnect()
        try {
            completed.await()
        } catch (e: InterruptedException) {
            throw UncheckedException.throwAsUncheckedException(e)
        }
    }

    override fun disconnect() {
        try {
            standardInputWriter!!.closeInput()
        } catch (e: IOException) {
            throw UncheckedException.throwAsUncheckedException(e)
        }
    }
}
