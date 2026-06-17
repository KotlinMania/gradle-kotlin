/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.operations.BuildOperationAncestryTracker
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent
import org.jspecify.annotations.NullMarked
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

@NullMarked
internal class ProgressEventConsumer(private val delegate: BuildEventConsumer, private val ancestryTracker: BuildOperationAncestryTracker) {
    private val startedIds: MutableSet<Any> = ConcurrentHashMap.newKeySet<Any>()

    fun findStartedParentId(operation: BuildOperationDescriptor): OperationIdentifier? {
        return ancestryTracker
            .findClosestMatchingAncestor(operation.getParentId(), Predicate { o: OperationIdentifier? -> startedIds.contains(o) })
            .orElse(null)
    }

    fun findStartedParentId(identifier: OperationIdentifier): OperationIdentifier? {
        return ancestryTracker
            .findClosestMatchingAncestor(identifier, Predicate { o: OperationIdentifier? -> startedIds.contains(o) })
            .orElse(null)
    }

    fun started(event: InternalOperationStartedProgressEvent) {
        delegate.dispatch(event)
        startedIds.add(event.descriptor!!.id!!)
    }

    fun finished(event: InternalOperationFinishedProgressEvent) {
        startedIds.remove(event.descriptor!!.id)
        delegate.dispatch(event)
    }

    fun progress(event: InternalProgressEvent) {
        delegate.dispatch(event)
    }
}
