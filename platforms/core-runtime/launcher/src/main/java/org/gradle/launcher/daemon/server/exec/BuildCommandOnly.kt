/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.launcher.daemon.protocol.Build
import org.gradle.launcher.daemon.server.api.DaemonCommandAction
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution

/**
 * Superclass template for actions that only work for Build.
 *
 * If an action of this type receives a command that is not Build it will throw an exception.
 */
abstract class BuildCommandOnly : DaemonCommandAction {
    override fun execute(execution: DaemonCommandExecution) {
        val command = execution.command
        check(command is Build) { String.format("%1\$s command action received a command that isn't Build (command is %2\$s), this shouldn't happen", this.javaClass, command!!.javaClass) }

        doBuild(execution, command)
    }

    /**
     * Note that the build param is the same object as execution.getCommand(), just "pre casted".
     */
    protected open fun doBuild(execution: DaemonCommandExecution?, build: Build?) {}
}
