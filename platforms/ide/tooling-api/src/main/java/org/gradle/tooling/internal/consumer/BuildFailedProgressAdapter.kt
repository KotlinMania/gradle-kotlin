/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.tooling.internal.consumer

import org.gradle.tooling.Failure
import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.jspecify.annotations.NullMarked

@NullMarked
class BuildFailedProgressAdapter : ProgressListener {
    var failures: MutableList<Failure> = ArrayList<Failure>()

    override fun statusChanged(event: ProgressEvent?) {
        if (event != null && isBuildFinishedEvent(event)) {
            val result = (event as FinishEvent).result
            if (result is FailureResult) {
                this.failures = ArrayList<Failure>(result.failures!!.filterNotNull())
            } else {
                this.failures = mutableListOf<Failure>()
            }
        }
    }

    companion object {
        private fun isBuildFinishedEvent(event: ProgressEvent): Boolean {
            return event is FinishEvent && event.descriptor!!.parent == null
        }
    }
}
