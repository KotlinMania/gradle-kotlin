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
package org.gradle.tooling.events.task.internal.java

import org.gradle.tooling.events.task.internal.DefaultTaskSuccessResult
import org.gradle.tooling.events.task.internal.TaskExecutionDetails
import org.gradle.tooling.events.task.java.JavaCompileTaskOperationResult

class DefaultJavaCompileTaskSuccessResult(
    startTime: Long,
    endTime: Long,
    upToDate: Boolean,
    fromCache: Boolean,
    taskExecutionDetails: TaskExecutionDetails?,
    override val annotationProcessorResults: MutableList<JavaCompileTaskOperationResult.AnnotationProcessorResult?>?
) : DefaultTaskSuccessResult(startTime, endTime, upToDate, fromCache, taskExecutionDetails ?: TaskExecutionDetails.unsupported()), JavaCompileTaskOperationResult {

}
