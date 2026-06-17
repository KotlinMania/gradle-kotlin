/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.plugin.devel.tasks

import com.google.common.base.Joiner
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.util.PropertiesUtils
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.IOException
import java.util.Properties

/**
 * Custom task for generating the metadata for a plugin user test.
 *
 * @since 2.13
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class PluginUnderTestMetadata : DefaultTask() {
    @get:Classpath
    abstract val pluginClasspath: ConfigurableFileCollection?

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty?

    @TaskAction
    fun generate() {
        val properties = Properties()

        if (!this.pluginClasspath.isEmpty()) {
            properties.setProperty(IMPLEMENTATION_CLASSPATH_PROP_KEY, implementationClasspath())
        }

        val outputFile = File(this.outputDirectory.get().getAsFile(), METADATA_FILE_NAME)
        saveProperties(properties, outputFile)
    }

    private fun implementationClasspath(): String {
        val implementationClasspath = StringBuilder()
        Joiner.on(File.pathSeparator).appendTo(implementationClasspath, this.paths)
        return implementationClasspath.toString()
    }

    private fun saveProperties(properties: Properties, outputFile: File) {
        try {
            PropertiesUtils.store(properties, outputFile)
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    @get:Input
    protected val paths: MutableList<String>
        get() = collect(this.pluginClasspath, { file -> file.getAbsolutePath().replaceAll("\\\\", "/") })

    companion object {
        const val IMPLEMENTATION_CLASSPATH_PROP_KEY: String = "implementation-classpath"
        const val METADATA_FILE_NAME: String = "plugin-under-test-metadata.properties"
    }
}
