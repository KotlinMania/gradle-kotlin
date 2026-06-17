/*
 * Copyright 2012 the original author or authors.
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

interface DaemonStateControl {
    /**
     *
     * Requests that the daemon stop, but wait until the daemon is idle. The stop will happen asynchronously, and this method does not block.
     *
     *
     * The daemon will stop accepting new work, so that subsequent calls to [.runCommand] will fail with [DaemonUnavailableException].
     */
    fun requestStop(reason: String?)

    /**
     * Requests a forceful stops of the daemon. Does not wait until the daemon is idle to begin stopping. The stop will happen asynchronously, and this method does not block.
     *
     *
     * If any long running command is currently running, the blocked call to [.runCommand] will fail with [DaemonStoppedException].
     *
     *
     * The daemon will stop accepting new work, so that subsequent calls to [.runCommand] will failing with [DaemonUnavailableException].
     */
    fun requestForcefulStop(reason: String?)

    /**
     * Returns the current state of the daemon
     *
     * @return The current state of the daemon
     */
    val state: DaemonState?

    /**
     * Requests that a running build be canceled.  Note that this method does NOT block until the operation has been cancelled.
     */
    fun requestCancel()

    /**
     * Communicates a request for build cancellation. Note that this method blocks until the operation has been cancelled.
     *
     *
     * If any long running command is currently running, this method does block for certain time to give chance to perform cancellation, and if the command
     * doesn't finnish in a timely manner a request for forceful stop will be issued ([.requestForcefulStop].
     */
    fun cancelBuild()

    /**
     * Returns a cancellation token used to communicate cancel requests to commands processed in this daemon.
     *
     * @return Created cancellation token associated with currently running command or an arbitrary instance if no command is running.
     */
    @JvmField
    val cancellationToken: BuildCancellationToken?

    /**
     * Runs the given long running command. No more than 1 command may be running at any given time.
     *
     * @param command The command to run
     * @param commandDisplayName The command's display name, used for logging and error messages.
     *
     * @throws DaemonUnavailableException When this daemon is unable to run the command, either because it is currently executing another command
     * or is currently stopping.
     * @throws DaemonStoppedException When this daemon started executing the command but was unable to complete it because the daemon is about to stop.
     * The caller should note that the command may still be running at the time the method returns but should consider the command as abandoned.
     */
    @Throws(DaemonUnavailableException::class, DaemonStoppedException::class)
    fun runCommand(command: Runnable?, commandDisplayName: String?)
}
