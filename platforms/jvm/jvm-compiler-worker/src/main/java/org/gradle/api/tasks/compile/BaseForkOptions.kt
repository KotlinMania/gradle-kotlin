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
package org.gradle.api.tasks.compile

import org.apache.commons.lang3.StringUtils
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import java.io.Serializable
import java.util.Objects
import java.util.stream.Collectors

/**
 * Fork options for compilation. Only take effect if `fork`
 * is `true`.
 */
open class BaseForkOptions : Serializable {
    /**
     * Returns the initial heap size for the compiler process.
     * Defaults to `null`, in which case the JVM's default will be used.
     */
    /**
     * Sets the initial heap size for the compiler process.
     * Defaults to `null`, in which case the JVM's default will be used.
     */
    @JvmField
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var memoryInitialSize: String? = null

    /**
     * Returns the maximum heap size for the compiler process.
     * Defaults to `null`, in which case the JVM's default will be used.
     */
    /**
     * Sets the maximum heap size for the compiler process.
     * Defaults to `null`, in which case the JVM's default will be used.
     */
    @JvmField
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var memoryMaximumSize: String? = null

    /**
     * Returns any additional JVM arguments for the compiler process.
     * Defaults to the empty list.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var jvmArgs: MutableList<String?>? = ArrayList<String?>()
        /**
         * Sets any additional JVM arguments for the compiler process.
         * Defaults to the empty list. Empty or null arguments are filtered out because they cause
         * JVM Launch to fail.
         */
        set(jvmArgs) {
            field = if (jvmArgs == null) null else jvmArgs.stream()
                .filter { obj: String? -> Objects.nonNull(obj) }
                .filter { string: String? -> !StringUtils.isBlank(string) }
                .collect(Collectors.toList())
        }

    companion object {
        private const val serialVersionUID: Long = 0
    }
}
