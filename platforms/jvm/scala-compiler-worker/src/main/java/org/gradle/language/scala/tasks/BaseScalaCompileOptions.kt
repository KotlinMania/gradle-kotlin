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
package org.gradle.language.scala.tasks

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.scala.IncrementalCompileOptions
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import java.io.Serializable
import javax.inject.Inject

/**
 * Options for Scala platform compilation.
 */
abstract class BaseScalaCompileOptions : Serializable {
    /**
     * Fail the build on compilation errors.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isFailOnError: Boolean = true

    /**
     * Generate deprecation information.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Console
    var isDeprecation: Boolean = true

    /**
     * Generate unchecked information.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Console
    var isUnchecked: Boolean = true

    /**
     * Generate debugging information.
     * Legal values: none, source, line, vars, notailcalls
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var debugLevel: String? = null

    /**
     * Run optimizations.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isOptimize: Boolean = false

    /**
     * Encoding of source files.
     */
    @get:Input
    @get:Optional
    @get:ToBeReplacedByLazyProperty
    var encoding: String? = null

    /**
     * Whether to force the compilation of all files.
     * Legal values:
     * - false (only compile modified files)
     * - true (always recompile all files)
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isForce: Boolean = false

    /**
     * Additional parameters passed to the compiler.
     * Each parameter must start with '-'.
     *
     * @return The list of additional parameters.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var additionalParameters: MutableList<String?> = ArrayList<String?>()
        /**
         * Sets the additional parameters.
         *
         *
         * Setting this property will clear any previously set additional parameters.
         */
        set(additionalParameters) {
            field.clear()
            if (additionalParameters != null) {
                field.addAll(additionalParameters)
            }
        }

    /**
     * List files to be compiled.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Console
    var isListFiles: Boolean = false

    /**
     * Specifies the amount of logging.
     * Legal values:  none, verbose, debug
     */
    @get:ToBeReplacedByLazyProperty
    @get:Console
    var loggingLevel: String? = null

    /**
     * Phases of the compiler to log.
     * Legal values: namer, typer, pickler, uncurry, tailcalls, transmatch, explicitouter, erasure,
     * lambdalift, flatten, constructors, mixin, icode, jvm, terminal.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Console
    var loggingPhases: MutableList<String?>? = null

    private val forkOptions: ScalaForkOptions = this.objectFactory.newInstance<ScalaForkOptions>(ScalaForkOptions::class.java)

    /**
     * Options for incremental compilation of Scala code.
     */
    @get:Nested
    val incrementalOptions: IncrementalCompileOptions = this.objectFactory.newInstance<IncrementalCompileOptions>(IncrementalCompileOptions::class.java)

    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    /**
     * Options for running the Scala compiler in a separate process.
     */
    @Nested
    fun getForkOptions(): ScalaForkOptions {
        return forkOptions
    }

    /**
     * Configure options for running the Scala compiler in a separate process.
     *
     * @since 8.11
     */
    fun forkOptions(action: Action<in ScalaForkOptions?>) {
        action.execute(forkOptions)
    }

    /**
     * Configure options for incremental compilation of Scala code.
     *
     * @since 8.11
     */
    fun incrementalOptions(action: Action<in IncrementalCompileOptions?>) {
        action.execute(incrementalOptions)
    }

    @get:Input
    @get:Incubating
    abstract val keepAliveMode: Property<KeepAliveMode?>?

    companion object {
        private const val serialVersionUID: Long = 0
    }
}
