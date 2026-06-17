/*
 * Copyright 2007 the original author or authors.
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

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import java.io.File

/**
 * Fork options for Java compilation. Only take effect if `CompileOptions.fork` is `true`.
 */
abstract class ForkOptions : ProviderAwareCompilerDaemonForkOptions() {
    /**
     * Returns the compiler executable to be used.
     *
     *
     * Only takes effect if `CompileOptions.fork` is `true`. Defaults to `null`.
     *
     *
     * Setting the executable disables task output caching.
     */
    /**
     * Sets the compiler executable to be used.
     *
     *
     * Only takes effect if `CompileOptions.fork` is `true`. Defaults to `null`.
     *
     *
     * Setting the executable disables task output caching.
     */
    @JvmField
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var executable: String? = null

    /**
     * Returns the directory used for temporary files that may be created to pass
     * command line arguments to the compiler process. Defaults to `null`,
     * in which case the directory will be chosen automatically.
     */
    /**
     * Sets the directory used for temporary files that may be created to pass
     * command line arguments to the compiler process. Defaults to `null`,
     * in which case the directory will be chosen automatically.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var tempDir: String? = null

    /**
     * Returns the Java home which contains the compiler to use.
     *
     *
     * Only takes effect if `CompileOptions.fork` is `true`. Defaults to `null`.
     *
     * @since 3.5
     */
    /**
     * Sets the Java home which contains the compiler to use.
     *
     *
     * Only takes effect if `CompileOptions.fork` is `true`. Defaults to `null`.
     *
     * @since 3.5
     */
    @JvmField
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var javaHome: File? = null

    companion object {
        private const val serialVersionUID: Long = 0
    }
}
