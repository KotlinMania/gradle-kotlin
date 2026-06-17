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
package org.gradle.api.internal.tasks.compile

import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.compile.CompileOptions
import java.io.File
import java.io.Serializable

class MinimalJavaCompileOptions(compileOptions: CompileOptions) : Serializable {
    var sourcepath: MutableList<File?>?
    @JvmField
    var compilerArgs: MutableList<String?>?
    var encoding: String?
    var bootClasspath: String?
    var extensionDirs: String?
    @JvmField
    var forkOptions: MinimalJavaCompilerDaemonForkOptions?
    var debugLevel: String?
    var isDebug: Boolean
    var isDeprecation: Boolean
    var isFailOnError: Boolean
    var isListFiles: Boolean
    var isVerbose: Boolean
    var isWarnings: Boolean
    @JvmField
    var annotationProcessorGeneratedSourcesDirectory: File?
    @JvmField
    var headerOutputDirectory: File?
    var javaModuleVersion: String?
    @JvmField
    var javaModuleMainClass: String?
    private var supportsCompilerApi = false
    private var supportsConstantsAnalysis = false
    private var supportsIncrementalCompilationAfterFailure: Boolean
    @JvmField
    var previousCompilationDataFile: File? = null

    init {
        val sourcepath = compileOptions.sourcepath
        this.sourcepath = if (sourcepath == null) null else ImmutableList.copyOf<File?>(sourcepath.getFiles())
        this.compilerArgs = Lists.newArrayList<String?>(compileOptions.getAllCompilerArgs())
        this.encoding = compileOptions.encoding
        this.bootClasspath = getAsPath(compileOptions.bootstrapClasspath)
        this.extensionDirs = compileOptions.extensionDirs
        this.forkOptions = MinimalJavaCompilerDaemonForkOptions(compileOptions.forkOptions)
        this.debugLevel = compileOptions.debugOptions.debugLevel
        this.isDebug = compileOptions.isDebug()
        this.isDeprecation = compileOptions.isDeprecation()
        this.isFailOnError = compileOptions.isFailOnError()
        this.isListFiles = compileOptions.isListFiles()
        this.isVerbose = compileOptions.isVerbose()
        this.isWarnings = compileOptions.isWarnings()
        this.annotationProcessorGeneratedSourcesDirectory = compileOptions.generatedSourceOutputDirectory.getAsFile().getOrNull()
        this.headerOutputDirectory = compileOptions.headerOutputDirectory.getAsFile().getOrNull()
        this.javaModuleVersion = compileOptions.javaModuleVersion.getOrNull()
        this.javaModuleMainClass = compileOptions.javaModuleMainClass.getOrNull()
        this.supportsIncrementalCompilationAfterFailure = compileOptions.incrementalAfterFailure.getOrElse(false)
    }

    fun supportsCompilerApi(): Boolean {
        return supportsCompilerApi
    }

    fun setSupportsCompilerApi(supportsCompilerApi: Boolean) {
        this.supportsCompilerApi = supportsCompilerApi
    }

    fun supportsConstantAnalysis(): Boolean {
        return supportsConstantsAnalysis
    }

    fun setSupportsConstantAnalysis(supportsConstantsAnalysis: Boolean) {
        this.supportsConstantsAnalysis = supportsConstantsAnalysis
    }

    fun supportsIncrementalCompilationAfterFailure(): Boolean {
        return supportsIncrementalCompilationAfterFailure
    }

    fun setSupportsIncrementalCompilationAfterFailure(supportsIncrementalCompilationAfterFailure: Boolean) {
        this.supportsIncrementalCompilationAfterFailure = supportsIncrementalCompilationAfterFailure
    }

    companion object {
        private fun getAsPath(files: FileCollection?): String? {
            return if (files == null) null else files.getAsPath()
        }
    }
}
