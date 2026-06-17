/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling.events.task.internal

import org.gradle.tooling.events.internal.DefaultStartEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.task.TaskStartEvent

/**
 * Implementation of the `TaskStartEvent` interface.
 */
class DefaultTaskStartEvent(eventTime: Long, displayName: String?, descriptor: TaskOperationDescriptor?) : DefaultStartEvent(eventTime, displayName, descriptor), TaskStartEvent {
    override fun getDescriptor(): TaskOperationDescriptor? {
        return super.getDescriptor() as TaskOperationDescriptor?
    }
}
