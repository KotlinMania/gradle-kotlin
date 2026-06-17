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

import com.google.common.base.MoreObjects
import org.apache.commons.io.FilenameUtils
import org.gradle.api.internal.ExecuteDomainObjectCollectionCallbackBuildOperationType
import org.gradle.api.internal.plugins.ApplyPluginBuildOperationType
import org.gradle.configuration.ApplyScriptPluginBuildOperationType
import org.gradle.configuration.internal.ExecuteListenerBuildOperationType
import org.gradle.internal.build.event.types.DefaultBinaryPluginIdentifier
import org.gradle.internal.build.event.types.DefaultScriptPluginIdentifier
import org.gradle.internal.operations.BuildOperationAncestryTracker
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.tooling.internal.protocol.events.InternalBinaryPluginIdentifier
import org.gradle.tooling.internal.protocol.events.InternalPluginIdentifier
import org.gradle.tooling.internal.protocol.events.InternalScriptPluginIdentifier
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier

internal class PluginApplicationTracker(private val ancestryTracker: BuildOperationAncestryTracker) : BuildOperationTracker {
    private val runningPluginApplications: MutableMap<OperationIdentifier?, PluginApplication?> = ConcurrentHashMap<OperationIdentifier?, PluginApplication?>()
    private val pluginApplicationRegistry: MutableMap<Long?, PluginApplication?> = ConcurrentHashMap<Long?, PluginApplication?>()

    fun getRunningPluginApplication(id: OperationIdentifier?): PluginApplication? {
        return runningPluginApplications.get(id)
    }

    fun hasRunningPluginApplication(id: OperationIdentifier?, predicate: Predicate<in PluginApplication?>): Boolean {
        return ancestryTracker.findClosestMatchingAncestor(id, Predicate { parent: OperationIdentifier? ->
            val pluginApplication = runningPluginApplications.get(parent)
            pluginApplication != null && predicate.test(pluginApplication)
        }).isPresent()
    }

    fun findRunningPluginApplication(id: OperationIdentifier?): PluginApplication? {
        return ancestryTracker.findClosestExistingAncestor<PluginApplication?>(id, Function { key: OperationIdentifier? -> runningPluginApplications.get(key) })
            .orElse(null)
    }

    override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent?) {
        if (buildOperation.getDetails() is ApplyPluginBuildOperationType.Details) {
            val details = buildOperation.getDetails() as ApplyPluginBuildOperationType.Details
            createAndTrack(buildOperation, details.targetType, details.applicationId, Supplier { toBinaryPluginIdentifier(details) })
        } else if (buildOperation.getDetails() is ApplyScriptPluginBuildOperationType.Details) {
            val details = buildOperation.getDetails() as ApplyScriptPluginBuildOperationType.Details
            createAndTrack(buildOperation, details.targetType, details.applicationId, Supplier { toScriptPluginIdentifier(details) })
        } else if (buildOperation.getDetails() is ExecuteListenerBuildOperationType.Details) {
            val details = buildOperation.getDetails() as ExecuteListenerBuildOperationType.Details
            lookupAndTrack(buildOperation, details.applicationId)
        } else if (buildOperation.getDetails() is ExecuteDomainObjectCollectionCallbackBuildOperationType.Details) {
            val details = buildOperation.getDetails() as ExecuteDomainObjectCollectionCallbackBuildOperationType.Details
            lookupAndTrack(buildOperation, details.applicationId)
        }
    }

    private fun createAndTrack(buildOperation: BuildOperationDescriptor, targetType: String?, applicationId: Long, pluginSupplier: Supplier<InternalPluginIdentifier?>) {
        if (PROJECT_TARGET_TYPE == targetType) {
            val plugin = pluginSupplier.get()
            if (plugin != null) {
                val pluginApplication = PluginApplication(applicationId, plugin)
                pluginApplicationRegistry.put(applicationId, pluginApplication)
                track(buildOperation, pluginApplication)
            }
        }
    }

    private fun lookupAndTrack(buildOperation: BuildOperationDescriptor, applicationId: Long) {
        val pluginApplication = pluginApplicationRegistry.get(applicationId)
        if (pluginApplication != null) {
            track(buildOperation, pluginApplication)
        } // else either user code is not a plugin or script or the target is not a project
    }

    private fun track(buildOperation: BuildOperationDescriptor, pluginApplication: PluginApplication?) {
        runningPluginApplications.put(buildOperation.getId(), pluginApplication)
    }

    override fun finished(buildOperation: BuildOperationDescriptor?, finishEvent: OperationFinishEvent?) {
    }

    override fun discardState(buildOperation: BuildOperationDescriptor) {
        runningPluginApplications.remove(buildOperation.getId())
    }

    private fun toBinaryPluginIdentifier(details: ApplyPluginBuildOperationType.Details): InternalBinaryPluginIdentifier {
        val className = details.pluginClass!!.getName()
        val pluginId = details.pluginId
        val displayName = MoreObjects.firstNonNull<String>(pluginId, className)
        return DefaultBinaryPluginIdentifier(displayName, className, pluginId)
    }

    private fun toScriptPluginIdentifier(details: ApplyScriptPluginBuildOperationType.Details): InternalScriptPluginIdentifier? {
        val fileString = details.file
        if (fileString != null) {
            val file = File(fileString)
            return DefaultScriptPluginIdentifier(file.getName(), file.toURI())
        }
        val uriString = details.uri
        if (uriString != null) {
            val uri = URI.create(uriString)
            return DefaultScriptPluginIdentifier(FilenameUtils.getName(uri.getPath()), uri)
        }
        return null
    }

    internal class PluginApplication(val applicationId: Long, val plugin: InternalPluginIdentifier?)

    companion object {
        private const val PROJECT_TARGET_TYPE = "project"
    }
}
