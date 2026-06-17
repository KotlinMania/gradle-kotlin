/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.launcher.daemon.server.api

import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.launcher.daemon.protocol.InvalidateVirtualFileSystemAfterChange
import org.gradle.launcher.daemon.protocol.Success
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.function.Consumer

class HandleInvalidateVirtualFileSystem(private val gradleUserHomeScopeServiceRegistry: GradleUserHomeScopeServiceRegistry) : DaemonCommandAction {
    override fun execute(execution: DaemonCommandExecution) {
        if (execution.getCommand() is InvalidateVirtualFileSystemAfterChange) {
            val command = execution.getCommand() as InvalidateVirtualFileSystemAfterChange
            gradleUserHomeScopeServiceRegistry.getCurrentServices().ifPresent(Consumer { currentServices: ServiceRegistry? ->
                LOGGER.info("Invalidating {}", command.getChangedPaths())
                val fileSystemAccess = currentServices!!.get<FileSystemAccess>(FileSystemAccess::class.java)
                fileSystemAccess.invalidate(command.getChangedPaths())
            })
            execution.getConnection().completed(Success(null))
        } else {
            execution.proceed()
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(HandleInvalidateVirtualFileSystem::class.java)
    }
}
