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
package org.gradle.launcher.exec

import org.gradle.execution.MultipleBuildFailures
import org.gradle.internal.UncheckedException
import org.gradle.internal.buildtree.BuildActionRunner
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.problems.failure.FailureFactory

/**
 * An [BuildActionRunner] that notifies the GE plugin manager that the build has completed.
 */
class BuildCompletionNotifyingBuildActionRunner(
    private val gradleEnterprisePluginManager: GradleEnterprisePluginManager,
    private val failureFactory: FailureFactory,
    private val buildOperationRunner: BuildOperationRunner,
    private val delegate: BuildActionRunner
) : BuildActionRunner {
    override fun run(action: BuildAction, buildController: BuildTreeLifecycleController): BuildActionRunner.Result {
        val result: BuildActionRunner.Result
        try {
            result = delegate.run(action, buildController)
        } catch (t: Throwable) {
            // Note: throw the failure rather than returning a result object containing the failure, as console failure logging based on the _result_ happens down in the root build scope
            // whereas console failure logging based on the _thrown exception_ happens up outside session scope. It would be better to refactor so that a result can be returned from here
            notifyEnterprisePluginManager(BuildActionRunner.Result.failed(t, failureFactory.create(t)))
            throw UncheckedException.throwAsUncheckedException(t)
        }
        notifyEnterprisePluginManager(result)
        return result
    }

    private fun notifyEnterprisePluginManager(result: BuildActionRunner.Result) {
        // Validate the invariant, but avoid failing in production to allow Develocity to receive _a_ result
        // to provide a better user experience in the face of a bug on the Gradle side
        assert(
            result.getBuildFailure() == null || result.getRichBuildFailure() != null
        ) { "Rich build failure must not be null when build failure is present. Build failure: " + result.getBuildFailure() }
        val unwrappedBuildFailure: MutableList<Failure>? = unwrapBuildFailure(result.getRichBuildFailure())
        buildOperationRunner.run(object : RunnableBuildOperation {
            override fun run(context: BuildOperationContext) {
                gradleEnterprisePluginManager.buildFinished(result.getBuildFailure(), unwrappedBuildFailure)
            }

            override fun description(): BuildOperationDescriptor.Builder {
                return BuildOperationDescriptor.displayName("Develocity plugin build finished")
                    .details(DevelocityPluginBuildFinishedBuildOperationType.Companion.DETAILS)
            }
        })
    }

    /**
     * This build operation is for making the Develocity build finished callback visible in build operation traces.
     *
     * Note that currently you cannot measure this build operation in Gradle profiler, since the
     * BuildService responsible for measuring it has already been closed when the build operation fires.
     */
    interface DevelocityPluginBuildFinishedBuildOperationType : BuildOperationType<DevelocityPluginBuildFinishedBuildOperationType.Details, DevelocityPluginBuildFinishedBuildOperationType.Result> {
        interface Details

        interface Result
        companion object {
            val DETAILS: Details = object : Details {}
        }
    }

    companion object {
        private fun unwrapBuildFailure(richBuildFailure: Failure?): MutableList<Failure>? {
            if (richBuildFailure == null) {
                // No build failure
                return null
            }
            return if (richBuildFailure.original is MultipleBuildFailures)
                richBuildFailure.causes
            else mutableListOf<Failure>(richBuildFailure)
        }
    }
}
