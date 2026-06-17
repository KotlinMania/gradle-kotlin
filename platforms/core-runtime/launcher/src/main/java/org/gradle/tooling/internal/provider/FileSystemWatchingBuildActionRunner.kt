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
package org.gradle.tooling.internal.provider

import org.gradle.api.internal.changedetection.state.FileHasherStatistics
import org.gradle.deployment.internal.DeploymentRegistryInternal
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.buildtree.BuildActionRunner
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.file.StatStatistics
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.service.scopes.VirtualFileSystemServices
import org.gradle.internal.snapshot.impl.DirectorySnapshotterStatistics
import org.gradle.internal.watch.options.FileSystemWatchingSettingsFinalizedProgressDetails
import org.gradle.internal.watch.registry.WatchMode
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem
import org.gradle.internal.watch.vfs.VfsLogging
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class FileSystemWatchingBuildActionRunner(
    private val eventEmitter: BuildOperationProgressEventEmitter,
    private val virtualFileSystem: BuildLifecycleAwareVirtualFileSystem,
    private val deploymentRegistry: DeploymentRegistryInternal,
    private val statStatisticsCollector: StatStatistics.Collector,
    private val fileHasherStatisticsCollector: FileHasherStatistics.Collector,
    private val directorySnapshotterStatisticsCollector: DirectorySnapshotterStatistics.Collector,
    private val buildOperationRunner: BuildOperationRunner,
    private val options: InternalOptions,
    private val delegate: BuildActionRunner
) : BuildActionRunner {
    override fun run(action: BuildAction, buildController: BuildTreeLifecycleController): BuildActionRunner.Result {
        val startParameter = action.getStartParameter()

        var watchFileSystemMode = startParameter.getWatchFileSystemMode()
        val verboseVfsLogging = if (startParameter.isVfsVerboseLogging())
            VfsLogging.VERBOSE
        else
            VfsLogging.NORMAL

        LOGGER.info("Watching the file system is configured to be {}", watchFileSystemMode.getDescription())

        val continuousBuild = startParameter.isContinuous() || !deploymentRegistry.getRunningDeployments().isEmpty()

        if (continuousBuild && watchFileSystemMode === WatchMode.DEFAULT) {
            // Try to watch as much as possible when using continuous build.
            watchFileSystemMode = WatchMode.ENABLED
        }
        if (watchFileSystemMode.isEnabled()) {
            dropVirtualFileSystemIfRequested(options, virtualFileSystem)
        }
        if (verboseVfsLogging == VfsLogging.VERBOSE) {
            logVfsStatistics("since last build", statStatisticsCollector, fileHasherStatisticsCollector, directorySnapshotterStatisticsCollector)
        }

        if (action.getStartParameter().getProjectCacheDir() != null) {
            // We'd like to create the probe in the `.gradle` directory under the build root,
            // but if project cache is somewhere else, then we don't want to put trash in there
            // See https://github.com/gradle/gradle/issues/17262
            when (watchFileSystemMode) {
                WatchMode.ENABLED -> throw IllegalStateException("Enabling file system watching via --watch-fs (or via the " + StartParameterBuildOptions.WatchFileSystemOption.GRADLE_PROPERTY + " property) with --project-cache-dir also specified is not supported; remove either option to fix this problem")
                WatchMode.DEFAULT -> {
                    LOGGER.info("File system watching is disabled because --project-cache-dir is specified")
                    watchFileSystemMode = WatchMode.DISABLED
                }

                else -> {}
            }
        }

        LOGGER.debug("Watching the file system computed to be {}", watchFileSystemMode.getDescription())
        val actuallyWatching = virtualFileSystem.afterBuildStarted(
            watchFileSystemMode,
            verboseVfsLogging,
            buildOperationRunner
        )
        LOGGER.info("File system watching is {}", if (actuallyWatching) "active" else "inactive")
        eventEmitter.emitNowForCurrent(object : FileSystemWatchingSettingsFinalizedProgressDetails {
            override fun isEnabled(): Boolean {
                return actuallyWatching
            }
        })
        if (continuousBuild) {
            check(actuallyWatching) { "Continuous build does not work when file system watching is disabled" }
        }

        try {
            return delegate.run(action, buildController)
        } finally {
            val maximumNumberOfWatchedHierarchies = VirtualFileSystemServices.getMaximumNumberOfWatchedHierarchies(options)
            virtualFileSystem.beforeBuildFinished(
                watchFileSystemMode,
                verboseVfsLogging,
                buildOperationRunner,
                maximumNumberOfWatchedHierarchies
            )
            if (verboseVfsLogging == VfsLogging.VERBOSE) {
                logVfsStatistics("during current build", statStatisticsCollector, fileHasherStatisticsCollector, directorySnapshotterStatisticsCollector)
            }
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(FileSystemWatchingBuildActionRunner::class.java)

        private fun logVfsStatistics(
            title: String,
            statStatisticsCollector: StatStatistics.Collector,
            fileHasherStatisticsCollector: FileHasherStatistics.Collector,
            directorySnapshotterStatisticsCollector: DirectorySnapshotterStatistics.Collector
        ) {
            LOGGER.warn("VFS> Statistics {}:", title)
            LOGGER.warn("VFS> > Stat: {}", statStatisticsCollector.collect())
            LOGGER.warn("VFS> > FileHasher: {}", fileHasherStatisticsCollector.collect())
            LOGGER.warn("VFS> > DirectorySnapshotter: {}", directorySnapshotterStatisticsCollector.collect())
        }

        private fun dropVirtualFileSystemIfRequested(options: InternalOptions, virtualFileSystem: BuildLifecycleAwareVirtualFileSystem) {
            if (VirtualFileSystemServices.isDropVfs(options)) {
                virtualFileSystem.invalidateAll()
            }
        }
    }
}
