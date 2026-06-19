/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.jvm.ModularitySpec
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.jvm.DefaultModularitySpec
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaExecSpec
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import javax.inject.Inject

class DefaultJavaExecSpec @Inject constructor(
    objectFactory: ObjectFactory,
    resolver: PathToFileResolver,
    private val fileCollectionFactory: FileCollectionFactory
) : DefaultJavaForkOptions(objectFactory, resolver, fileCollectionFactory), JavaExecSpec {
    private var ignoreExitValue = false
    private val streamsSpec = ProcessStreamsSpec()
    private val argumentsSpec = ProcessArgumentsSpec(object : ProcessArgumentsSpec.HasExecutable {
        override var executable: String?
            get() = this@DefaultJavaExecSpec.getExecutable()
            set(value) {
                this@DefaultJavaExecSpec.setExecutable(value)
            }
    })

    private val mainClass: Property<String>
    private val mainModule: Property<String>
    private val modularity: ModularitySpec
    private val jvmArguments: ListProperty<String>

    private var classpath: ConfigurableFileCollection

    init {
        this.jvmArguments = objectFactory.listProperty<String>(String::class.java)
        this.mainClass = objectFactory.property<String>(String::class.java)
        this.mainModule = objectFactory.property<String>(String::class.java)
        this.modularity = objectFactory.newInstance<DefaultModularitySpec>(DefaultModularitySpec::class.java)
        this.classpath = fileCollectionFactory.configurableFiles("classpath")
    }

    fun copyTo(targetSpec: JavaExecSpec) {
        // JavaExecSpec
        targetSpec.setArgs(getArgs())
        targetSpec.getArgumentProviders().addAll(getArgumentProviders())
        targetSpec.getMainClass().set(getMainClass())
        targetSpec.getMainModule().set(getMainModule())
        targetSpec.getModularity().getInferModulePath().set(getModularity().getInferModulePath())
        targetSpec.classpath(getClasspath())
        // BaseExecSpec
        DefaultExecSpec.Companion.copyBaseExecSpecTo(this, targetSpec)
        // Java fork options
        super.copyTo(targetSpec)
    }

    override fun getCommandLine(): MutableList<String> {
        return argumentsSpec.commandLine
    }

    override fun args(vararg args: Any): JavaExecSpec {
        argumentsSpec.args(*args)
        return this
    }

    override fun args(args: Iterable<*>): JavaExecSpec {
        argumentsSpec.args(args)
        return this
    }

    override fun setArgs(arguments: MutableList<String>): JavaExecSpec {
        argumentsSpec.setArgs(arguments)
        return this
    }

    override fun setArgs(arguments: Iterable<*>): JavaExecSpec {
        argumentsSpec.setArgs(arguments)
        return this
    }

    override fun getArgs(): MutableList<String> {
        return argumentsSpec.args
    }

    override fun getArgumentProviders(): MutableList<CommandLineArgumentProvider> {
        return argumentsSpec.argumentProviders
    }

    override fun classpath(vararg paths: Any?): JavaExecSpec {
        this.classpath.from(*paths)
        return this
    }

    override fun getClasspath(): FileCollection {
        return classpath
    }

    override fun setClasspath(classpath: FileCollection): JavaExecSpec {
        this.classpath = fileCollectionFactory.configurableFiles("classpath")
        this.classpath.setFrom(classpath)
        return this
    }

    override fun isIgnoreExitValue(): Boolean {
        return ignoreExitValue
    }

    override fun setIgnoreExitValue(ignoreExitValue: Boolean): JavaExecSpec {
        this.ignoreExitValue = ignoreExitValue
        return this
    }

    override fun getStandardInput(): InputStream {
        return streamsSpec.standardInput!!
    }

    override fun setStandardInput(standardInput: InputStream): JavaExecSpec {
        streamsSpec.setStandardInput(standardInput)
        return this
    }

    override fun getStandardOutput(): OutputStream {
        return streamsSpec.standardOutput!!
    }

    override fun setStandardOutput(standardOutput: OutputStream): JavaExecSpec {
        streamsSpec.setStandardOutput(standardOutput)
        return this
    }

    override fun getErrorOutput(): OutputStream {
        return streamsSpec.errorOutput!!
    }

    override fun setErrorOutput(errorOutput: OutputStream): JavaExecSpec {
        streamsSpec.setErrorOutput(errorOutput)
        return this
    }

    override fun getAllJvmArgs(): MutableList<String> {
        val allJvmArgs: MutableList<String> = ArrayList<String>(super.getAllJvmArgs())
        allJvmArgs.addAll(getJvmArguments().get())
        return Collections.unmodifiableList<String>(allJvmArgs)
    }

    @Deprecated("")
    override fun setAllJvmArgs(arguments: MutableList<String>?) {
        getJvmArguments().empty()
        super.setAllJvmArgs(arguments)
    }

    @Deprecated("")
    override fun setAllJvmArgs(arguments: Iterable<*>?) {
        getJvmArguments().empty()
        super.setAllJvmArgs(arguments)
    }

    override fun getJvmArguments(): ListProperty<String> {
        return jvmArguments
    }

    override fun getMainClass(): Property<String> {
        return mainClass
    }

    override fun getMainModule(): Property<String> {
        return mainModule
    }

    override fun getModularity(): ModularitySpec {
        return modularity
    }
}
