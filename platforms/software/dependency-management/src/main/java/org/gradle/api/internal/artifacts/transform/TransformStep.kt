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
package org.gradle.api.internal.artifacts.transform

import com.google.common.collect.ImmutableList
import org.gradle.api.Describable
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.Deferrable
import org.gradle.internal.Try
import org.gradle.internal.execution.InputFingerprinter
import java.io.File
import java.util.function.Function

/**
 * A single transform step in a transform chain.
 *
 *
 * Transforms a subject by invoking a transform on each of the subjects files.
 *
 * @see TransformChain
 */
class TransformStep(
    val transform: Transform,
    private val transformInvocationFactory: TransformInvocationFactory,
    owner: DomainObjectContext,
    private val globalInputFingerprinter: InputFingerprinter
) : TaskDependencyContainer, Describable {
    private val owningProject: ProjectInternal

    init {
        this.owningProject = owner.getProject()!!
    }

    fun getOwningProject(): ProjectInternal? {
        return owningProject
    }

    fun createInvocation(subjectToTransform: TransformStepSubject, upstreamDependencies: TransformUpstreamDependencies, context: NodeExecutionContext?): Deferrable<Try<TransformStepSubject>?> {
        val inputFingerprinter = if (context != null) context.getService<InputFingerprinter>(InputFingerprinter::class.java) else globalInputFingerprinter

        val resolvedDependencies: Try<TransformDependencies?> = upstreamDependencies.computeArtifacts()
        return resolvedDependencies
            .map<Any?>(java.util.function.Function { dependencies: org.gradle.api.internal.artifacts.transform.TransformDependencies? ->
                val inputArtifacts: com.google.common.collect.ImmutableList<java.io.File> = subjectToTransform.getFiles()
                if (inputArtifacts.isEmpty()) {
                    return@map org.gradle.internal.Deferrable.completed(org.gradle.internal.Try.successful(subjectToTransform.createSubjectFromResult(com.google.common.collect.ImmutableList.of<java.io.File>())))
                } else if (inputArtifacts.size > 1) {
                    return@map org.gradle.internal.Deferrable.deferred({ doTransform(subjectToTransform, inputFingerprinter, dependencies, inputArtifacts) }
                    )
                } else {
                    val inputArtifact: java.io.File = inputArtifacts.get(0)
                    return@map transformInvocationFactory.createInvocation(transform, inputArtifact, dependencies, subjectToTransform, inputFingerprinter)
                        .map<org.gradle.internal.Try<org.gradle.api.internal.artifacts.transform.TransformStepSubject?>?>(java.util.function.Function { result: org.gradle.internal.Try<com.google.common.collect.ImmutableList<java.io.File?>?>? ->
                            result.map<org.gradle.api.internal.artifacts.transform.TransformStepSubject?>(
                                java.util.function.Function { result: com.google.common.collect.ImmutableList<java.io.File?>? -> subjectToTransform.createSubjectFromResult(result) })
                        })
                }

            })!!
            .getOrMapFailure(Function { failure: Throwable? -> Deferrable.completed(Try.failure<U?>(failure)) })
    }

    private fun doTransform(
        subjectToTransform: TransformStepSubject,
        inputFingerprinter: InputFingerprinter,
        dependencies: TransformDependencies,
        inputArtifacts: ImmutableList<File>
    ): Try<TransformStepSubject?> {
        val builder = ImmutableList.builder<File>()
        for (inputArtifact in inputArtifacts) {
            val result: Try<ImmutableList<File>?>? = transformInvocationFactory
                .createInvocation(transform, inputArtifact, dependencies, subjectToTransform, inputFingerprinter)
                .completeAndGet()

            if (result!!.failure!!.isPresent()) {
                return uncheckedCast<Try<TransformStepSubject?>?>(result)!!
            }
            builder.addAll(result.get()!!)
        }
        return Try.successful(subjectToTransform.createSubjectFromResult(builder.build()))
    }

    fun isolateParametersIfNotAlready() {
        transform.isolateParametersIfNotAlready()
    }

    fun requiresDependencies(): Boolean {
        return transform.requiresDependencies()
    }

    override fun getDisplayName(): String {
        return transform.getDisplayName()
    }

    val fromAttributes: ImmutableAttributes
        get() = transform.getFromAttributes()

    override fun toString(): String {
        return transform.getDisplayName()
    }

    override fun visitDependencies(context: TaskDependencyResolveContext) {
        transform.visitDependencies(context)
    }
}
