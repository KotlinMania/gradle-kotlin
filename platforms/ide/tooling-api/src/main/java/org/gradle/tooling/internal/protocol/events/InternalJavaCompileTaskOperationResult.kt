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
package org.gradle.tooling.internal.protocol.events

import java.time.Duration
import org.gradle.tooling.internal.protocol.InternalProtocolInterface

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 *
 * @since 5.1
 */
interface InternalJavaCompileTaskOperationResult : InternalTaskResult {
    val annotationProcessorResults: MutableList<InternalAnnotationProcessorResult?>?

    interface InternalAnnotationProcessorResult : InternalProtocolInterface {
        val className: String?

        val type: String?

        val duration: Duration?

        companion object {
            const val TYPE_ISOLATING: String = "ISOLATING"
            const val TYPE_AGGREGATING: String = "AGGREGATING"
            const val TYPE_UNKNOWN: String = "UNKNOWN"
        }
    }
}
