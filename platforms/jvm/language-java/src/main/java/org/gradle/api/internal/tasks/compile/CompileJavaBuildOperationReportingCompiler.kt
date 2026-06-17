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
package org.gradle.api.internal.tasks.compile

import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessorResult
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType
import org.gradle.api.tasks.WorkResult
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.language.base.internal.compile.Compiler

class CompileJavaBuildOperationReportingCompiler(private val task: TaskInternal, private val delegate: Compiler<JavaCompileSpec?>, private val buildOperationRunner: BuildOperationRunner) :
    Compiler<JavaCompileSpec?> {
    override fun execute(spec: JavaCompileSpec?): WorkResult? {
        val taskIdentityPath = task.getIdentityPath().asString()
        return buildOperationRunner.call<WorkResult?>(CompileOperation(this.delegate, taskIdentityPath, spec))
    }

    private class CompileOperation(private val delegate: Compiler<JavaCompileSpec?>, private val taskIdentityPath: String?, private val spec: JavaCompileSpec?) : CallableBuildOperation<WorkResult?> {
        override fun description(): BuildOperationDescriptor.Builder {
            return BuildOperationDescriptor
                .displayName("Compile Java for " + taskIdentityPath) // Must not be a lambda for serialization reasons
                .details(object : CompileJavaBuildOperationType.Details {
                    override fun getTaskIdentityPath(): String? {
                        return taskIdentityPath
                    }
                })
        }

        override fun call(context: BuildOperationContext): WorkResult? {
            val result = delegate.execute(spec)
            context.setResult(toBuildOperationResult(result))
            return result
        }

        fun toBuildOperationResult(result: WorkResult?): Result {
            if (result is ApiCompilerResult) {
                val annotationProcessingResult = result.annotationProcessingResult
                val details: MutableList<CompileJavaBuildOperationType.Result.AnnotationProcessorDetails?> = ArrayList<CompileJavaBuildOperationType.Result.AnnotationProcessorDetails?>()
                for (processorResult in annotationProcessingResult.annotationProcessorResults) {
                    details.add(toAnnotationProcessorDetails(processorResult!!))
                }
                return Result(details)
            }
            return Result(null)
        }

        fun toAnnotationProcessorDetails(result: AnnotationProcessorResult): DefaultAnnotationProcessorDetails {
            return DefaultAnnotationProcessorDetails(result.className, toType(result.type), result.executionTimeInMillis)
        }

        fun toType(type: IncrementalAnnotationProcessorType?): CompileJavaBuildOperationType.Result.AnnotationProcessorDetails.Type {
            if (type == IncrementalAnnotationProcessorType.AGGREGATING) {
                return CompileJavaBuildOperationType.Result.AnnotationProcessorDetails.Type.AGGREGATING
            }
            if (type == IncrementalAnnotationProcessorType.ISOLATING) {
                return CompileJavaBuildOperationType.Result.AnnotationProcessorDetails.Type.ISOLATING
            }
            return CompileJavaBuildOperationType.Result.AnnotationProcessorDetails.Type.UNKNOWN
        }
    }

    private class Result(private val annotationProcessorDetails: MutableList<CompileJavaBuildOperationType.Result.AnnotationProcessorDetails?>?) : CompileJavaBuildOperationType.Result {
        override fun getAnnotationProcessorDetails(): MutableList<CompileJavaBuildOperationType.Result.AnnotationProcessorDetails?>? {
            return annotationProcessorDetails
        }
    }

    private class DefaultAnnotationProcessorDetails(
        private val className: String?,
        private val type: CompileJavaBuildOperationType.Result.AnnotationProcessorDetails.Type?,
        private val executionTimeInMillis: Long
    ) : CompileJavaBuildOperationType.Result.AnnotationProcessorDetails {
        override fun getClassName(): String? {
            return className
        }

        override fun getType(): CompileJavaBuildOperationType.Result.AnnotationProcessorDetails.Type? {
            return type
        }

        override fun getExecutionTimeInMillis(): Long {
            return executionTimeInMillis
        }
    }
}
