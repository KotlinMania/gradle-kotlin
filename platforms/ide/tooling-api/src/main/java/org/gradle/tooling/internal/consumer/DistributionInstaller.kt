/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.tooling.internal.consumer

import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.time.Clock
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.StatusEvent
import org.gradle.tooling.events.download.FileDownloadOperationDescriptor
import org.gradle.tooling.events.download.internal.DefaultFileDownloadFailureResult
import org.gradle.tooling.events.download.internal.DefaultFileDownloadFinishEvent
import org.gradle.tooling.events.download.internal.DefaultFileDownloadOperationDescriptor
import org.gradle.tooling.events.download.internal.DefaultFileDownloadStartEvent
import org.gradle.tooling.events.download.internal.DefaultFileDownloadSuccessResult
import org.gradle.tooling.events.internal.DefaultStatusEvent
import org.gradle.tooling.internal.consumer.DefaultFailure.Companion.fromThrowable
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.util.GradleVersion
import org.gradle.wrapper.Download
import org.gradle.wrapper.DownloadProgressListener
import org.gradle.wrapper.IDownload
import org.gradle.wrapper.Install
import org.gradle.wrapper.Logger
import org.gradle.wrapper.PathAssembler
import org.gradle.wrapper.WrapperConfiguration
import java.io.File
import java.net.URI
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference

class DistributionInstaller(private val progressLoggerFactory: ProgressLoggerFactory, buildProgressListener: InternalBuildProgressListener, clock: Clock, timeout: Int) {
    private val buildProgressListener: InternalBuildProgressListener
    private val clock: Clock
    private val currentListener = AtomicReference<InternalBuildProgressListener?>(NO_OP)

    // Protects the following state
    private val lock = Any()
    private var completed = false
    private var cancelled = false
    private var failure: Throwable? = null
    private val timeout: Int

    init {
        this.buildProgressListener = getListener(buildProgressListener)
        this.clock = clock
        this.timeout = timeout
    }

    private fun getListener(buildProgressListener: InternalBuildProgressListener): InternalBuildProgressListener {
        if (buildProgressListener.subscribedOperations.contains(InternalBuildProgressListener.FILE_DOWNLOAD)) {
            return buildProgressListener
        } else {
            return NO_OP
        }
    }

    /**
     * Installs the distribution and returns the result.
     */
    @Throws(Exception::class)
    fun install(userHomeDir: File?, projectDir: File?, wrapperConfiguration: WrapperConfiguration, systemProperties: MutableMap<String?, String?>): File? {
        val install = Install(Logger(false), DistributionInstaller.AsyncDownload(systemProperties), PathAssembler(userHomeDir, projectDir))
        return install.createDist(wrapperConfiguration)
    }

    /**
     * Cancels the current installation, if running.
     */
    fun cancel() {
        synchronized(lock) {
            cancelled = true
            (lock as Object).notifyAll()
        }
    }

    private class NoOpListener : InternalBuildProgressListener {
        override fun onEvent(event: Any?) {
        }

        override fun getSubscribedOperations(): MutableList<String?> {
            return mutableListOf<String?>()
        }
    }

    private inner class ForwardingDownloadProgressListener(private val descriptor: OperationDescriptor) : DownloadProgressListener {
        private var downloaded: Long = 0

        override fun downloadStatusChanged(address: URI?, contentLength: Long, downloaded: Long) {
            this.downloaded = downloaded
            val statusEvent: StatusEvent = DefaultStatusEvent(clock.currentTime, descriptor, contentLength, downloaded, "bytes")
            // This is called from the download thread. Only forward the events when not cancelled
            currentListener.get()!!.onEvent(statusEvent)
        }
    }

    private inner class AsyncDownload(private val systemProperties: MutableMap<String?, String?>) : IDownload {
        @Throws(Exception::class)
        override fun download(address: URI, destination: File) {
            synchronized(lock) {
                doDownload(address, destination)
            }
        }

        @Throws(Exception::class)
        fun doDownload(address: URI, destination: File) {
            val displayName = "Download " + address
            val descriptor: FileDownloadOperationDescriptor = DefaultFileDownloadOperationDescriptor(displayName, address, null)
            val startTime = clock.currentTime
            buildProgressListener.onEvent(DefaultFileDownloadStartEvent(startTime, displayName + " started", descriptor))

            var failure: Throwable? = null
            var bytesDownloaded: Long = 0
            try {
                bytesDownloaded = withProgressLogging(address, destination, descriptor)
            } catch (t: Throwable) {
                failure = t
            }

            val endTime = clock.currentTime
            val result = if (failure == null) DefaultFileDownloadSuccessResult(startTime, endTime, bytesDownloaded) else DefaultFileDownloadFailureResult(
                startTime, endTime, mutableListOf<DefaultFailure>(
                    fromThrowable(failure)
                ), bytesDownloaded
            )
            buildProgressListener.onEvent(DefaultFileDownloadFinishEvent(endTime, displayName + " finished", descriptor, result))
            if (failure != null) {
                if (failure is Exception) {
                    throw failure
                }
                throw throwAsUncheckedException(failure)
            }
        }

        @Throws(Throwable::class)
        fun withProgressLogging(address: URI, destination: File, operationDescriptor: OperationDescriptor): Long {
            val progressLogger = progressLoggerFactory.newOperation(DistributionInstaller::class.java)
            progressLogger!!.setDescription("Download " + address)
            progressLogger.started()
            try {
                return withAsyncDownload(address, destination, operationDescriptor)
            } finally {
                progressLogger.completed()
            }
        }

        @Throws(Throwable::class)
        fun withAsyncDownload(address: URI, destination: File, operationDescriptor: OperationDescriptor): Long {
            val listener: ForwardingDownloadProgressListener = DistributionInstaller.ForwardingDownloadProgressListener(operationDescriptor)
            currentListener.set(buildProgressListener)
            try {
                // Start the download in another thread and wait for the result
                val thread: Thread = object : Thread("Distribution download") {
                    override fun run() {
                        try {
                            Download(Logger(false), listener, APP_NAME, GradleVersion.current().getVersion(), systemProperties, timeout).download(address, destination)
                        } catch (t: Throwable) {
                            synchronized(lock) {
                                failure = t
                            }
                        } finally {
                            synchronized(lock) {
                                completed = true
                                (lock as Object).notifyAll()
                            }
                        }
                    }
                }
                thread.setDaemon(true)
                thread.start()
                synchronized(lock) {
                    while (!completed && !cancelled) {
                        try {
                            (lock as Object).wait()
                        } catch (e: InterruptedException) {
                            throw throwAsUncheckedException(e)
                        }
                    }
                    if (failure != null) {
                        throw failure
                    }
                    if (cancelled) {
                        // When cancelled, try to stop the download thread but don't attempt to wait for it to complete
                        // Could possibly loop here for a while trying to force the thread to exit
                        thread.interrupt()
                        throw CancellationException()
                    }
                }
            } finally {
                // The download thread may still be running. Ignore any further status events from it
                currentListener.set(NO_OP)
            }
            return listener.downloaded
        }
    }

    companion object {
        private const val APP_NAME = "Gradle Tooling API"
        private val NO_OP: InternalBuildProgressListener = NoOpListener()
    }
}
