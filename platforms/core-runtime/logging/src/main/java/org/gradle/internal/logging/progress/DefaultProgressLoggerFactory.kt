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
package org.gradle.internal.logging.progress

import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationIdFactory
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.time.Clock
import org.gradle.util.internal.GUtil

class DefaultProgressLoggerFactory(private val progressListener: ProgressListener, private val clock: Clock, private val buildOperationIdFactory: BuildOperationIdFactory) : ProgressLoggerFactory {
    private val current = ThreadLocal<ProgressLoggerImpl>()
    private val currentBuildOperationRef = CurrentBuildOperationRef.instance()

    override fun newOperation(loggerCategory: Class<*>?): ProgressLogger {
        return newOperation(loggerCategory!!.getName())
    }

    override fun newOperation(loggerCategory: Class<*>?, buildOperationDescriptor: BuildOperationDescriptor?): ProgressLogger {
        val buildOperationDescriptor = buildOperationDescriptor!!
        var category = ProgressStartEvent.BUILD_OP_CATEGORY
        val metadata = buildOperationDescriptor.getMetadata()
        val buildOperationCategory = BuildOperationCategory.toCategory(metadata)
        if (buildOperationCategory == BuildOperationCategory.TASK) {
            // This is a legacy quirk.
            // Scans use this to determine that progress logging is indicating start/finish of tasks.
            // This can be removed in Gradle 5.0 (along with the concept of a "logging category" of an operation)
            category = ProgressStartEvent.TASK_CATEGORY
        }

        val logger = ProgressLoggerImpl(
            null,
            buildOperationDescriptor.getId(),
            category,
            progressListener,
            clock,
            true,
            buildOperationDescriptor.getId(),
            buildOperationDescriptor.getParentId(),
            buildOperationCategory
        )
        logger.totalProgress = buildOperationDescriptor.getTotalProgress()

        // Make some assumptions about the console output
        if (buildOperationCategory.isTopLevelWorkItem()) {
            logger.loggingHeader = buildOperationDescriptor.getProgressDisplayName()
        }

        return logger
    }

    override fun newOperation(loggerCategory: String?): ProgressLogger {
        return init(loggerCategory!!, null)
    }

    override fun newOperation(loggerClass: Class<*>?, parent: ProgressLogger?): ProgressLogger {
        return init(loggerClass.toString(), parent)
    }

    private fun init(
        loggerCategory: String,
        parentOperation: ProgressLogger?
    ): ProgressLogger {
        require(!(parentOperation != null && parentOperation !is ProgressLoggerImpl)) { "Unexpected parent logger." }
        val currentBuildOperation = currentBuildOperationRef.get()
        return ProgressLoggerImpl(
            parentOperation,
            OperationIdentifier(buildOperationIdFactory.nextId()),
            loggerCategory,
            progressListener,
            clock,
            false,
            if (currentBuildOperation != null) currentBuildOperation.getId() else null,
            if (currentBuildOperation != null) currentBuildOperation.getParentId() else null,
            null
        )
    }

    private enum class State {
        idle, started, completed
    }

    private inner class ProgressLoggerImpl(
        private var parent: ProgressLoggerImpl?,
        private val progressOperationId: OperationIdentifier?,
        private val category: String,
        private val listener: ProgressListener,
        private val clock: Clock,
        private val buildOperationStart: Boolean,
        private val buildOperationId: OperationIdentifier?,
        private val parentBuildOperationId: OperationIdentifier?,
        private val buildOperationCategory: BuildOperationCategory?
    ) : ProgressLogger {
        private var previous: ProgressLoggerImpl? = null
        private var descriptionValue: String? = null
        var loggingHeader: String? = null
        private var state = State.idle
        var totalProgress = 0
        override val description: String?
            get() = descriptionValue

        override fun toString(): String {
            return category + " - " + descriptionValue
        }

        override fun setDescription(description: String?): ProgressLogger {
            assertCanConfigure()
            this.descriptionValue = description
            return this
        }

        override fun start(description: String?, status: String?): ProgressLogger {
            setDescription(description)
            started(status)
            return this
        }

        override fun started() {
            started(null)
        }

        override fun started(status: String?) {
            started(status, totalProgress)
        }

        fun started(status: String?, totalProgress: Int) {
            check(GUtil.isTrue(descriptionValue)) { "A description must be specified before this operation is started." }
            assertNotStarted()
            state = State.started
            previous = current.get()
            val parentProgressId: OperationIdentifier?
            if (parent == null) {
                if (previous != null) {
                    parent = previous
                    parentProgressId = parent!!.progressOperationId
                } else if (buildOperationStart) {
                    parentProgressId = parentBuildOperationId
                } else {
                    parentProgressId = buildOperationId
                }
            } else {
                parentProgressId = parent!!.progressOperationId
                parent!!.assertRunning()
            }
            current.set(this)
            listener.started(
                ProgressStartEvent(
                    progressOperationId,
                    parentProgressId,
                    clock.currentTime,
                    category,
                    descriptionValue!!,
                    loggingHeader,
                    ensureNotNull(status),
                    totalProgress,
                    buildOperationStart,
                    buildOperationId,
                    buildOperationCategory
                )
            )
        }

        override fun progress(status: String?) {
            progress(status, false)
        }

        override fun progress(status: String?, failing: Boolean) {
            assertRunning()
            listener.progress(ProgressEvent(progressOperationId!!, ensureNotNull(status), failing))
        }

        override fun completed() {
            completed(null, false)
        }

        override fun completed(status: String?, failed: Boolean) {
            assertRunning()
            state = State.completed
            current.set(previous)
            listener.completed(ProgressCompleteEvent(progressOperationId!!, clock.currentTime, ensureNotNull(status), failed))
        }

        fun ensureNotNull(status: String?): String {
            return if (status == null) "" else status
        }

        fun assertNotStarted() {
            check(state != State.started) { String.format("This operation (%s) has already been started.", this) }
            check(state != State.completed) { String.format("This operation (%s) has already completed.", this) }
        }

        fun assertRunning() {
            check(state != State.idle) { String.format("This operation (%s) has not been started.", this) }
            check(state != State.completed) { String.format("This operation (%s) has already been completed.", this) }
        }

        fun assertCanConfigure() {
            check(state == State.idle) { String.format("Cannot configure this operation (%s) once it has started.", this) }
        }
    }
}
