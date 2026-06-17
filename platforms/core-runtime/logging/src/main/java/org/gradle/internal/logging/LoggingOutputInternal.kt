/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.logging

import org.gradle.api.logging.LoggingOutput
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.api.logging.configuration.ConsoleUnicodeSupport
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import org.gradle.internal.scan.UsedByScanPlugin
import java.io.OutputStream

/**
 * Allows various logging consumers to be attached to the output of the logging system.
 */
interface LoggingOutputInternal : LoggingOutput {
    /**
     * Adds System.out and System.err as logging destinations. The output will include plain text only, with no color or dynamic text.
     */
    fun attachSystemOutAndErr()

    /**
     * Adds the current processes' stdout and stderr as logging destinations. The output will also include color and dynamic text when one of these
     * is connected to a console.
     *
     *
     * Removes standard output and/or error as a side-effect.
     */
    fun attachProcessConsole(consoleOutput: ConsoleOutput?, consoleUnicodeSupport: ConsoleUnicodeSupport?)

    /**
     * Adds the given [OutputStream] as a logging destination. The stream receives stdout and stderr logging formatted according to the current logging settings and encoded using the system character encoding. The output also includes color and dynamic text encoded using ANSI control sequences, depending on the requested output format.
     *
     * Assumes that a console is attached to stderr.
     *
     *
     * Removes System.out and System.err as logging destinations, if present, as a side-effect.
     *
     * @param outputStream Receives formatted output.
     * @param errorStream Receives formatted error output. Note that this steam may not necessarily be used, depending on the console mode requested.
     * @param consoleOutput The output format.
     */
    fun attachConsole(outputStream: OutputStream?, errorStream: OutputStream?, consoleOutput: ConsoleOutput?)

    /**
     * Adds the given [OutputStream] as a logging destination. The stream receives stdout and stderr logging formatted according to the current logging settings and encoded using the system character encoding. The output also includes color and dynamic text encoded using ANSI control sequences, depending on the requested output format.
     *
     *
     * Removes System.out and System.err as logging destinations, if present, as a side-effect.
     *
     * @param outputStream Receives formatted output.
     * @param errorStream Receives formatted error output. Note that this steam may not necessarily be used, depending on the console mode requested.
     * @param consoleMetadata The metadata associated with this console
     * @param consoleOutput The output format.
     */
    fun attachConsole(outputStream: OutputStream?, errorStream: OutputStream?, consoleOutput: ConsoleOutput?, consoleMetadata: ConsoleMetaData?)

    /**
     * Adds the given [OutputStream] as a logging destination. The stream receives stdout logging formatted according to the current logging settings and
     * encoded using the system character encoding.
     */
    fun addStandardOutputListener(outputStream: OutputStream?)

    /**
     * Adds the given [OutputStream] as a logging destination. The stream receives stderr logging formatted according to the current logging settings and
     * encoded using the system character encoding.
     */
    fun addStandardErrorListener(outputStream: OutputStream?)

    /**
     * Adds the given listener as a logging destination.
     */
    @UsedByScanPlugin
    fun addOutputEventListener(listener: OutputEventListener?)

    /**
     * Adds the given listener.
     */
    @UsedByScanPlugin
    fun removeOutputEventListener(listener: OutputEventListener?)

    /**
     * Flush any outstanding output.
     */
    fun flush()
}
