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
package org.gradle.api.internal.tasks.compile

import com.google.common.collect.ImmutableList
import com.google.common.collect.Maps
import org.gradle.api.tasks.compile.GroovyCompileOptions
import java.io.File
import java.io.Serializable

class MinimalGroovyCompileOptions(compileOptions: GroovyCompileOptions) : Serializable {
    var isFailOnError: Boolean
    var isVerbose: Boolean
    var isListFiles: Boolean
    var encoding: String?
    var isFork: Boolean = true
    var isKeepStubs: Boolean
    @JvmField
    var fileExtensions: MutableList<String?>?
    @JvmField
    var forkOptions: MinimalGroovyCompilerDaemonForkOptions?
    var optimizationOptions: MutableMap<String?, Boolean?>?
    @JvmField
    var stubDir: File?
    var configurationScript: File?
    var isJavaAnnotationProcessing: Boolean
    var isParameters: Boolean
    var disabledGlobalASTTransformations: MutableSet<String?>?

    init {
        this.isFailOnError = compileOptions.isFailOnError()
        this.isVerbose = compileOptions.isVerbose()
        this.isListFiles = compileOptions.isListFiles()
        this.encoding = compileOptions.getEncoding()
        this.isFork = compileOptions.isFork()
        this.isKeepStubs = compileOptions.isKeepStubs()
        this.fileExtensions = ImmutableList.copyOf<String?>(compileOptions.getFileExtensions())
        this.forkOptions = MinimalGroovyCompilerDaemonForkOptions(compileOptions.getForkOptions())
        this.optimizationOptions = Maps.newHashMap<String?, Boolean?>(compileOptions.getOptimizationOptions()!!)
        this.stubDir = compileOptions.getStubDir()
        this.configurationScript = compileOptions.getConfigurationScript()
        this.isJavaAnnotationProcessing = compileOptions.isJavaAnnotationProcessing()
        this.isParameters = compileOptions.isParameters()
        this.disabledGlobalASTTransformations = compileOptions.getDisabledGlobalASTTransformations().get()
    }
}
