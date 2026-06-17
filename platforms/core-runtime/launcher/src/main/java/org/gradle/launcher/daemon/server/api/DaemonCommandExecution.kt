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
package org.gradle.launcher.daemon.server.api

import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.protocol.Command
import java.util.LinkedList

/**
 * A continuation style object used to model the execution of a command.
 *
 *
 * Facilitates processing "chains", making it easier to break up processing logic into discrete [actions][DaemonCommandAction].
 *
 *
 * The given actions will be executed in the order given to the constructor, and should use the [.proceed] method to allow
 * the next action to run. If an action does not call `proceed()`, it will be the last action that executes.
 */
class DaemonCommandExecution(
    private val configuration: DaemonServerConfiguration, @JvmField val connection: DaemonConnection?,
    /**
     * The command to execute.
     *
     *
     * If the client disconnects before sending a command, this **will** be `null`.
     */
    @JvmField val command: Command?, @JvmField val daemonContext: DaemonContext?, @JvmField val daemonStateControl: DaemonStateControl?, actions: MutableList<DaemonCommandAction?>
) {
    private val actions: LinkedList<DaemonCommandAction?>

    /**
     * The currently nominated result for the execution.
     *
     *
     * May be null if no action has set the result yet.
     */
    /**
     * Sets what is to be considered the result of executing the command.
     *
     *
     * This may be called multiple times to do things like wrap the result in another type.
     */
    @JvmField
    var result: Any? = null

    init {
        this.actions = LinkedList<DaemonCommandAction?>(actions)
    }

    /**
     * Continues (or starts) execution.
     *
     *
     * Each action should call this method if it determines that execution should continue.
     *
     * @return true if execution did occur, false if this execution has already occurred.
     */
    fun proceed(): Boolean {
        if (actions.isEmpty()) {
            return false
        } else {
            actions.removeFirst()!!.execute(this)
            return true
        }
    }

    val isSingleUseDaemon: Boolean
        /**
         * Informs if this execution is of single-use-daemon type
         */
        get() = configuration.isSingleUse

    override fun toString(): String {
        return String.format("DaemonCommandExecution[command = %s, connection = %s]", command, connection)
    }
}
