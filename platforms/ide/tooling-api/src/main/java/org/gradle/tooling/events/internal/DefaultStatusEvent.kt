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
package org.gradle.tooling.events.internal

import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.StatusEvent

/**
 * Base implementation of the `StatusEvent` interface.
 */
class DefaultStatusEvent(eventTime: Long, descriptor: OperationDescriptor, private val total: Long, private val progress: Long, private val unit: String?) :
    BaseProgressEvent(eventTime, descriptor.displayName, descriptor), StatusEvent {
    override fun getDisplayName(): String {
        return getDescriptor().displayName + " " + progress + "/" + total + " " + unit + " completed"
    }

    override fun getProgress(): Long {
        return progress
    }

    override fun getTotal(): Long {
        return total
    }

    override fun getUnit(): String? {
        return unit
    }
}
