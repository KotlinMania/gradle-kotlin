/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.file.FileCollection
import org.gradle.api.jvm.ModularitySpec
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.process.BaseExecSpec
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.ExecResult
import org.gradle.process.JavaDebugOptions
import org.gradle.process.JavaExecSpec
import org.gradle.process.JavaForkOptions
import org.gradle.process.ProcessForkOptions
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Use [ExecActionFactory] (for core code) or [org.gradle.process.ExecOperations] (for plugin code) instead.
 *
 * TODO: We should remove setters and have abstract getters in Gradle 10 and configure builder in execute() method.
 */
class DefaultJavaExecAction(private val javaExecHandleBuilder: JavaExecHandleBuilder) : JavaExecAction {
    private var ignoreExitValue = false

    override fun execute(): ExecResult {
        val execHandle = javaExecHandleBuilder.build()
        val execResult = execHandle.start().waitForFinish()
        if (!ignoreExitValue) {
            execResult.assertNormalExitValue()
        }
        return execResult
    }

    override fun getJvmArguments(): ListProperty<String> {
        return javaExecHandleBuilder.jvmArguments
    }

    override fun getMainModule(): Property<String> {
        return javaExecHandleBuilder.mainModule
    }

    override fun getMainClass(): Property<String> {
        return javaExecHandleBuilder.mainClass
    }

    override fun getArgs(): MutableList<String> {
        return javaExecHandleBuilder.args
    }

    override fun args(vararg args: Any): JavaExecSpec {
        javaExecHandleBuilder.args(*args)
        return this
    }

    override fun args(args: Iterable<*>): JavaExecSpec {
        javaExecHandleBuilder.args(args)
        return this
    }

    override fun setArgs(args: MutableList<String>): JavaExecSpec {
        javaExecHandleBuilder.setArgs(args)
        return this
    }

    override fun setArgs(args: Iterable<*>): JavaExecSpec {
        javaExecHandleBuilder.setArgs(args)
        return this
    }

    override fun getArgumentProviders(): MutableList<CommandLineArgumentProvider> {
        return javaExecHandleBuilder.argumentProviders
    }

    override fun classpath(vararg paths: Any?): JavaExecSpec {
        javaExecHandleBuilder.classpath(*paths)
        return this
    }

    override fun getClasspath(): FileCollection {
        return javaExecHandleBuilder.getClasspath()
    }

    override fun setClasspath(classpath: FileCollection): JavaExecSpec {
        javaExecHandleBuilder.setClasspath(classpath)
        return this
    }

    override fun getModularity(): ModularitySpec {
        return javaExecHandleBuilder.modularity
    }

    override fun setIgnoreExitValue(ignoreExitValue: Boolean): BaseExecSpec {
        this.ignoreExitValue = ignoreExitValue
        return this
    }

    override fun isIgnoreExitValue(): Boolean {
        return ignoreExitValue
    }

    override fun setStandardInput(inputStream: InputStream): BaseExecSpec {
        javaExecHandleBuilder.standardInput = inputStream
        return this
    }

    override fun getStandardInput(): InputStream {
        return javaExecHandleBuilder.standardInput
    }

    override fun setStandardOutput(outputStream: OutputStream): BaseExecSpec {
        javaExecHandleBuilder.setStandardOutput(outputStream)
        return this
    }

    override fun getStandardOutput(): OutputStream {
        return javaExecHandleBuilder.standardOutput
    }

    override fun setErrorOutput(outputStream: OutputStream): BaseExecSpec {
        javaExecHandleBuilder.setErrorOutput(outputStream)
        return this
    }

    override fun getErrorOutput(): OutputStream {
        return javaExecHandleBuilder.errorOutput
    }

    override fun getCommandLine(): MutableList<String> {
        return javaExecHandleBuilder.commandLine
    }

    override fun getSystemProperties(): MutableMap<String, Any?> {
        return javaExecHandleBuilder.systemProperties
    }

    override fun setSystemProperties(properties: MutableMap<String, out Any?>) {
        javaExecHandleBuilder.systemProperties = properties.toMutableMap()
    }

    override fun systemProperties(properties: MutableMap<String, out Any?>): JavaForkOptions {
        javaExecHandleBuilder.systemProperties(properties)
        return this
    }

    override fun systemProperty(name: String, value: Any?): JavaForkOptions {
        javaExecHandleBuilder.systemProperty(name, value)
        return this
    }

    override fun getDefaultCharacterEncoding(): String? {
        return javaExecHandleBuilder.defaultCharacterEncoding
    }

    override fun setDefaultCharacterEncoding(defaultCharacterEncoding: String?) {
        javaExecHandleBuilder.defaultCharacterEncoding = defaultCharacterEncoding!!
    }

    override fun getMinHeapSize(): String? {
        return javaExecHandleBuilder.minHeapSize
    }

