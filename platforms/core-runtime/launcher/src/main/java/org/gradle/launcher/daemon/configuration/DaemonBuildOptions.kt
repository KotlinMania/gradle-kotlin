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

interface DaemonServerConfiguration {
    @JvmField
    val baseDir: File?

    @JvmField
    val idleTimeout: Int

    @JvmField
    val periodicCheckIntervalMs: Int

    @JvmField
    val uid: String?

    @JvmField
    val jvmOptions: MutableList<String>?

    @JvmField
    val priority: DaemonPriority?

    @JvmField
    val isSingleUse: Boolean

    @JvmField
    val isInstrumentationAgentAllowed: Boolean

    @JvmField
    val nativeServicesMode: NativeServices.NativeServicesMode?
}
