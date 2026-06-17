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
package org.gradle.api.internal.tasks.compile.tooling

import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.compile.CompileJavaBuildOperationType
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.build.event.OperationResultPostProcessor
import org.gradle.internal.build.event.types.AbstractTaskResult
import org.gradle.internal.build.event.types.DefaultAnnotationProcessorResult
import org.gradle.internal.build.event.types.DefaultJavaCompileTaskSuccessResult
import org.gradle.internal.build.event.types.DefaultTaskSuccessResult
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.tooling.internal.protocol.events.InternalJavaCompileTaskOperationResult
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class JavaCompileTaskSuccessResultPostProcessor : OperationResultPostProcessor {
    private val results: MutableMap<String?, CompileJavaBuildOperationType.Result?> = ConcurrentHashMap<String?, CompileJavaBuildOperationType.Result?>()

    override fun started(buildOperation: BuildOperationDescriptor?, startEvent: OperationStartEvent?) {
    }

    override fun finished(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent) {
        if (finishEvent.getResult() is CompileJavaBuildOperationType.Result) {
            val result = finishEvent.getResult() as CompileJavaBuildOperationType.Result
            val details = buildOperation.getDetails() as CompileJavaBuildOperationType.Details?
            checkNotNull(details) { "No details for " + buildOperation.getDisplayName() + ", which is required for proper result tracking" }
            results.put(details.getTaskIdentityPath(), result)
        }
    }

    override fun process(taskResult: AbstractTaskResult?, taskInternal: TaskInternal): AbstractTaskResult? {
        val compileResult = results.remove(taskInternal.getIdentityPath().asString())
        if (taskResult is DefaultTaskSuccessResult) {
            if (compileResult != null) {
                return DefaultJavaCompileTaskSuccessResult(taskResult, toAnnotationProcessorResults(compileResult.getAnnotationProcessorDetails()))
            } else if (taskInternal is JavaCompile) {
                LOGGER!!.info("No compile result for " + taskInternal.getIdentityPath())
            }
        }
        return taskResult
    }

    private fun toAnnotationProcessorResults(allDetails: MutableList<CompileJavaBuildOperationType.Result.AnnotationProcessorDetails>?): MutableList<InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult?>? {
        if (allDetails == null) {
            return null
        }
        val results: MutableList<InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult?> =
            ArrayList<InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult?>(allDetails.size)
        for (details in allDetails) {
            results.add(toAnnotationProcessorResult(details))
        }
        return results
    }

    private fun toAnnotationProcessorResult(details: CompileJavaBuildOperationType.Result.AnnotationProcessorDetails): InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult {
        return DefaultAnnotationProcessorResult(details.getClassName(), toAnnotationProcessorType(details.getType()), Duration.ofMillis(details.getExecutionTimeInMillis()))
    }

    private fun toAnnotationProcessorType(type: CompileJavaBuildOperationType.Result.AnnotationProcessorDetails.Type): String {
        when (type) {
            CompileJavaBuildOperationType.Result.AnnotationProcessorDetails.Type.AGGREGATING -> return InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult.TYPE_AGGREGATING
            CompileJavaBuildOperationType.Result.AnnotationProcessorDetails.Type.ISOLATING -> return InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult.TYPE_ISOLATING
            CompileJavaBuildOperationType.Result.AnnotationProcessorDetails.Type.UNKNOWN -> return InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult.TYPE_UNKNOWN
        }
        throw IllegalArgumentException("Missing conversion for enum constant " + type)
    }

    companion object {
        private val LOGGER = getLogger(JavaCompileTaskSuccessResultPostProcessor::class.java)
    }
}