    override fun setMinHeapSize(heapSize: String?) {
        javaExecHandleBuilder.minHeapSize = heapSize!!
    }

    override fun getMaxHeapSize(): String? {
        return javaExecHandleBuilder.maxHeapSize
    }

    override fun setMaxHeapSize(heapSize: String?) {
        javaExecHandleBuilder.maxHeapSize = heapSize!!
    }

    override fun getJvmArgs(): MutableList<String> {
        return javaExecHandleBuilder.jvmArgs
    }

    override fun setJvmArgs(arguments: MutableList<String>) {
        javaExecHandleBuilder.jvmArgs = arguments
    }

    override fun setJvmArgs(arguments: Iterable<*>) {
        javaExecHandleBuilder.setJvmArgs(arguments)
    }

    override fun jvmArgs(arguments: Iterable<*>): JavaForkOptions {
        javaExecHandleBuilder.jvmArgs(arguments)
        return this
    }

    override fun jvmArgs(vararg arguments: Any): JavaForkOptions {
        javaExecHandleBuilder.jvmArgs(*arguments)
        return this
    }

    override fun getJvmArgumentProviders(): MutableList<CommandLineArgumentProvider> {
        return javaExecHandleBuilder.jvmArgumentProviders
    }

    override fun getBootstrapClasspath(): FileCollection {
        return javaExecHandleBuilder.bootstrapClasspath
    }

    override fun setBootstrapClasspath(classpath: FileCollection) {
        javaExecHandleBuilder.bootstrapClasspath = classpath
    }

    override fun bootstrapClasspath(vararg classpath: Any?): JavaForkOptions {
        javaExecHandleBuilder.bootstrapClasspath(*classpath)
        return this
    }

    override fun getEnableAssertions(): Boolean {
        return javaExecHandleBuilder.enableAssertions
    }

    override fun setEnableAssertions(enabled: Boolean) {
        javaExecHandleBuilder.enableAssertions = enabled
    }

    override fun getDebug(): Boolean {
        return javaExecHandleBuilder.debug
    }

    override fun setDebug(enabled: Boolean) {
        javaExecHandleBuilder.debug = enabled
    }

    override fun getDebugOptions(): JavaDebugOptions {
        return javaExecHandleBuilder.debugOptions
    }

    override fun debugOptions(action: Action<JavaDebugOptions>) {
        javaExecHandleBuilder.debugOptions(action)
    }

    override fun getAllJvmArgs(): MutableList<String> {
        return javaExecHandleBuilder.allJvmArgs
    }

    @Deprecated("")
    override fun setAllJvmArgs(arguments: MutableList<String?>?) {
        throw UnsupportedOperationException()
    }

    @Deprecated("")
    override fun setAllJvmArgs(arguments: Iterable<*>?) {
        throw UnsupportedOperationException()
    }

    override fun getExecutable(): String {
        return javaExecHandleBuilder.executable!!
    }

    override fun setExecutable(executable: String) {
        javaExecHandleBuilder.setExecutable(executable)
    }

    override fun setExecutable(executable: Any) {
        javaExecHandleBuilder.setExecutable(executable)
    }

    override fun executable(executable: Any): ProcessForkOptions {
        javaExecHandleBuilder.setExecutable(executable)
        return this
    }

    override fun getWorkingDir(): File? {
        return javaExecHandleBuilder.workingDir
    }

    override fun setWorkingDir(dir: File?) {
        javaExecHandleBuilder.setWorkingDir(dir)
    }

    override fun setWorkingDir(dir: Any?) {
        javaExecHandleBuilder.setWorkingDir(dir)
    }

    override fun workingDir(dir: Any?): ProcessForkOptions {
        javaExecHandleBuilder.setWorkingDir(dir)
        return this
    }

    override fun getEnvironment(): MutableMap<String?, Any?> {
        @Suppress("UNCHECKED_CAST")
        return javaExecHandleBuilder.environment as MutableMap<String?, Any?>
    }

    override fun setEnvironment(environmentVariables: MutableMap<String, *>) {
        javaExecHandleBuilder.setEnvironment(environmentVariables)
    }

    override fun environment(environmentVariables: MutableMap<String, *>): ProcessForkOptions {
        javaExecHandleBuilder.environment(environmentVariables)
        return this
    }

    override fun environment(name: String, value: Any): ProcessForkOptions {
        javaExecHandleBuilder.environment(name, value)
        return this
    }

    override fun listener(listener: ExecHandleListener?): JavaExecAction {
        if (listener != null) {
            javaExecHandleBuilder.listener(listener)
        }
        return this
    }

    override fun copyTo(options: ProcessForkOptions?): ProcessForkOptions? {
        throw UnsupportedOperationException()
    }

    override fun copyTo(options: JavaForkOptions?): JavaForkOptions? {
        throw UnsupportedOperationException()
    }
}
