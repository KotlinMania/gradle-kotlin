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
package org.gradle.internal.logging.sink

import org.fusesource.jansi.AnsiPrintStream
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.api.logging.configuration.ConsoleUnicodeSupport
import org.gradle.internal.logging.console.AnsiConsole
import org.gradle.internal.logging.console.ColorMap
import org.gradle.internal.logging.console.Console
import org.gradle.internal.logging.console.ProgressBar.Companion.buildTaskbarProgressResetSequence
import org.gradle.internal.nativeintegration.console.ConsoleDetector
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import org.gradle.internal.nativeintegration.console.FallbackConsoleMetaData
import org.gradle.internal.nativeintegration.console.UnicodeProxyConsoleMetaData
import org.gradle.internal.nativeintegration.services.NativeServices
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.UncheckedIOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.function.Supplier

object ConsoleConfigureAction {
    fun execute(renderer: OutputEventRenderer, consoleOutput: ConsoleOutput?, consoleUnicodeSupport: ConsoleUnicodeSupport) {
        ConsoleConfigureAction.execute(renderer, consoleOutput, getConsoleMetaData(consoleUnicodeSupport)!!, renderer.getOriginalStdOut(), renderer.getOriginalStdErr())
    }

    fun execute(renderer: OutputEventRenderer, consoleOutput: ConsoleOutput?, consoleMetadata: ConsoleMetaData, stdout: OutputStream, stderr: OutputStream?) {
        if (consoleOutput == ConsoleOutput.Auto) {
            configureAutoConsole(renderer, consoleMetadata, stdout, stderr)
        } else if (consoleOutput == ConsoleOutput.Rich) {
            configureRichConsole(renderer, consoleMetadata, stdout, stderr, false)
        } else if (consoleOutput == ConsoleOutput.Verbose) {
            configureRichConsole(renderer, consoleMetadata, stdout, stderr, true)
        } else if (consoleOutput == ConsoleOutput.Plain) {
            configurePlainConsole(renderer, consoleMetadata, stdout, stderr)
        } else if (consoleOutput == ConsoleOutput.Colored) {
            configureColoredConsole(renderer, consoleMetadata, stdout, stderr)
        }

        if (shouldEmitTaskbarProgress(consoleOutput, consoleMetadata)) {
            registerTaskbarReset(consoleMetadata, stdout)
        }
    }

    private fun shouldEmitTaskbarProgress(consoleOutput: ConsoleOutput?, consoleMetadata: ConsoleMetaData): Boolean {
        if (consoleOutput == ConsoleOutput.Plain) {
            return false
        }
        return consoleOutput != ConsoleOutput.Auto || consoleMetadata.isStdOutATerminal
    }

    private fun registerTaskbarReset(consoleMetadata: ConsoleMetaData, stdout: OutputStream) {
        val reset = buildTaskbarProgressResetSequence(consoleMetadata)
        if (!reset.isEmpty()) {
            try {
                val resetBytes = reset.toByteArray(StandardCharsets.UTF_8)
                Runtime.getRuntime().addShutdownHook(Thread(Runnable {
                    try {
                        stdout.write(resetBytes)
                        stdout.flush()
                    } catch (ignored: IOException) {
                        //ignore
                    }
                }, "taskbar-progress-reset"))
            } catch (ignored: SecurityException) {
                // Unable to register shutdown hook; proceed without it
            } catch (ignored: IllegalStateException) {
            }
        }
    }

    fun createProxyingConsoleMetaData(metaData: ConsoleMetaData, consoleUnicodeSupport: ConsoleUnicodeSupport): ConsoleMetaData? {
        when (consoleUnicodeSupport) {
            ConsoleUnicodeSupport.Auto -> return metaData
            ConsoleUnicodeSupport.Disable -> return UnicodeProxyConsoleMetaData.FixedUnicodeSupport(metaData, false)
            ConsoleUnicodeSupport.Enable -> return UnicodeProxyConsoleMetaData.FixedUnicodeSupport(metaData, true)
            else -> return UnicodeProxyConsoleMetaData.FixedUnicodeSupport(metaData, true)
        }
    }

    private fun getConsoleMetaData(consoleUnicodeSupport: ConsoleUnicodeSupport): ConsoleMetaData? {
        val consoleDetector = NativeServices.getInstance().get<ConsoleDetector>(ConsoleDetector::class.java)
        val metaData = consoleDetector.console
        return createProxyingConsoleMetaData(
            if (metaData != null) metaData else FallbackConsoleMetaData.NOT_ATTACHED,
            consoleUnicodeSupport
        )
    }

