/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.tooling.internal.provider.runner

import com.google.common.collect.ImmutableList
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor
import java.util.concurrent.ConcurrentHashMap

/**
 * Generates progress events to send back to the client,
 */
class ClientBuildEventGenerator(
    progressEventConsumer: ProgressEventConsumer,
    subscriptions: BuildEventSubscriptions?,
    mappers: MutableList<out BuildOperationMapper<*, *>>,
    private val nonMappedBuildEventGenerator: BuildOperationListener
) : BuildOperationListener {
    private val mappers: MutableList<Mapper>
    private val trackers: MutableList<BuildOperationTracker>
    private val running: MutableMap<OperationIdentifier?, Operation?> = ConcurrentHashMap<OperationIdentifier?, Operation?>()

    private fun collectTrackers(dest: MutableSet<BuildOperationTracker?>, trackers: MutableList<out BuildOperationTracker>) {
        for (tracker in trackers) {
            if (!dest.contains(tracker)) {
                collectTrackers(dest, tracker.getTrackers())
                dest.add(tracker)
            }
        }
    }

    override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent) {
        for (tracker in trackers) {
            tracker.started(buildOperation, startEvent)
        }
        for (mapper in mappers) {
            val operation = mapper.accept(buildOperation)
            if (operation != null) {
                val previous = running.put(buildOperation.getId(), operation)
                check(previous == null) { "Operation " + buildOperation.getId() + " already started." }
                operation.generateStartEvent(buildOperation, startEvent)
                return
            }
        }
        // Not recognized, so generate generic events, if appropriate
        nonMappedBuildEventGenerator.started(buildOperation, startEvent)
    }

    override fun progress(operationIdentifier: OperationIdentifier, progressEvent: OperationProgressEvent) {
        val operation = running.get(operationIdentifier)
        if (operation != null) {
            operation.progress(progressEvent)
        }
        // For start and finish events we either emit a mapped or a non-mapped event. For progress events we are not doing mapping, but we emit additional progress events. Therefore, we are not
        // returning in the if statement above.
        nonMappedBuildEventGenerator.progress(operationIdentifier, progressEvent)
    }

    override fun finished(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent) {
        for (tracker in trackers) {
            tracker.finished(buildOperation, finishEvent)
        }
        val operation = running.remove(buildOperation.getId())
        if (operation != null) {
            operation.generateFinishEvent(buildOperation, finishEvent)
        } else {
            // Not recognized, so generate generic events, if appropriate
            nonMappedBuildEventGenerator.finished(buildOperation, finishEvent)
        }
        for (tracker in trackers) {
            tracker.discardState(buildOperation)
        }
    }

    private abstract class Operation {
        abstract fun generateStartEvent(buildOperation: BuildOperationDescriptor?, startEvent: OperationStartEvent?)

        abstract fun generateFinishEvent(buildOperation: BuildOperationDescriptor?, finishEvent: OperationFinishEvent?)

        abstract fun progress(progressEvent: OperationProgressEvent?)
    }

    private class EnabledOperation(
        private val descriptor: InternalOperationDescriptor?,
        private val mapper: BuildOperationMapper<Any?, InternalOperationDescriptor?>,
        private val progressEventConsumer: ProgressEventConsumer
    ) : Operation() {
        override fun generateStartEvent(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent?) {
            progressEventConsumer.started(mapper.createStartedEvent(descriptor, buildOperation.getDetails(), startEvent))
        }

        override fun progress(progressEvent: OperationProgressEvent?) {
            val mapped = mapper.createProgressEvent(descriptor, progressEvent)
            if (mapped != null) {
                progressEventConsumer.progress(mapped)
            }
        }

        override fun generateFinishEvent(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent?) {
            progressEventConsumer.finished(mapper.createFinishedEvent(descriptor, buildOperation.getDetails(), finishEvent))
        }
    }

    init {
        val mapperBuilder: MutableList<Mapper?> = ArrayList<Mapper?>(mappers.size)
        val trackers: MutableSet<BuildOperationTracker?> = LinkedHashSet<BuildOperationTracker?>()
        for (mapper in mappers) {
            if (mapper.isEnabled(subscriptions)) {
                mapperBuilder.add(Enabled(mapper, progressEventConsumer))
                collectTrackers(trackers, mapper.getTrackers())
            } else {
                mapperBuilder.add(Disabled(mapper))
            }
        }
        this.mappers = ImmutableList.copyOf<Mapper?>(mapperBuilder)
        this.trackers = ImmutableList.copyOf<BuildOperationTracker?>(trackers)
    }

    private abstract class Mapper {
        abstract fun accept(buildOperation: BuildOperationDescriptor?): Operation?
    }

    private class Enabled(mapper: BuildOperationMapper<*, *>, private val progressEventConsumer: ProgressEventConsumer) : Mapper() {
        private val mapper: BuildOperationMapper<Any?, InternalOperationDescriptor?>?
        private val detailsType: Class<*>

        init {
            this.mapper = uncheckedCast<BuildOperationMapper<Any?, InternalOperationDescriptor?>?>(mapper)
            this.detailsType = mapper.getDetailsType()
        }

        override fun accept(buildOperation: BuildOperationDescriptor): Operation? {
            if (detailsType.isInstance(buildOperation.getDetails())) {
                val parentId = progressEventConsumer.findStartedParentId(buildOperation)
                val descriptor = mapper!!.createDescriptor(buildOperation.getDetails(), buildOperation, parentId)
                return ClientBuildEventGenerator.EnabledOperation(descriptor, mapper, progressEventConsumer)
            } else {
                return null
            }
        }
    }

    private class Disabled(mapper: BuildOperationMapper<*, *>) : Mapper() {
        private val detailsType: Class<*>

        init {
            this.detailsType = mapper.getDetailsType()
        }

        override fun accept(buildOperation: BuildOperationDescriptor): Operation? {
            if (detailsType.isInstance(buildOperation.getDetails())) {
                return DISABLED_OPERATION
            } else {
                return null
            }
        }
    }

    companion object {
        private val DISABLED_OPERATION: Operation = object : Operation() {
            override fun generateStartEvent(buildOperation: BuildOperationDescriptor?, startEvent: OperationStartEvent?) {
            }

            override fun progress(progressEvent: OperationProgressEvent?) {
            }

            override fun generateFinishEvent(buildOperation: BuildOperationDescriptor?, finishEvent: OperationFinishEvent?) {
            }
        }
    }
}
