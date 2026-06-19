/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.process.internal

import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.internal.instrumentation.agent.AgentUtils
import org.gradle.internal.jvm.JavaInfo
import org.gradle.internal.jvm.Jvm
import java.lang.management.ManagementFactory
import java.util.stream.Collectors

class CurrentProcess protected constructor(@JvmField val jvm: JavaInfo?, val jvmOptions: JvmOptions?) {
    constructor(fileCollectionFactory: FileCollectionFactory) : this(Jvm.current(), inferJvmOptions(fileCollectionFactory, ManagementFactory.getRuntimeMXBean().getInputArguments()))

    val isLowMemoryProcess: Boolean
        get() = Runtime.getRuntime().maxMemory() <= 64L * 1024 * 1024 // 64MB is our default for a launcher process

    companion object {
        fun inferJvmOptions(fileCollectionFactory: FileCollectionFactory, arguments: MutableList<String?>): JvmOptions {
            // Try to infer the effective jvm options for the currently running process.
            // We only care about 'managed' jvm args, anything else is unimportant to the running build
            val jvmOptions = JvmOptions(fileCollectionFactory)
            // TODO(mlopatkin) figure out a nicer way of handling the presence of agent in the foreground daemon.
            //  Currently it is hard to have a proper "-javaagent:/path/to/jar" in clients that start the daemon, so all code deals with a boolean flag shouldApplyAgent instead.
            //  It is also possible to have the agent attached at runtime, without the flag, so flag checking is preferred.
            jvmOptions.allJvmArgs = arguments.stream().filter { arg: String? -> !AgentUtils.isGradleInstrumentationAgentSwitch(arg!!) }.collect(Collectors.toList())
            return jvmOptions
        }
    }
}
