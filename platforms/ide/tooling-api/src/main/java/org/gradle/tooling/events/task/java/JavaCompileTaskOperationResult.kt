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
package org.gradle.tooling.events.task.java

import java.time.Duration
import org.gradle.tooling.events.task.TaskOperationResult

/**
 * Describes the result of a `JavaCompile` task.
 *
 *
 * Currently, this result is only reported for successful tasks.
 *
 * @since 5.1
 */
interface JavaCompileTaskOperationResult : TaskOperationResult {
    /**
     * Returns results of used annotation processors, if available.
     *
     *
     * Details are only available if an instrumented compiler was used.
     *
     * @return details about used annotation processors; `null` if unknown.
     */
    val annotationProcessorResults: MutableList<AnnotationProcessorResult?>?

    /**
     * The results of an annotation processor used during compilation.
     *
     * @since 5.1
     */
    interface AnnotationProcessorResult {
        /**
         * Returns the fully-qualified class name of this annotation processor.
         */
        val className: String?

        /**
         * Returns the type of this annotation processor.
         *
         *
         * Can be used to determine whether this processor was incremental.
         */
        val type: Type?

        /**
         * Returns the total execution time of this annotation processor.
         */
        val duration: Duration?

        /**
         * Type of annotation processor.
         *
         * @since 5.1
         */
        enum class Type {
            ISOLATING,
            AGGREGATING,
            UNKNOWN
        }
    }
}
