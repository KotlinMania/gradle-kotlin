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
package org.gradle.api.internal.tasks.scala

import com.google.common.collect.ImmutableList
import org.gradle.api.tasks.scala.IncrementalCompileOptions
import org.gradle.language.scala.tasks.BaseScalaCompileOptions
import org.gradle.language.scala.tasks.KeepAliveMode
import java.io.Serializable

class MinimalScalaCompileOptions(compileOptions: BaseScalaCompileOptions) : Serializable {
    var isFailOnError: Boolean = true
    var isDeprecation: Boolean = true
    var isUnchecked: Boolean = true
    var debugLevel: String?
    var isOptimize: Boolean
    var encoding: String?
    var isForce: Boolean
    var additionalParameters: MutableList<String?>?
    var isListFiles: Boolean
    var loggingLevel: String?
    var loggingPhases: MutableList<String?>?
    var forkOptions: MinimalScalaCompilerDaemonForkOptions?

    @Transient
    var incrementalOptions: IncrementalCompileOptions?
    val keepAliveMode: KeepAliveMode

    init {
        this.isFailOnError = compileOptions.isFailOnError()
        this.isDeprecation = compileOptions.isDeprecation()
        this.isUnchecked = compileOptions.isUnchecked()
        this.debugLevel = compileOptions.getDebugLevel()
        this.isOptimize = compileOptions.isOptimize()
        this.encoding = compileOptions.getEncoding()
        this.isForce = compileOptions.isForce()
        this.additionalParameters = ImmutableList.copyOf<String?>(compileOptions.getAdditionalParameters())
        this.isListFiles = compileOptions.isListFiles()
        this.loggingLevel = compileOptions.getLoggingLevel()
        this.loggingPhases = if (compileOptions.getLoggingPhases() == null) null else ImmutableList.copyOf<String?>(compileOptions.getLoggingPhases())
        this.forkOptions = MinimalScalaCompilerDaemonForkOptions(compileOptions.getForkOptions())
        this.incrementalOptions = compileOptions.getIncrementalOptions()
        this.keepAliveMode = compileOptions.getKeepAliveMode().get()
    }
}
