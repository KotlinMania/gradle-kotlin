/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.enterprise.impl

import org.gradle.api.problems.internal.BuildOperationProblem
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.internal.enterprise.GradleEnterprisePluginBuildState
import org.gradle.internal.enterprise.GradleEnterprisePluginConfig
import org.gradle.internal.enterprise.GradleEnterprisePluginEndOfBuildListener
import org.gradle.internal.enterprise.GradleEnterprisePluginRequiredServices
import org.gradle.internal.enterprise.GradleEnterprisePluginService
import org.gradle.internal.enterprise.GradleEnterprisePluginServiceFactory
import org.gradle.internal.enterprise.GradleEnterprisePluginServiceRef
import org.gradle.internal.enterprise.core.GradleEnterprisePluginAdapter
import org.gradle.internal.enterprise.exceptions.ExceptionMetadataHelper
import org.gradle.internal.operations.notify.BuildOperationNotificationListenerRegistrar
import org.gradle.internal.problems.failure.Failure
import org.gradle.operations.problems.Problem
import java.util.Objects
import java.util.stream.Collectors

/**
 * Captures the state to recreate the [GradleEnterprisePluginService] instance.
 *
 *
 * The adapter is created on check-in in [DefaultGradleEnterprisePluginCheckInService] via [DefaultGradleEnterprisePluginAdapterFactory].
 * Then the adapter is stored on the [GradleEnterprisePluginManager].
 *
 *
 * There is some custom logic to store the adapter from the manager in the configuration cache and restore it afterward.
 * The pluginServices need to be recreated when loading from the configuration cache.
 *
 *
 * This must not be a service, since the configuration cache will not serialize services with state to the configuration cache.
 * Instead, it would re-use the newly registered services in the new build that causes the loss of pluginServiceFactory.
 */
class DefaultGradleEnterprisePluginAdapter(
    private val pluginServiceFactory: GradleEnterprisePluginServiceFactory,
    private val config: GradleEnterprisePluginConfig,
    private val requiredServices: GradleEnterprisePluginRequiredServices,
    private val buildState: GradleEnterprisePluginBuildState,
    private val backgroundJobExecutors: GradleEnterprisePluginBackgroundJobExecutorsInternal,
    private val pluginServiceRef: GradleEnterprisePluginServiceRefInternal,
    private val buildOperationNotificationListenerRegistrar: BuildOperationNotificationListenerRegistrar
) : GradleEnterprisePluginAdapter {
    @Transient
    private var pluginService: GradleEnterprisePluginService? = null

    init {
        createPluginService()
    }

    fun getPluginServiceRef(): GradleEnterprisePluginServiceRef {
        return pluginServiceRef
    }

    override fun shouldSaveToConfigurationCache(): Boolean {
        return true
    }

    override fun onLoadFromConfigurationCache() {
        createPluginService()
    }

    override fun buildFinished(buildFailure: Throwable?, buildFailures: MutableList<Failure>) {
        // Ensure that all tasks are complete prior to the buildFinished callback.
        backgroundJobExecutors.shutdown()

        if (pluginService != null) {
            pluginService!!.getEndOfBuildListener().buildFinished(DefaultDevelocityPluginResult(buildFailure, buildFailures))
        }
    }

    private fun createPluginService() {
        pluginService = pluginServiceFactory.create(config, requiredServices, buildState)
        pluginServiceRef.set(pluginService!!)
        buildOperationNotificationListenerRegistrar.register(pluginService!!.getBuildOperationNotificationListener())
    }

    private class DefaultDevelocityPluginResult(buildFailure: Throwable?, buildFailures: MutableList<Failure>?) : GradleEnterprisePluginEndOfBuildListener.BuildResult {
        private val buildFailure: Throwable?
        private val buildFailures: MutableList<Failure>?

        init {
            // Validate the invariant, but avoid failing in production to allow Develocity to receive _a_ result
            // to provide a better user experience in the face of a bug on the Gradle side
            assert(
                (buildFailure == null && buildFailures == null) ||
                        (buildFailure != null && buildFailures != null && !buildFailures.isEmpty())
            )
            this.buildFailure = buildFailure
            this.buildFailures = buildFailures
        }

        @Suppress("deprecation")
        override fun getFailure(): Throwable? {
            return buildFailure
        }

        override fun getBuildFailure(): GradleEnterprisePluginEndOfBuildListener.BuildFailure? {
            return if (buildFailures == null)
                null
            else GradleEnterprisePluginEndOfBuildListener.BuildFailure { this.getBuildFailures() }
        }

        fun getBuildFailures(): MutableList<org.gradle.operations.problems.Failure> {
            return Objects.requireNonNull<MutableList<Failure>?>(buildFailures).stream()
                .map<DevelocityBuildFailure> { failure: Failure? -> DevelocityBuildFailure(failure!!) }
                .collect(Collectors.toList())
        }
    }

    private class DevelocityBuildFailure(private val failure: Failure) : org.gradle.operations.problems.Failure {
        override fun getExceptionType(): String {
            return failure.exceptionType.getName()
        }

        override fun getMessage(): String? {
            return failure.header
        }

        override fun getMetadata(): MutableMap<String, String> {
            return ExceptionMetadataHelper.getMetadata(failure.original)
        }

        override fun getStackTrace(): MutableList<StackTraceElement> {
            return failure.stackTrace
        }

        override fun getClassLevelAnnotations(): MutableList<String> {
            return getClassLevelAnnotations(failure.exceptionType)
        }

        override fun getCauses(): MutableList<org.gradle.operations.problems.Failure> {
            return failure.causes.stream()
                .map<DevelocityBuildFailure> { failure: Failure? -> DevelocityBuildFailure(failure!!) }
                .collect(Collectors.toList())
        }

        override fun getProblems(): MutableList<Problem> {
            return failure.problems.stream()
                .map<BuildOperationProblem> { problem: ProblemInternal? -> BuildOperationProblem(problem) }
                .collect(Collectors.toList())
        }

        companion object {
            private fun getClassLevelAnnotations(cls: Class<*>): MutableList<String> {
                val anns: MutableSet<String> = HashSet<String>()
                for (a in cls.getAnnotations()) {
                    anns.add(a.annotationType().getName())
                }
                return ArrayList<String>(anns)
            }
        }
    }
}
