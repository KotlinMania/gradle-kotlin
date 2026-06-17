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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.jvm.ModularitySpec
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.internal.jvm.DefaultModularitySpec
import org.gradle.internal.jvm.JavaModuleDetector
import org.gradle.internal.process.ArgWriter
import org.gradle.process.JavaDebugOptions
import org.gradle.process.JavaForkOptions
import org.gradle.process.internal.util.LongCommandLineDetectionUtil
import org.gradle.util.internal.CollectionUtils
import org.jspecify.annotations.NullMarked
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.URI
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.stream.Collectors
import java.util.zip.ZipEntry

/**
 * Use [JavaExecHandleFactory] instead.
 */
@NullMarked
class JavaExecHandleBuilder(
    private val fileCollectionFactory: FileCollectionFactory,
    objectFactory: ObjectFactory,
    private val temporaryFileProvider: TemporaryFileProvider,
    private val javaModuleDetector: JavaModuleDetector?,
    private val javaOptions: JavaForkOptionsInternal,
    private val execHandleBuilder: ClientExecHandleBuilder
) : BaseExecHandleBuilder, ProcessArgumentsSpec.HasExecutable {
    @JvmField
    val mainModule: Property<String>
    @JvmField
    val mainClass: Property<String>
    val jvmArguments: ListProperty<String>
    private var classpath: ConfigurableFileCollection
    @JvmField
    val modularity: ModularitySpec

    init {
        this.classpath = fileCollectionFactory.configurableFiles("classpath")
        this.mainModule = objectFactory.property<String>(String::class.java)
        this.mainClass = objectFactory.property<String>(String::class.java)
        this.jvmArguments = objectFactory.listProperty<String>(String::class.java)
        this.modularity = DefaultModularitySpec(objectFactory)
        setExecutable(javaOptions.getExecutable())
    }

    val allJvmArgs: MutableList<String>
        get() = getAllJvmArgs(this.classpath)

    private fun getAllJvmArgs(realClasspath: FileCollection): MutableList<String> {
        val allArgs: MutableList<String> = ArrayList<String>(javaOptions.getAllJvmArgs())
        val runAsModule = modularity.getInferModulePath().get() && mainModule.isPresent()

        if (runAsModule) {
            addModularJavaRunArgs(realClasspath, allArgs)
        } else {
            addClassicJavaRunArgs(realClasspath, allArgs)
        }

        return allArgs
    }

    private fun addClassicJavaRunArgs(classpath: FileCollection, allArgs: MutableList<String>) {
        if (!mainClass.isPresent()) {
            if (classpath != null && classpath.getFiles().size == 1) {
                allArgs.add("-jar")
                allArgs.add(classpath.getSingleFile().getAbsolutePath())
            } else {
                throw IllegalStateException("No main class specified and classpath is not an executable jar.")
            }
        } else {
            if (classpath != null && !classpath.isEmpty()) {
                allArgs.add("-cp")
                allArgs.add(CollectionUtils.join(File.pathSeparator, classpath))
            }
            allArgs.add(mainClass.get())
        }
    }

    private fun addModularJavaRunArgs(classpath: FileCollection, allArgs: MutableList<String>) {
        checkNotNull(javaModuleDetector) { "Running a Java module is not supported in this context." }
        val rtModulePath: FileCollection? = javaModuleDetector.inferModulePath(modularity.getInferModulePath().get(), classpath)
        val rtClasspath: FileCollection? = javaModuleDetector.inferClasspath(modularity.getInferModulePath().get(), classpath)

        if (rtClasspath != null && !rtClasspath.isEmpty()) {
            allArgs.add("-cp")
            allArgs.add(CollectionUtils.join(File.pathSeparator, rtClasspath))
        }
        if (rtModulePath != null && !rtModulePath.isEmpty()) {
            allArgs.add("--module-path")
            allArgs.add(CollectionUtils.join(File.pathSeparator, rtModulePath))
        }
        allArgs.add("--module")
        if (!mainClass.isPresent()) {
            allArgs.add(mainModule.get())
        } else {
            allArgs.add(mainModule.get() + "/" + mainClass.get())
        }
    }

    var jvmArgs: MutableList<String>
        get() = javaOptions.getJvmArgs()
        set(arguments) {
            javaOptions.setJvmArgs(arguments)
        }

    fun setJvmArgs(arguments: Iterable<*>) {
        javaOptions.setJvmArgs(arguments)
    }

    fun jvmArgs(arguments: Iterable<*>): JavaExecHandleBuilder {
        javaOptions.jvmArgs(arguments)
        return this
    }

    fun jvmArgs(vararg arguments: Any): JavaExecHandleBuilder {
        javaOptions.jvmArgs(*arguments)
        return this
    }

    var systemProperties: MutableMap<String, Any?>
        get() = javaOptions.getSystemProperties()
        set(properties) {
            javaOptions.setSystemProperties(properties)
        }

    fun systemProperties(properties: MutableMap<String, out Any?>): JavaExecHandleBuilder {
        javaOptions.systemProperties(properties)
        return this
    }

    fun systemProperty(name: String, value: Any?): JavaExecHandleBuilder {
        javaOptions.systemProperty(name, value)
        return this
    }

    var bootstrapClasspath: FileCollection
        get() = javaOptions.getBootstrapClasspath()
        set(classpath) {
            javaOptions.setBootstrapClasspath(classpath)
        }

    fun bootstrapClasspath(vararg classpath: Any): JavaExecHandleBuilder {
        javaOptions.bootstrapClasspath(*classpath)
        return this
    }

    var minHeapSize: String
        get() = javaOptions.getMinHeapSize()!!
        set(heapSize) {
            javaOptions.setMinHeapSize(heapSize)
        }

    var defaultCharacterEncoding: String
        get() = javaOptions.getDefaultCharacterEncoding()!!
        set(defaultCharacterEncoding) {
            javaOptions.setDefaultCharacterEncoding(defaultCharacterEncoding)
        }

    var maxHeapSize: String
        get() = javaOptions.getMaxHeapSize()!!
        set(heapSize) {
            javaOptions.setMaxHeapSize(heapSize)
        }

    var enableAssertions: Boolean
        get() = javaOptions.getEnableAssertions()
        set(enabled) {
            javaOptions.setEnableAssertions(enabled)
        }

    var debug: Boolean
        get() = javaOptions.getDebug()
        set(enabled) {
            javaOptions.setDebug(enabled)
        }

    val debugOptions: JavaDebugOptions
        get() = javaOptions.getDebugOptions()

    fun debugOptions(action: Action<JavaDebugOptions>) {
        javaOptions.debugOptions(action)
    }


    override fun getExecutable(): String {
        return javaOptions.getExecutable()
    }

    override fun setExecutable(executable: Any) {
        javaOptions.setExecutable(executable)
    }

    fun setExecutable(executable: String) {
        javaOptions.setExecutable(executable)
    }

    var workingDir: File?
        get() = javaOptions.getWorkingDir()
        set(dir) {
            javaOptions.setWorkingDir(dir)
        }

    fun setWorkingDir(dir: File?) {
        javaOptions.setWorkingDir(dir)
    }

    val environment: MutableMap<String, Any>
        get() = javaOptions.getEnvironment()

    fun setEnvironment(environmentVariables: MutableMap<String, *>): JavaExecHandleBuilder {
        javaOptions.setEnvironment(environmentVariables)
        return this
    }

    fun environment(environmentVariables: MutableMap<String, *>): JavaExecHandleBuilder {
        javaOptions.environment(environmentVariables)
        return this
    }

    fun environment(name: String, value: Any): JavaExecHandleBuilder {
        javaOptions.environment(name, value)
        return this
    }

    val args: MutableList<String>
        get() = execHandleBuilder.getArgs()

    fun setArgs(applicationArgs: MutableList<String>): JavaExecHandleBuilder {
        execHandleBuilder.setArgs(applicationArgs)
        return this
    }

    fun setArgs(applicationArgs: Iterable<*>): JavaExecHandleBuilder {
        execHandleBuilder.setArgs(applicationArgs)
        return this
    }

    fun args(vararg args: Any): JavaExecHandleBuilder {
        execHandleBuilder.args(*args)
        return this
    }

    fun args(args: Iterable<*>): JavaExecHandleBuilder {
        execHandleBuilder.args(args)
        return this
    }

    val argumentProviders: MutableList<CommandLineArgumentProvider>
        get() = execHandleBuilder.getArgumentProviders()

    fun setClasspath(classpath: FileCollection): JavaExecHandleBuilder {
        // we need to create a new file collection container to avoid cycles. See: https://github.com/gradle/gradle/issues/8755
        val newClasspath = fileCollectionFactory.configurableFiles("classpath")
        newClasspath.setFrom(classpath)
        this.classpath = newClasspath
        return this
    }

    fun classpath(vararg paths: Any): JavaExecHandleBuilder {
        this.classpath.from(*paths)
        return this
    }

    fun getClasspath(): FileCollection {
        return classpath
    }

    val allArguments: MutableList<String>
        get() = getAllArguments(this.classpath)

    private fun getAllArguments(realClasspath: FileCollection): MutableList<String> {
        val arguments: MutableList<String> = ArrayList<String>(getAllJvmArgs(realClasspath))
        arguments.addAll(execHandleBuilder.getAllArguments())
        return arguments
    }

    val jvmArgumentProviders: MutableList<CommandLineArgumentProvider>
        get() = javaOptions.getJvmArgumentProviders()

    var standardInput: InputStream
        get() = execHandleBuilder.getStandardInput()
        set(inputStream) {
            execHandleBuilder.setStandardInput(inputStream)
        }

    val standardOutput: OutputStream
        get() = execHandleBuilder.getStandardOutput()

    override fun setStandardOutput(outputStream: OutputStream): JavaExecHandleBuilder {
        execHandleBuilder.setStandardOutput(outputStream)
        return this
    }

    val errorOutput: OutputStream
        get() = execHandleBuilder.getErrorOutput()

    override fun setErrorOutput(outputStream: OutputStream): JavaExecHandleBuilder {
        execHandleBuilder.setErrorOutput(outputStream)
        return this
    }

    val commandLine: MutableList<String>
        get() {
            val commandLine: MutableList<String> = ArrayList<String>()
            commandLine.add(getExecutable())
            commandLine.addAll(this.allArguments)
            return commandLine
        }

    override fun listener(listener: ExecHandleListener): JavaExecHandleBuilder {
        execHandleBuilder.listener(listener)
        return this
    }

    override fun setDisplayName(displayName: String?): JavaExecHandleBuilder {
        execHandleBuilder.setDisplayName(displayName)
        return this
    }

    private val effectiveArguments: MutableList<String>
        get() {
            val arguments = this.allArguments

            // Try to shorten command-line if necessary
            if (LongCommandLineDetectionUtil.hasCommandLineExceedMaxLength(getExecutable(), arguments)) {
                // TODO: This is an ugly check that relies on Java 9 command-line
                // arguments to detect that we're about to run on Java 9+
                // This should actually base this decision (and support for modules)
                // off of the detected version of Java we're about to use.
                if (arguments.contains("--module") || arguments.contains("--module--path")) {
                    return shortenJava9Arguments(arguments)
                } else {
                    return shortenJava8Arguments(arguments)
                }
            }

            return arguments
        }

    private fun shortenJava8Arguments(arguments: MutableList<String>): MutableList<String> {
        val pathingJarFile: File?
        try {
            pathingJarFile = writePathingJarFile(classpath)
        } catch (e: IOException) {
            LOGGER.info("Pathing JAR could not be created, Gradle cannot shorten the command line.", e)
            return arguments
        }
        val shortenedClasspath = fileCollectionFactory.configurableFiles()
        shortenedClasspath.from(pathingJarFile)
        val shortenedArguments = getAllArguments(shortenedClasspath)
        LOGGER.info("Shortening Java classpath {} with {}", this.classpath.getFiles(), pathingJarFile)
        return shortenedArguments
    }

    @Throws(IOException::class)
    private fun writePathingJarFile(classpath: FileCollection): File {
        val pathingJarFile = temporaryFileProvider.createTemporaryFile("gradle-javaexec-classpath", ".jar")
        FileOutputStream(pathingJarFile).use { fileOutputStream ->
            JarOutputStream(fileOutputStream, toManifest(classpath)).use { jarOutputStream ->
                jarOutputStream.putNextEntry(ZipEntry("META-INF/"))
            }
        }
        return pathingJarFile
    }

    private fun shortenJava9Arguments(arguments: MutableList<String>): MutableList<String> {
        LOGGER.info("Command line too long - creating argsfile(s)")
        val effectiveArguments: MutableList<String> = ArrayList<String>()
        val argsFileContents: MutableList<String> = ArrayList<String>(arguments.size)
        try {
            for (arg in arguments) {
                if (arg.startsWith("@")) {
                    if (!argsFileContents.isEmpty()) {
                        createArgsFile(argsFileContents, effectiveArguments)
                        argsFileContents.clear()
                        continue
                    }
                    effectiveArguments.add(arg)
                    continue
                }
                argsFileContents.add(arg)
            }
            if (!argsFileContents.isEmpty()) {
                createArgsFile(argsFileContents, effectiveArguments)
            }
        } catch (e: IOException) {
            LOGGER.info("args file could not be created, Gradle cannot shorten the command line.", e)
            return arguments
        }
        LOGGER.info("effective arguments {}", effectiveArguments)
        return effectiveArguments
    }

    @Throws(IOException::class)
    private fun createArgsFile(argsFileContents: MutableList<String>, effectiveArguments: MutableList<String>) {
        val argsFile = temporaryFileProvider.createTemporaryFile("args", ".txt")
        effectiveArguments.addAll(ArgWriter.javaStyle().generateArgsFile(argsFileContents, argsFile))
    }

    fun redirectErrorStream(): JavaExecHandleBuilder {
        execHandleBuilder.redirectErrorStream()
        return this
    }

    fun copyJavaForkOptions(source: JavaForkOptions) {
        source.copyTo(javaOptions)
    }

    fun copyJavaForkOptions(source: EffectiveJavaForkOptions.ReadOnlyJvmOptions) {
        source.copyTo(javaOptions)
    }

    override fun build(): ExecHandle {
        // We delegate properties that are also on ProcessForkOptions interface to JavaForkOptions
        // to support copy from JavaOptions, and thus we have to copy them to execHandleBuilder here
        execHandleBuilder.setExecutable(getExecutable())
        execHandleBuilder.setWorkingDir(this.workingDir)
        execHandleBuilder.setEnvironment(this.environment)
        return execHandleBuilder.buildWithEffectiveArguments(this.effectiveArguments)
    }

    companion object {
        private val LOGGER: Logger = getLogger(JavaExecHandleBuilder::class.java)!!

        private fun toManifest(classpath: FileCollection): Manifest {
            val manifest = Manifest()
            val attributes = manifest.getMainAttributes()
            attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
            attributes.putValue("Class-Path", classpath.getFiles().stream().map<URI> { obj: File? -> obj!!.toURI() }.map<String> { obj: URI? -> obj.toString() }.collect(Collectors.joining(" ")))
            return manifest
        }
    }
}
