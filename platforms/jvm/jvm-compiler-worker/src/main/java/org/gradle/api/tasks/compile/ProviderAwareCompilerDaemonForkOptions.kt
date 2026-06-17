/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.tasks.compile

import com.google.common.collect.ImmutableList
import org.gradle.api.Incubating
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.util.internal.CollectionUtils

/**
 * Fork options for compilation that can accept user-defined [CommandLineArgumentProvider] objects.
 *
 * Only take effect if `fork` is `true`.
 *
 * @since 7.1
 */
@Incubating
open class ProviderAwareCompilerDaemonForkOptions : BaseForkOptions() {
    /**
     * Returns any additional JVM argument providers for the compiler process.
     *
     */
    @get:ToBeReplacedByLazyProperty
    @get:Nested
    @get:Optional
    val jvmArgumentProviders: MutableList<CommandLineArgumentProvider> = ArrayList<CommandLineArgumentProvider>()

    @get:ToBeReplacedByLazyProperty
    @get:Internal
    val allJvmArgs: MutableList<String?>
        /**
         * Returns the full set of arguments to use to launch the JVM for the compiler process. This includes arguments to define
         * system properties, the minimum/maximum heap size, and the bootstrap classpath.
         *
         * @return The immutable list of arguments. Returns an empty list if there are no arguments.
         */
        get() {
            val builder = ImmutableList.builder<String?>()
            builder.addAll(CollectionUtils.stringize(getJvmArgs()!!))
            for (argumentProvider in this.jvmArgumentProviders) {
                builder.addAll(CollectionUtils.toStringList(argumentProvider.asArguments()!!))
            }
            return builder.build()
        }
}
