/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.launcher.daemon.configuration

import org.gradle.internal.nativeintegration.services.NativeServices
import java.io.File

open class DefaultDaemonServerConfiguration // There is at least one case when the daemon shouldn't use the available agent: if the foreground
// daemon is started with feature flag disabled.
// The start script cannot look into the feature flags, so the agent is always applied to the foreground daemon.
// The state of the flag has to be communicated to the daemon setup code explicitly, which is what this constructor allows.
    (
    private val daemonUid: String,
    private val daemonBaseDir: File,
    private val idleTimeoutMs: Int,
    private val periodicCheckIntervalMs: Int,
    private val singleUse: Boolean,
    private val priority: DaemonPriority,
    private val jvmOptions: MutableList<String>,
    private val instrumentationAgentAllowed: Boolean,
    private val nativeServicesMode: NativeServices.NativeServicesMode
) : DaemonServerConfiguration {
    /**
     * Creates the DefaultDaemonConfiguration that allows the use of the instrumentation agent if the latter is applied.
     */
    constructor(
        daemonUid: String,
        daemonBaseDir: File,
        idleTimeoutMs: Int,
        periodicCheckIntervalMs: Int,
        singleUse: Boolean,
        priority: DaemonPriority,
        jvmOptions: MutableList<String>,
        nativeServicesMode: NativeServices.NativeServicesMode
    ) : this(daemonUid, daemonBaseDir, idleTimeoutMs, periodicCheckIntervalMs, singleUse, priority, jvmOptions, true, nativeServicesMode)

    override fun getBaseDir(): File {
        return daemonBaseDir
    }

    override fun getIdleTimeout(): Int {
        return idleTimeoutMs
    }

    override fun getPeriodicCheckIntervalMs(): Int {
        return periodicCheckIntervalMs
    }

    override fun getUid(): String {
        return daemonUid
    }

    override fun getPriority(): DaemonPriority {
        return priority
    }

    override fun getJvmOptions(): MutableList<String> {
        return jvmOptions
    }

    override fun isSingleUse(): Boolean {
        return singleUse
    }

    override fun isInstrumentationAgentAllowed(): Boolean {
        return instrumentationAgentAllowed
    }

    override fun getNativeServicesMode(): NativeServices.NativeServicesMode {
        return nativeServicesMode
    }
}
