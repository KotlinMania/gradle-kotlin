/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.launcher.daemon.server.exec

import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry
import org.gradle.internal.vfs.VirtualFileSystem
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem
import org.gradle.launcher.daemon.protocol.Build
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution
import org.jspecify.annotations.NullMarked
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

/**
 * Asynchronously cleans up the VFS after a build.
 *
 * Unblocks the client to receive the build finished event while the cleanup is happening.
 * However, the next build is not allowed to start until the cleanup is finished.
 */
@NullMarked
class CleanUpVirtualFileSystemAfterBuild(executorFactory: ExecutorFactory, private val userHomeServiceRegistry: GradleUserHomeScopeServiceRegistry) : BuildCommandOnly(), Stoppable {
    private val executor: ManagedExecutor

    private var pendingCleanup: CompletableFuture<Void> = CompletableFuture.completedFuture<Void>(null)

    init {
        this.executor = executorFactory.create("VFS cleanup")
    }

    override fun doBuild(execution: DaemonCommandExecution, build: Build) {
        waitForPendingCleanupToFinish(pendingCleanup)
        try {
            execution.proceed()
        } finally {
            pendingCleanup = startAsyncCleanupAfterBuild()
        }
    }

    private fun startAsyncCleanupAfterBuild(): CompletableFuture<Void> {
        return userHomeServiceRegistry.getCurrentServices()
            .map<CompletableFuture<Void>>(Function { serviceRegistry: ServiceRegistry? ->
                CompletableFuture.runAsync(Runnable {
                    LOGGER.debug("Cleaning virtual file system after build finished")
                    val virtualFileSystem = serviceRegistry!!.get<BuildLifecycleAwareVirtualFileSystem>(BuildLifecycleAwareVirtualFileSystem::class.java)
                    virtualFileSystem.afterBuildFinished()
                }, executor)
            })
            .orElseGet(Supplier { CompletableFuture.completedFuture<Void>(null) })
    }

    private fun waitForPendingCleanupToFinish(pendingCleanup: CompletableFuture<Void>) {
        if (!pendingCleanup.isDone()) {
            LOGGER.debug("Waiting for pending virtual file system cleanup to be finished")
            try {
                pendingCleanup.get()
            } catch (e: InterruptedException) {
                LOGGER.error("Couldn't clean up VFS between builds, dropping content", e)
                userHomeServiceRegistry.getCurrentServices().ifPresent(Consumer { serviceRegistry: ServiceRegistry? ->
                    val virtualFileSystem = serviceRegistry!!.get<VirtualFileSystem>(VirtualFileSystem::class.java)
                    virtualFileSystem.invalidateAll()
                })
            } catch (e: ExecutionException) {
                LOGGER.error("Couldn't clean up VFS between builds, dropping content", e)
                userHomeServiceRegistry.getCurrentServices().ifPresent(Consumer { serviceRegistry: ServiceRegistry? ->
                    val virtualFileSystem = serviceRegistry!!.get<VirtualFileSystem>(VirtualFileSystem::class.java)
                    virtualFileSystem.invalidateAll()
                })
            }
        }
    }

    override fun stop() {
        // If we are shutting down, it's not important to finish cleaning the VFS
        executor.shutdownNow()
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(CleanUpVirtualFileSystemAfterBuild::class.java)
    }
}
