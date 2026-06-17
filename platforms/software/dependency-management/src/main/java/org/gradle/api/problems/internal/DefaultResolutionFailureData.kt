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
package org.gradle.api.problems.internal

import org.gradle.internal.component.resolution.failure.interfaces.ResolutionFailure

/**
 * Default implementation of [ResolutionFailureData].
 */
class DefaultResolutionFailureData(private val resolutionFailure: ResolutionFailure?) : ResolutionFailureData {
    override fun getResolutionFailure(): ResolutionFailure? {
        return resolutionFailure
    }

    private class DefaultResolutionFailureDataBuilder : ResolutionFailureDataSpec, AdditionalDataBuilder<ResolutionFailureData?> {
        private var failure: ResolutionFailure? = null

        constructor(from: ResolutionFailureData) {
            this.failure = from.getResolutionFailure()
        }

        constructor()

        override fun from(failure: ResolutionFailure?): ResolutionFailureDataSpec {
            this.failure = failure
            return this
        }

        override fun build(): ResolutionFailureData {
            return DefaultResolutionFailureData(failure)
        }
    }

    companion object {
        fun builder(resolutionFailure: ResolutionFailureData?): AdditionalDataBuilder<ResolutionFailureData?> {
            if (resolutionFailure == null) {
                return DefaultResolutionFailureDataBuilder()
            }
            return DefaultResolutionFailureDataBuilder(resolutionFailure)
        }
    }
}
