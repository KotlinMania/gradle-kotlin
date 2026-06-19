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
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.jvm.Jvm
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaDebugOptions
import org.gradle.process.JavaForkOptions
import java.util.Arrays
import javax.inject.Inject

open class DefaultJavaForkOptions @Inject constructor(
    objectFactory: ObjectFactory,
    resolver: PathToFileResolver,
    private val fileCollectionFactory: FileCollectionFactory
) : DefaultProcessForkOptions(resolver), JavaForkOptionsInternal {
    private val options: JvmOptions
    private val debugOptions: JavaDebugOptions
    private var jvmArgumentProviders: MutableList<CommandLineArgumentProvider>? = null

    init {
        this.debugOptions = objectFactory.newInstance<DefaultJavaDebugOptions>(DefaultJavaDebugOptions::class.java, objectFactory)
        this.options = JvmOptions(fileCollectionFactory, JvmDebugSpec.JavaDebugOptionsBackedSpec(debugOptions))
    }

    override fun getAllJvmArgs(): MutableList<String> {
        if (hasJvmArgumentProviders(this)) {
            val copy = options.createCopy(fileCollectionFactory)
            for (jvmArgumentProvider in jvmArgumentProviders!!) {
                copy.jvmArgs(jvmArgumentProvider.asArguments())
            }
            return copy.allJvmArgs.filterNotNull().toMutableList()
        } else {
            return options.allJvmArgs.filterNotNull().toMutableList()
        }
    }

    @Deprecated("")
    override fun setAllJvmArgs(arguments: MutableList<String>?) {
        deprecateMethod(DefaultJavaForkOptions::class.java, "setAllJvmArgs")
            .withAdvice("Use `jvmArgs()`, `setJvmArgs()`, or `getJvmArgumentProviders()` instead to set JVM arguments.")!!
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "set-all-jvm-args")!!
            .nagUser()
        options.allJvmArgs = arguments?.toMutableList() ?: mutableListOf()
        if (hasJvmArgumentProviders(this)) {
            jvmArgumentProviders!!.clear()
        }
    }

    @Deprecated("")
    override fun setAllJvmArgs(arguments: Iterable<*>?) {
        deprecateMethod(DefaultJavaForkOptions::class.java, "setAllJvmArgs")
            .withAdvice("Use `jvmArgs()`, `setJvmArgs()`, or `getJvmArgumentProviders()` instead to set JVM arguments.")!!
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "set-all-jvm-args")!!
            .nagUser()
        options.allJvmArgs = arguments?.map { it.toString() }?.toMutableList() ?: mutableListOf()
        if (hasJvmArgumentProviders(this)) {
            jvmArgumentProviders!!.clear()
        }
    }

    override fun getJvmArgs(): MutableList<String> {
        return options.jvmArgs.filterNotNull().toMutableList()
    }

    override fun setJvmArgs(arguments: MutableList<String>) {
        options.jvmArgs = arguments.toMutableList()
    }

    override fun setJvmArgs(arguments: Iterable<*>) {
        options.jvmArgs = arguments.map { it.toString() }.toMutableList()
    }

    override fun jvmArgs(arguments: Iterable<*>): JavaForkOptions {
        options.jvmArgs(arguments)
        return this
    }

    override fun jvmArgs(vararg arguments: Any?): JavaForkOptions {
        jvmArgs(Arrays.asList<Any?>(*arguments))
        return this
    }

    override fun getJvmArgumentProviders(): MutableList<CommandLineArgumentProvider> {
        if (jvmArgumentProviders == null) {
            jvmArgumentProviders = ArrayList<CommandLineArgumentProvider>()
        }
        return jvmArgumentProviders!!
    }

    override fun getSystemProperties(): MutableMap<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return options.mutableSystemProperties as MutableMap<String, Any?>
    }

    override fun setSystemProperties(properties: MutableMap<String, out Any?>) {
        @Suppress("UNCHECKED_CAST")
        options.setSystemProperties(properties as MutableMap<String?, *>)
    }

    override fun systemProperties(properties: MutableMap<String, out Any?>): JavaForkOptions {
        @Suppress("UNCHECKED_CAST")
        options.systemProperties(properties as MutableMap<String?, *>)
        return this
    }

    override fun systemProperty(name: String?, value: Any?): JavaForkOptions {
        options.systemProperty(name, value)
        return this
    }

    override fun getBootstrapClasspath(): FileCollection {
        return options.getBootstrapClasspath()
    }

    override fun setBootstrapClasspath(classpath: FileCollection) {
        options.setBootstrapClasspath(classpath)
    }

    override fun bootstrapClasspath(vararg classpath: Any?): JavaForkOptions {
        options.bootstrapClasspath(*classpath)
        return this
    }

    override fun getMinHeapSize(): String? {
        return options.minHeapSize
    }

    override fun setMinHeapSize(heapSize: String?) {
        options.minHeapSize = heapSize
    }

    override fun getMaxHeapSize(): String? {
        return options.maxHeapSize
    }

    override fun setMaxHeapSize(heapSize: String?) {
        options.maxHeapSize = heapSize
    }

    override fun getDefaultCharacterEncoding(): String? {
        return options.defaultCharacterEncoding
    }

    override fun setDefaultCharacterEncoding(defaultCharacterEncoding: String?) {
        options.defaultCharacterEncoding = defaultCharacterEncoding
    }

    override fun getEnableAssertions(): Boolean {
        return options.enableAssertions
    }

    override fun setEnableAssertions(enabled: Boolean) {
        options.enableAssertions = enabled
    }

    override fun getDebug(): Boolean {
        return options.debug
    }

    override fun setDebug(enabled: Boolean) {
        options.debug = enabled
    }

    override fun getDebugOptions(): JavaDebugOptions {
        return debugOptions
    }

    override fun debugOptions(action: Action<JavaDebugOptions>) {
        action.execute(getDebugOptions())
    }

    override fun getInheritableEnvironment(): MutableMap<String?, *> {
        // Filter out any environment variables that should not be inherited.
        @Suppress("UNCHECKED_CAST")
        return Jvm.getInheritableEnvironmentVariables(super.getInheritableEnvironment()) as MutableMap<String?, *>
    }

    override fun copyTo(target: JavaForkOptions): JavaForkOptions {
        super.copyTo(target)
        options.copyTo(target)
        if (jvmArgumentProviders != null) {
            for (jvmArgumentProvider in jvmArgumentProviders!!) {
                target.jvmArgs(jvmArgumentProvider.asArguments())
            }
        }
        return this
    }

    override fun checkDebugConfiguration(arguments: Iterable<*>?) {
        options.checkDebugConfiguration(arguments ?: emptyList<Any>())
    }

    override fun toEffectiveJavaForkOptions(fileCollectionFactory: FileCollectionFactory?): EffectiveJavaForkOptions {
        val copy = options.createCopy(fileCollectionFactory!!)
        if (jvmArgumentProviders != null) {
            for (jvmArgumentProvider in jvmArgumentProviders!!) {
                copy.jvmArgs(jvmArgumentProvider.asArguments())
            }
        }
        return EffectiveJavaForkOptions(
            getExecutable()!!,
            getWorkingDir()!!,
            getEnvironment(),
            copy
        )
    }

    override var extraJvmArgs: Iterable<*>?
        get() = options.getExtraJvmArgs()
        set(arguments) {
            options.setExtraJvmArgs(arguments ?: emptyList<Any>())
        }

    companion object {
        private fun hasJvmArgumentProviders(forkOptions: DefaultJavaForkOptions): Boolean {
            return forkOptions.jvmArgumentProviders != null && forkOptions.jvmArgumentProviders!!.isNotEmpty()
        }
    }
}
