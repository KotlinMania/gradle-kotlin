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
package org.gradle.testkit.runner.internal

import org.gradle.testkit.runner.InvalidPluginMetadataException
import org.gradle.util.internal.GUtil
import java.io.File
import java.net.URL
import java.util.Properties

object PluginUnderTestMetadataReading {
    const val IMPLEMENTATION_CLASSPATH_PROP_KEY: String = "implementation-classpath"
    const val PLUGIN_METADATA_FILE_NAME: String = "plugin-under-test-metadata.properties"

    @JvmOverloads
    fun readImplementationClasspath(classLoader: ClassLoader = Thread.currentThread().getContextClassLoader()): MutableList<File?> {
        val pluginClasspathUrl = classLoader.getResource(PLUGIN_METADATA_FILE_NAME)

        if (pluginClasspathUrl == null) {
            throw InvalidPluginMetadataException(String.format("Test runtime classpath does not contain plugin metadata file '%s'", PLUGIN_METADATA_FILE_NAME))
        }

        return readImplementationClasspath(pluginClasspathUrl)
    }

    fun readImplementationClasspath(pluginClasspathUrl: URL): MutableList<File?> {
        return readImplementationClasspath(pluginClasspathUrl.toString(), GUtil.loadProperties(pluginClasspathUrl))
    }

    fun readImplementationClasspath(description: String?, properties: Properties): MutableList<File?> {
        if (!properties.containsKey(IMPLEMENTATION_CLASSPATH_PROP_KEY)) {
            throw InvalidPluginMetadataException(String.format("Plugin metadata file '%s' does not contain expected property named '%s'", description, IMPLEMENTATION_CLASSPATH_PROP_KEY))
        }

        var value = properties.getProperty(IMPLEMENTATION_CLASSPATH_PROP_KEY)
        if (value != null) {
            value = value.trim { it <= ' ' }
        }

        if (value == null || value.isEmpty()) {
            throw InvalidPluginMetadataException(String.format("Plugin metadata file '%s' has empty value for property named '%s'", description, IMPLEMENTATION_CLASSPATH_PROP_KEY))
        }

        val parsedImplementationClasspath = value.trim { it <= ' ' }.split(File.pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val files: MutableList<File?> = ArrayList<File?>(parsedImplementationClasspath.size)
        for (path in parsedImplementationClasspath) {
            files.add(File(path))
        }
        return files
    }
}