    private fun configureAutoConsole(renderer: OutputEventRenderer, consoleMetaData: ConsoleMetaData, stdout: OutputStream?, stderr: OutputStream?) {
        if (consoleMetaData.isStdOutATerminal && consoleMetaData.isStdErrATerminal) {
            // Redirect stderr to stdout when both stdout and stderr are attached to a console. Assume that they are attached to the same console
            // This avoids interleaving problems when stdout and stderr end up at the same location
            val console = consoleForStdOut(stdout, consoleMetaData, renderer.getColourMap())
            renderer.addRichConsoleWithErrorOutputOnStdout(console, consoleMetaData, false)
        } else if (consoleMetaData.isStdOutATerminal) {
            // Write rich content to stdout and plain content to stderr
            val stdoutConsole = consoleForStdOut(stdout, consoleMetaData, renderer.getColourMap())
            renderer.addRichConsole(stdoutConsole, stderr, consoleMetaData, false)
        } else if (consoleMetaData.isStdErrATerminal) {
            // Write plain content to stdout and rich content to stderr
            val stderrConsole = consoleForStdErr(stderr, consoleMetaData, renderer.getColourMap())
            renderer.addRichConsole(stdout, stderrConsole, true)
        } else {
            renderer.addPlainConsole(stdout, stderr)
        }
    }

    private fun configurePlainConsole(renderer: OutputEventRenderer, consoleMetaData: ConsoleMetaData, stdout: OutputStream?, stderr: OutputStream?) {
        if (consoleMetaData.isStdOutATerminal && consoleMetaData.isStdErrATerminal) {
            // Redirect stderr to stdout when both stdout and stderr are attached to a console. Assume that they are attached to the same console
            // This avoids interleaving problems when stdout and stderr end up at the same location
            renderer.addPlainConsoleWithErrorOutputOnStdout(stdout)
        } else {
            renderer.addPlainConsole(stdout, stderr)
        }
    }

    private fun configureColoredConsole(renderer: OutputEventRenderer, consoleMetaData: ConsoleMetaData, stdout: OutputStream?, stderr: OutputStream?) {
        if (consoleMetaData.isStdOutATerminal && consoleMetaData.isStdErrATerminal) {
            // Redirect stderr to stdout when both stdout and stderr are attached to a console.
            // Assume that they are attached to the same console.
            // This avoids interleaving problems when stdout and stderr end up at the same location.
            val console = consoleForStdOut(stdout, consoleMetaData, renderer.getColourMap())
            renderer.addColoredConsoleWithErrorOutputOnStdout(console)
        } else {
            // Write colored content to both stdout and stderr
            val stdoutConsole = consoleForStdOut(stdout, consoleMetaData, renderer.getColourMap())
            val stderrConsole = consoleForStdErr(stderr, consoleMetaData, renderer.getColourMap())
            renderer.addColoredConsole(stdoutConsole, stderrConsole)
        }
    }

    private fun configureRichConsole(renderer: OutputEventRenderer, consoleMetaData: ConsoleMetaData, stdout: OutputStream?, stderr: OutputStream?, verbose: Boolean) {
        if (consoleMetaData.isStdOutATerminal && consoleMetaData.isStdErrATerminal) {
            // Redirect stderr to stdout when both stdout and stderr are attached to a console.
            // Assume that they are attached to the same console.
            // This avoids interleaving problems when stdout and stderr end up at the same location.
            val console = consoleForStdOut(stdout, consoleMetaData, renderer.getColourMap())
            renderer.addRichConsoleWithErrorOutputOnStdout(console, consoleMetaData, verbose)
        } else {
            // Write rich content to both stdout and stderr
            val stdoutConsole = consoleForStdOut(stdout, consoleMetaData, renderer.getColourMap())
            val stderrConsole = consoleForStdErr(stderr, consoleMetaData, renderer.getColourMap())
            renderer.addRichConsole(stdoutConsole, stderrConsole, consoleMetaData, verbose)
        }
    }

    private fun consoleFor(stream: OutputStream?, jansiFallback: Supplier<OutputStream?>, consoleMetaData: ConsoleMetaData, colourMap: ColorMap): Console {
        val force = !consoleMetaData.isWrapStreams

        // Use UTF-8 when terminal supports Unicode, otherwise use default charset
        val charset = if (consoleMetaData.supportsUnicode())
            StandardCharsets.UTF_8
        else
            Charset.defaultCharset()

        val writer = OutputStreamWriter(if (force) stream else jansiFallback.get(), charset)
        return AnsiConsole(writer, writer, colourMap, consoleMetaData, force)
    }

    private fun consoleForStdOut(stdout: OutputStream?, consoleMetaData: ConsoleMetaData, colourMap: ColorMap): Console {
        return consoleFor(stdout, Supplier { installJansiStream(org.fusesource.jansi.AnsiConsole.out()) }, consoleMetaData, colourMap)
    }

    private fun consoleForStdErr(stderr: OutputStream?, consoleMetaData: ConsoleMetaData, colourMap: ColorMap): Console {
        return consoleFor(stderr, Supplier { installJansiStream(org.fusesource.jansi.AnsiConsole.err()) }, consoleMetaData, colourMap)
    }

    /**
     * If any changes are made to the use of JANSI here, try out gradle on a windows CMD.EXE terminal.
     *
     * @return the installed ansiPrintStream
     */
    private fun installJansiStream(ansiPrintStream: AnsiPrintStream): OutputStream {
        try {
            ansiPrintStream.install()
        } catch (e: IOException) {
            // compiler appeasement, no exception should be thrown according to the jansi code
            throw UncheckedIOException(e)
        }
        return ansiPrintStream
    }
}
