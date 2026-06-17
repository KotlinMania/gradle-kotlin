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
package org.gradle.launcher.daemon.server

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.internal.UncheckedException
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.time.Time
import org.gradle.internal.time.Timer
import org.gradle.launcher.daemon.server.api.DaemonState
import org.gradle.launcher.daemon.server.api.DaemonStateControl
import org.gradle.launcher.daemon.server.api.DaemonStoppedException
import org.gradle.launcher.daemon.server.api.DaemonUnavailableException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile

/**
 * A tool for synchronising the state amongst different threads.
 *
 * This class has no knowledge of the Daemon's internals and is designed to be used internally by the daemon to coordinate itself and allow worker threads to control the daemon's busy/idle status.
 *
 * This is not exposed to clients of the daemon.
 */
class DaemonStateCoordinator internal constructor(
    executorFactory: ExecutorFactory,
    private val onStartCommand: Runnable,
    private val onFinishCommand: Runnable,
    private val onCancelCommand: Runnable,
    private val cancelTimeoutMs: Long
) : Stoppable, DaemonStateControl {
    private val lock: Lock = ReentrantLock()
    private val condition: Condition = lock.newCondition()

    private var state = DaemonState.Idle
    private val idleTimer: Timer
    private var currentCommandExecution: String? = null
    private var result: Any? = null
    private var stopReason: String? = null

    @Volatile
    private var cancellationToken: DefaultBuildCancellationToken? = null

    private val executor: ManagedExecutor

    constructor(executorFactory: ExecutorFactory, onStartCommand: Runnable, onFinishCommand: Runnable, onCancelCommand: Runnable) : this(
        executorFactory,
        onStartCommand,
        onFinishCommand,
        onCancelCommand,
        10 * 1000L
    )

    init {
        executor = executorFactory.create("Daemon worker")
        idleTimer = Time.startTimer()
        updateCancellationToken()
    }

    private fun setState(state: DaemonState) {
        this.state = state
        condition.signalAll()
    }

    fun awaitStop(): DaemonStopState {
        lock.lock()
        try {
            while (true) {
                try {
                    when (state) {
                        DaemonState.Idle, DaemonState.Busy -> {
                            LOGGER.debug("daemon is running. Sleeping until state changes.")
                            condition.await()
                        }

                        DaemonState.Canceled -> {
                            LOGGER.debug("cancel requested.")
                            val state = cancelNow()
                            if (state != null) {
                                // Could not cancel cleanly, so stop
                                return state
                            }
                        }

                        DaemonState.Broken -> throw IllegalStateException("This daemon is in a broken state.")
                        DaemonState.StopRequested -> {
                            LOGGER.debug("daemon stop has been requested. Sleeping until state changes.")
                            condition.await()
                        }

                        DaemonState.Stopped -> {
                            LOGGER.debug("daemon has stopped.")
                            return DaemonStopState.Clean
                        }

                        DaemonState.ForceStopped -> {
                            LOGGER.debug("daemon has been force stopped.")
                            return DaemonStopState.Forced
                        }
                    }
                } catch (e: InterruptedException) {
                    throw UncheckedException.throwAsUncheckedException(e)
                }
            }
        } finally {
            lock.unlock()
        }
    }

    override fun requestStop(reason: String) {
        lock.lock()
        try {
            if (state != DaemonState.StopRequested && state != DaemonState.Stopped && state != DaemonState.ForceStopped) {
                LOGGER.lifecycle(DAEMON_WILL_STOP_MESSAGE + reason)
                if (state == DaemonState.Busy) {
                    LOGGER.debug("Stop as soon as idle requested. The daemon is busy")
                    beginStopping()
                } else {
                    stopNow(reason)
                }
            }
        } finally {
            lock.unlock()
        }
    }

    override fun requestForcefulStop(reason: String) {
        LOGGER.lifecycle(DAEMON_STOPPING_IMMEDIATELY_MESSAGE + reason)
        stopNow(reason)
    }

    /**
     * Forcibly stops the daemon, even if it is busy.
     *
     * If the daemon is busy and the client is waiting for a response, it may receive "null" from the daemon as the connection may be closed by this method before the result is sent back.
     *
     * @see .requestStop
     */
    override fun stop() {
        stopNow("service stop")
    }

    private fun stopNow(reason: String) {
        lock.lock()
        try {
            when (state) {
                DaemonState.Idle -> {
                    LOGGER.debug("Marking daemon stopped due to {}. The daemon is not running a build", reason)
                    stopReason = reason
                    setState(DaemonState.Stopped)
                }

                DaemonState.Busy, DaemonState.Canceled, DaemonState.Broken, DaemonState.StopRequested -> {
                    LOGGER.debug("Marking daemon stopped due to {}. The daemon is running a build", reason)
                    stopReason = reason
                    setState(DaemonState.ForceStopped)
                }

                DaemonState.Stopped, DaemonState.ForceStopped -> {}
                else -> throw IllegalStateException("Daemon is in unexpected state: " + state)
            }
        } finally {
            lock.unlock()
        }
    }

    private fun beginStopping() {
        when (state) {
            DaemonState.Idle, DaemonState.Busy, DaemonState.Canceled, DaemonState.Broken -> setState(DaemonState.StopRequested)
            DaemonState.StopRequested, DaemonState.Stopped, DaemonState.ForceStopped -> {}
            else -> throw IllegalStateException("Daemon is in unexpected state: " + state)
        }
    }

    public override fun getCancellationToken(): BuildCancellationToken {
        return cancellationToken!!
    }

    private fun updateCancellationToken() {
        cancellationToken = DefaultBuildCancellationToken()
        cancellationToken!!.addCallback(onCancelCommand)
    }

    override fun requestCancel() {
        lock.lock()
        try {
            if (state == DaemonState.Busy) {
                setState(DaemonState.Canceled)
            } else if (state == DaemonState.StopRequested) {
                requestForcefulStop("the build was canceled after a stop was requested")
            }
        } finally {
            lock.unlock()
        }
    }

    override fun cancelBuild() {
        requestCancel()

        lock.lock()
        try {
            while (true) {
                try {
                    when (state) {
                        DaemonState.Idle, DaemonState.Stopped, DaemonState.ForceStopped -> return
                        DaemonState.Busy, DaemonState.Canceled, DaemonState.StopRequested -> condition.await()
                        DaemonState.Broken -> throw IllegalStateException("This daemon is in a broken state.")
                    }
                } catch (e: InterruptedException) {
                    throw UncheckedException.throwAsUncheckedException(e)
                }
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * @return null if the current build could be cancelled cleanly, otherwise the result of stopping the daemon
     */
    private fun cancelNow(): DaemonStopState? {
        val timer = Time.startCountdownTimer(cancelTimeoutMs)

        LOGGER.debug("Cancel requested: will wait for daemon to become idle.")
        try {
            cancellationToken!!.cancel()
        } catch (ex: Exception) {
            LOGGER.error("Cancel processing failed. Will continue.", ex)
        }

        lock.lock()
        try {
            while (!timer.hasExpired()) {
                try {
                    when (state) {
                        DaemonState.Idle -> {
                            LOGGER.debug("Cancel: daemon is idle now.")
                            return null
                        }

                        DaemonState.Busy, DaemonState.Canceled, DaemonState.StopRequested -> {
                            LOGGER.debug("Cancel: daemon is busy, sleeping until state changes.")
                            condition.await(timer.remainingMillis, TimeUnit.MILLISECONDS)
                        }

                        DaemonState.Broken -> throw IllegalStateException("This daemon is in a broken state.")
                        DaemonState.Stopped -> {
                            LOGGER.debug("Cancel: daemon has stopped.")
                            return DaemonStopState.Clean
                        }

                        DaemonState.ForceStopped -> {
                            LOGGER.debug("Cancel: daemon has been force stopped.")
                            return DaemonStopState.Forced
                        }
                    }
                } catch (e: InterruptedException) {
                    throw UncheckedException.throwAsUncheckedException(e)
                }
            }
            LOGGER.debug("Cancel: daemon is still busy after grace period. Will force stop.")
            stopNow("cancel requested but timed out")
            return DaemonStopState.Forced
        } finally {
            lock.unlock()
        }
    }

    @Throws(DaemonUnavailableException::class)
    override fun runCommand(command: Runnable, commandDisplayName: String) {
        onStartCommand(commandDisplayName)
        try {
            executor.execute(Runnable {
                try {
                    command.run()
                    onCommandSuccessful()
                } catch (t: Throwable) {
                    onCommandFailed(t)
                }
            })
            waitForCommandCompletion()
        } finally {
            onFinishCommand()
        }
    }

    private fun waitForCommandCompletion() {
        lock.lock()
        try {
            while ((state == DaemonState.Busy || state == DaemonState.Canceled || state == DaemonState.StopRequested) && result == null) {
                try {
                    condition.await()
                } catch (e: InterruptedException) {
                    throw UncheckedException.throwAsUncheckedException(e)
                }
            }
            LOGGER.debug("Command execution: finished waiting for {}. Result {} with state {}", currentCommandExecution, result, state)
            if (result is Throwable) {
                throw UncheckedException.throwAsUncheckedException(result as Throwable)
            }
            if (result != null) {
                return
            }
            when (state) {
                DaemonState.Stopped, DaemonState.ForceStopped -> throw DaemonStoppedException(stopReason)
                DaemonState.Broken -> throw DaemonUnavailableException("This daemon is broken and will stop.")
                else -> throw IllegalStateException("Daemon is in unexpected state: " + state)
            }
        } finally {
            lock.unlock()
        }
    }

    private fun onCommandFailed(failure: Throwable) {
        lock.lock()
        try {
            result = failure
            condition.signalAll()
        } finally {
            lock.unlock()
        }
    }

    private fun onCommandSuccessful() {
        lock.lock()
        try {
            result = this
            condition.signalAll()
        } finally {
            lock.unlock()
        }
    }

    private fun onStartCommand(commandDisplayName: String) {
        lock.lock()
        try {
            when (state) {
                DaemonState.Broken -> throw DaemonUnavailableException("This daemon is in a broken state and will stop.")
                DaemonState.StopRequested -> throw DaemonUnavailableException("This daemon is currently stopping.")
                DaemonState.Stopped, DaemonState.ForceStopped -> throw DaemonUnavailableException("This daemon has stopped.")
                DaemonState.Busy, DaemonState.Canceled -> throw DaemonUnavailableException(String.format("This daemon is currently executing: %s", currentCommandExecution))
                DaemonState.Idle -> {}
            }

            LOGGER.debug("Command execution: started {} after {} minutes of idle", commandDisplayName, this.idleMinutes)
            try {
                setState(DaemonState.Busy)
                onStartCommand.run()
                currentCommandExecution = commandDisplayName
                result = null
                updateActivityTimestamp()
                updateCancellationToken()
                condition.signalAll()
            } catch (throwable: Throwable) {
                setState(DaemonState.Broken)
                throw UncheckedException.throwAsUncheckedException(throwable)
            }
        } finally {
            lock.unlock()
        }
    }

    private fun onFinishCommand() {
        lock.lock()
        try {
            LOGGER.debug("Command execution: completed {}", currentCommandExecution)
            currentCommandExecution = null
            result = null
            stopReason = null
            updateActivityTimestamp()
            when (state) {
                DaemonState.Idle, DaemonState.Busy, DaemonState.Canceled -> try {
                    onFinishCommand.run()
                    setState(DaemonState.Idle)
                } catch (throwable: Throwable) {
                    setState(DaemonState.Broken)
                    throw UncheckedException.throwAsUncheckedException(throwable)
                }

                DaemonState.StopRequested -> {
                    setState(DaemonState.Idle)
                    stopNow("command completed and stop requested")
                }

                DaemonState.Stopped, DaemonState.ForceStopped -> {}
                else -> throw IllegalStateException("Daemon is in unexpected state: " + state)
            }
        } finally {
            lock.unlock()
        }
    }

    private fun updateActivityTimestamp() {
        LOGGER.debug("resetting idle timer")
        idleTimer.reset()
    }

    private val idleMinutes: Double
        get() {
            lock.lock()
            try {
                return idleTimer.elapsedMillis / 1000.0 / 60.0
            } finally {
                lock.unlock()
            }
        }

    val idleMillis: Long
        get() {
            if (state == DaemonState.Idle) {
                return idleTimer.elapsedMillis
            } else {
                return 0L
            }
        }

    val isWillRefuseNewCommands: Boolean
        get() = !(state == DaemonState.Idle || state == DaemonState.Busy)

    override fun getState(): DaemonState {
        return state
    }

    companion object {
        const val DAEMON_WILL_STOP_MESSAGE: String = "Daemon will be stopped at the end of the build "
        const val DAEMON_STOPPING_IMMEDIATELY_MESSAGE: String = "Daemon is stopping immediately "
        private val LOGGER: Logger = Logging.getLogger(DaemonStateCoordinator::class.java)
    }
}
