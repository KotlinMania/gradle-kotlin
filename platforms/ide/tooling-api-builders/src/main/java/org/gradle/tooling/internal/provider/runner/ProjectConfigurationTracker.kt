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
package org.gradle.tooling.internal.provider.runner

import com.google.common.collect.ImmutableList
import org.gradle.configuration.project.ConfigureProjectBuildOperationType
import org.gradle.internal.build.event.types.DefaultPluginApplicationResult
import org.gradle.internal.operations.BuildOperationAncestryTracker
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.tooling.internal.protocol.events.InternalPluginIdentifier
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationResult
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Collectors

internal class ProjectConfigurationTracker(private val ancestryTracker: BuildOperationAncestryTracker, private val pluginApplicationTracker: PluginApplicationTracker) : BuildOperationTracker {
    private val results: MutableMap<OperationIdentifier?, ProjectConfigurationResult> = ConcurrentHashMap<OperationIdentifier?, ProjectConfigurationResult>()

    val trackers: MutableList<out BuildOperationTracker?>?
        get() = ImmutableList.of<PluginApplicationTracker?>(pluginApplicationTracker)

    fun resultsFor(buildOperation: OperationIdentifier?): MutableList<InternalProjectConfigurationResult.InternalPluginApplicationResult?> {
        val result: ProjectConfigurationResult = results.remove(buildOperation)!!
        checkNotNull(result) { "Project configuration results are not available for build operation " + buildOperation }
        return result.toInternalPluginApplicationResults()
    }

    override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent?) {
        if (buildOperation.getDetails() is ConfigureProjectBuildOperationType.Details) {
            results.put(buildOperation.getId(), ProjectConfigurationResult())
        }
    }

    override fun finished(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent) {
        val pluginApplication = pluginApplicationTracker.getRunningPluginApplication(buildOperation.getId())
        if (pluginApplication != null) {
            ancestryTracker.findClosestExistingAncestor<ProjectConfigurationResult?>(buildOperation.getParentId(), Function { key: OperationIdentifier? -> results.get(key) })
                .ifPresent(Consumer { result: ProjectConfigurationResult? ->
                    if (hasNoEnclosingRunningPluginApplicationForSamePlugin(buildOperation, pluginApplication.getPlugin())) {
                        result!!.increment(pluginApplication, finishEvent.getEndTime() - finishEvent.getStartTime())
                    }
                })
        }
    }

    private fun hasNoEnclosingRunningPluginApplicationForSamePlugin(buildOperation: BuildOperationDescriptor, plugin: InternalPluginIdentifier?): Boolean {
        return !pluginApplicationTracker.hasRunningPluginApplication(
            buildOperation.getParentId(),
            Predicate { pluginApplication: PluginApplicationTracker.PluginApplication? -> pluginApplication!!.getPlugin() == plugin })
    }

    private class ProjectConfigurationResult {
        private val pluginApplicationResults: MutableMap<InternalPluginIdentifier?, PluginApplicationResult?> = ConcurrentHashMap<InternalPluginIdentifier?, PluginApplicationResult?>()

        fun increment(pluginApplication: PluginApplicationTracker.PluginApplication, duration: Long) {
            val plugin = pluginApplication.getPlugin()
            pluginApplicationResults
                .computeIfAbsent(plugin) { key: org.gradle.tooling.internal.protocol.events.InternalPluginIdentifier? ->
                    org.gradle.tooling.internal.provider.runner.ProjectConfigurationTracker.PluginApplicationResult(
                        plugin,
                        pluginApplication.getApplicationId()
                    )
                }!!
                .increment(duration)
        }

        fun toInternalPluginApplicationResults(): MutableList<InternalProjectConfigurationResult.InternalPluginApplicationResult?> {
            return pluginApplicationResults.values.stream()
                .sorted(Comparator.comparing<PluginApplicationResult?, Long?>(Function { obj: PluginApplicationResult? -> obj!!.firstApplicationId }))
                .map<InternalProjectConfigurationResult.InternalPluginApplicationResult?> { obj: PluginApplicationResult? -> obj!!.toInternalPluginApplicationResult() }
                .collect(Collectors.toCollection(Supplier { ArrayList() }))
        }
    }

    private class PluginApplicationResult(private val plugin: InternalPluginIdentifier?, val firstApplicationId: Long) {
        private val duration = AtomicLong()

        fun increment(duration: Long) {
            this.duration.addAndGet(duration)
        }

        fun toInternalPluginApplicationResult(): InternalProjectConfigurationResult.InternalPluginApplicationResult {
            return DefaultPluginApplicationResult(plugin, Duration.ofMillis(duration.get()))
        }
    }
}
