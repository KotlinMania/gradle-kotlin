/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.testing.jacoco.plugins

import com.google.common.base.Joiner
import org.apache.commons.lang3.StringUtils
import org.gradle.api.Transformer
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.provider.Providers
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.internal.jacoco.JacocoAgentJar
import org.gradle.process.JavaForkOptions
import org.gradle.util.internal.RelativePathUtil
import java.io.File
import javax.inject.Inject

/**
 * Extension for tasks that should run with a Jacoco agent to generate coverage execution data.
 */
abstract class JacocoTaskExtension @Inject constructor(objects: ObjectFactory, private val agent: JacocoAgentJar, private val task: JavaForkOptions) {
    /**
     * The types of output that the agent can use for execution data.
     */
    enum class Output {
        FILE,
        TCP_SERVER,
        TCP_CLIENT,
        NONE;

        val asArg: String
            /**
             * Gets type in format of agent argument.
             */
            get() = toString().lowercase().replace("_".toRegex(), "")
    }

    /**
     * Whether or not the task should generate execution data. Defaults to `true`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isEnabled: Boolean = true
    private val destinationFile: RegularFileProperty

    /**
     * List of class names that should be included in analysis. Names can use wildcards (* and ?). If left empty, all classes will be included. Defaults to an empty list.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var includes: MutableList<String>? = ArrayList<String>()

    /**
     * List of class names that should be excluded from analysis. Names can use wildcard (* and ?). Defaults to an empty list.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var excludes: MutableList<String>? = ArrayList<String>()

    /**
     * List of classloader names that should be excluded from analysis. Names can use wildcards (* and ?). Defaults to an empty list.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var excludeClassLoaders: MutableList<String>? = ArrayList<String>()

    /**
     * Whether or not classes without source location should be instrumented. Defaults to `false`.
     *
     * This property is only taken into account if the used JaCoCo version supports this option (JaCoCo version &gt;= 0.7.6)
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isIncludeNoLocationClasses: Boolean = false

    /**
     * An identifier for the session written to the execution data. Defaults to an auto-generated identifier.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var sessionId: String? = null

    /**
     * Whether or not to dump the coverage data at VM shutdown. Defaults to `true`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isDumpOnExit: Boolean = true

    /**
     * The type of output to generate. Defaults to [Output.FILE].
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var output: Output = Output.FILE

    /**
     * IP address or hostname to use with [Output.TCP_SERVER] or [Output.TCP_CLIENT]. Defaults to localhost.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var address: String? = null

    /**
     * Port to bind to for [Output.TCP_SERVER] or [Output.TCP_CLIENT]. Defaults to 6300.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var port: Int = 0
    /**
     * Path to dump all class files the agent sees are dumped to. Defaults to no dumps.
     *
     * @since 3.4
     */
    /**
     * Sets path to dump all class files the agent sees are dumped to. Defaults to no dumps.
     *
     * @since 3.4
     */
    @get:ToBeReplacedByLazyProperty
    @get:LocalState
    @get:Optional
    var classDumpDir: File? = null

    /**
     * Whether or not to expose functionality via JMX under `org.jacoco:type=Runtime`. Defaults to `false`.
     *
     * The configuration of the jmx property is only taken into account if the used JaCoCo version supports this option (JaCoCo version &gt;= 0.6.2)
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isJmx: Boolean = false

    /**
     * Creates a Jacoco task extension.
     *
     * @param objects the object factory
     * @param agent the agent JAR to use for analysis
     * @param task the task we extend
     */
    init {
        destinationFile = objects.fileProperty()
    }

    /**
     * The path for the execution data to be written to.
     */
    @Optional
    @OutputFile
    @ToBeReplacedByLazyProperty
    fun getDestinationFile(): File? {
        return destinationFile.getAsFile().getOrNull()
    }

    /**
     * Set the provider for calculating the destination file.
     *
     * @param destinationFile Destination file provider
     * @since 4.0
     */
    fun setDestinationFile(destinationFile: Provider<File>) {
        // TODO: This is a workaround for behavior in AGP.
        // see https://github.com/gradle/gradle/issues/33389
        // This can be removed once we've fixed RegularFileProperty.fileProvider(...) to work properly
        this.destinationFile.fileProvider(destinationFile.flatMap<File>(Transformer { value: File? -> Providers.of(value!!) }))
    }

    fun setDestinationFile(destinationFile: File?) {
        this.destinationFile.set(destinationFile)
    }

    @get:ToBeReplacedByLazyProperty
    @get:Classpath
    val agentClasspath: FileCollection
        /**
         * The Jacoco agent classpath.
         *
         * This contains only one file - the agent jar.
         *
         * @since 4.6
         */
        get() = agent.getAgentConf()

    @get:ToBeReplacedByLazyProperty
    @get:Internal
    val asJvmArg: String
        /**
         * Gets all properties in the format expected of the agent JVM argument.
         *
         * @return state of extension in a JVM argument
         */
        get() {
            val builder = StringBuilder()
            val argument =
                ArgumentAppender(builder, task.getWorkingDir())
            builder.append("-javaagent:")
            builder.append(agent.getJar().getAbsolutePath())
            builder.append('=')
            argument.append("destfile", getDestinationFile())
            argument.append("append", true)
            argument.append("includes", this.includes)
            argument.append("excludes", this.excludes)
            argument.append("exclclassloader", this.excludeClassLoaders)
            if (agent.supportsInclNoLocationClasses()) {
                argument.append("inclnolocationclasses", this.isIncludeNoLocationClasses)
            }
            argument.append("sessionid", this.sessionId)
            argument.append("dumponexit", this.isDumpOnExit)
            argument.append("output", this.output.asArg)
            argument.append("address", this.address)
            argument.append("port", this.port)
            argument.append("classdumpdir", this.classDumpDir)

            if (agent.supportsJmx()) {
                argument.append("jmx", this.isJmx)
            }

            return builder.toString()
        }

    private class ArgumentAppender(private val builder: StringBuilder, private val workingDirectory: File) {
        private var anyArgs = false

        fun append(name: String, value: Any?) {
            if (value != null && !((value is MutableCollection<*>) && value.isEmpty()) && !((value is String) && StringUtils.isEmpty(value)) && !((value is Int) && (value == 0))) {
                if (anyArgs) {
                    builder.append(',')
                }
                builder.append(name).append('=')
                if (value is MutableCollection<*>) {
                    builder.append(Joiner.on(':').join(value))
                } else if (value is File) {
                    builder.append(RelativePathUtil.relativePath(workingDirectory, value))
                } else {
                    builder.append(value)
                }
                anyArgs = true
            }
        }
    }
}
