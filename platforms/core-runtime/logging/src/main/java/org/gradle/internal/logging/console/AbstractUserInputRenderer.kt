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
package org.gradle.internal.logging.console

import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.internal.logging.events.EndOutputEvent
import org.gradle.internal.logging.events.FlushOutputEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.PromptOutputEvent
import org.gradle.internal.logging.events.ReadStdInEvent
import org.gradle.internal.logging.events.RenderableOutputEvent
import org.gradle.internal.logging.events.UpdateNowEvent
import org.gradle.internal.logging.events.UserInputRequestEvent
import org.gradle.internal.logging.events.UserInputResumeEvent
import org.gradle.internal.logging.events.UserInputValidationProblemEvent
import org.gradle.internal.logging.serializer.OutputEventSerializer
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.Deque
import java.util.Objects

abstract class AbstractUserInputRenderer(@JvmField protected val delegate: OutputEventListener, private val userInput: GlobalUserInputReceiver, private val temporaryFileProvider: TemporaryFileProvider) :
    OutputEventListener {
    private val outputEventSerializer: Serializer<OutputEvent>
    val eventQueue: Deque<OutputEvent> = ArrayDeque<OutputEvent>(MEMORY_QUEUE_LIMIT)

    private var overflowFile: File? = null
    private var overflowEncoder: KryoBackedEncoder? = null
    private var overflowEventCount = 0
    private var paused = false
    private var overflowFailed = false

    init {
        this.outputEventSerializer = OutputEventSerializer.create()
    }

    override fun onOutput(event: OutputEvent) {
        if (event is UserInputRequestEvent) {
            handleUserInputRequestEvent()
            return
        } else if (event is UserInputResumeEvent) {
            handleUserInputResumeEvent(event)
            return
        } else if (event is PromptOutputEvent) {
            handlePromptOutputEvent(event)
            return
        } else if (event is UserInputValidationProblemEvent) {
            handleValidationProblemEvent(event)
            return
        } else if (event is ReadStdInEvent) {
            userInput.readAndForwardStdin(event)
            return
        }

        if (paused) {
            if (event is UpdateNowEvent
                || event is FlushOutputEvent
                || event is EndOutputEvent
            ) {
                return
            }
            bufferEvent(event)
            return
        }

        delegate.onOutput(event)
    }

    private fun bufferEvent(event: OutputEvent) {
        if (eventQueue.size < MEMORY_QUEUE_LIMIT) {
            eventQueue.add(event)
        } else {
            writeToOverflow(event)
        }
    }

    private fun writeToOverflow(event: OutputEvent) {
        if (overflowFailed) {
            eventQueue.add(event)
            return
        }
        if (overflowFile != null && overflowFile!!.length() >= MAX_OVERFLOW_FILE_SIZE) {
            cleanupOverflow()
            throw IllegalStateException("User input overflow file exceeded " + MAX_OVERFLOW_FILE_SIZE + " bytes, aborting to prevent filling up the disk")
        }
        try {
            if (overflowEncoder == null) {
                overflowFile = temporaryFileProvider.createTemporaryFile("user-input-overflow-", ".bin")
                overflowFile!!.deleteOnExit()
                overflowEncoder = KryoBackedEncoder(Files.newOutputStream(overflowFile!!.toPath()))
            }
            outputEventSerializer.write(overflowEncoder, event)
            overflowEventCount++
        } catch (e: Exception) {
            LOGGER.warn("Failed to write overflow event to disk, falling back to in-memory buffer", e)
            overflowFailed = true
            closeOverflowEncoder()
            eventQueue.add(event)
        }
    }

    val bufferedEventCount: Int
        get() = eventQueue.size + overflowEventCount

    private fun handleValidationProblemEvent(event: UserInputValidationProblemEvent) {
        handlePrompt(event)
    }

    private fun handlePromptOutputEvent(event: PromptOutputEvent) {
        // Start capturing input prior to displaying the prompt so that the input received after the prompt is displayed will be captured.
        // This does leave a small window where some text may be captured prior to the prompt being fully displayed, however this is
        // better than doing things in the other order, where there will be a small window where text may not be captured after prompt is fully displayed.
        // This is only a problem for tooling; for a human (the audience for this feature) this makes no difference.
        userInput.readAndForwardText(event)
        handlePrompt(event)
    }

    private fun handleUserInputRequestEvent() {
        startInput()
        paused = true
    }

    private fun handleUserInputResumeEvent(event: UserInputResumeEvent) {
        check(paused) { "Cannot resume user input if not paused yet" }

        paused = false
        finishInput(event)
        replayEvents()
    }

    private fun replayEvents() {
        while (!eventQueue.isEmpty()) {
            delegate.onOutput(eventQueue.pop())
        }

        if (overflowFile != null) {
            try {
                replayOverflowFromDisk()
            } finally {
                cleanupOverflow()
            }
        } else {
            // Reset overflow failure flag even when no file was created,
            // so the next pause/resume cycle can attempt disk overflow again.
            overflowFailed = false
        }
    }

    private fun replayOverflowFromDisk() {
        try {
            closeOverflowEncoder()
            var replayedCount = 0
            try {
                FileInputStream(Objects.requireNonNull<File?>(overflowFile)).use { fis ->
                    KryoBackedDecoder(fis).use { decoder ->
                        while (true) {
                            val event = outputEventSerializer.read(decoder)
                            delegate.onOutput(event)
                            replayedCount++
                        }
                    }
                }
            } catch (e: EOFException) {
                if (replayedCount != overflowEventCount) {
                    LOGGER.warn("Overflow file may be corrupt: expected {} events but replayed {}", overflowEventCount, replayedCount)
                }
            }
        } catch (e: Exception) {
            LOGGER.warn("Failed to replay overflow events from disk", e)
        }
    }

    private fun closeOverflowEncoder() {
        if (overflowEncoder != null) {
            try {
                overflowEncoder!!.close()
            } catch (e: Exception) {
                LOGGER.warn("Failed to close overflow encoder", e)
            }
            overflowEncoder = null
        }
    }

    private fun cleanupOverflow() {
        closeOverflowEncoder()
        if (overflowFile != null) {
            overflowFile!!.delete()
            overflowFile = null
        }
        overflowEventCount = 0
        overflowFailed = false
    }

    abstract fun startInput()

    abstract fun handlePrompt(event: RenderableOutputEvent)

    abstract fun finishInput(event: RenderableOutputEvent)

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(AbstractUserInputRenderer::class.java)
        private val MAX_OVERFLOW_FILE_SIZE = 1024L * 1024L * 1024L // 1GB

        const val MEMORY_QUEUE_LIMIT: Int = 2500
    }
}
