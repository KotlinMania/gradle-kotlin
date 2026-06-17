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
package org.gradle.wrapper

import org.gradle.util.internal.ArgumentsSplitter.split
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Collections
import java.util.Properties

object PropertiesFileHandler {
    private const val SYSTEM_PROP_PREFIX = "systemProp."
    private const val JVMARGS_PROP_KEY = "org.gradle.jvmargs"
    private const val DEBUG_PROP_KEY = "org.gradle.debug"
    private const val DEBUG_PROP_VALUE = "true"

    @JvmStatic
    fun getSystemProperties(propertiesFile: File): MutableMap<String?, String?> {
        if (!propertiesFile.isFile()) {
            return mutableMapOf<String?, String?>()
        }
        val properties = loadProperties(propertiesFile)
        val systemProperties: MutableMap<String?, String?> = HashMap<String?, String?>()
        for (argument in properties.keys) {
            if (argument.toString().startsWith(SYSTEM_PROP_PREFIX)) {
                val key = argument.toString().substring(SYSTEM_PROP_PREFIX.length)
                if (key.length > 0) {
                    systemProperties.put(key, properties.get(argument).toString())
                }
            }
        }
        return Collections.unmodifiableMap<String?, String?>(systemProperties)
    }

    @JvmStatic
    fun getJvmArgs(propertiesFile: File): MutableList<String?> {
        if (!propertiesFile.isFile()) {
            return mutableListOf<String?>()
        }
        val properties = loadProperties(propertiesFile)
        val jvmArgs: MutableList<String?> = ArrayList<String?>()
        for (entry in properties.entries) {
            if (JVMARGS_PROP_KEY == entry.key) {
                val jvmArgsPropValue = entry.value
                if (jvmArgsPropValue is String) {
                    jvmArgs.addAll(split(jvmArgsPropValue))
                }
            } else if (DEBUG_PROP_KEY == entry.key && DEBUG_PROP_VALUE == entry.value) {
                jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
            }
        }
        return Collections.unmodifiableList<String?>(jvmArgs)
    }

    private fun loadProperties(propertiesFile: File): Properties {
        val properties = Properties()
        try {
            val inStream = FileInputStream(propertiesFile)
            try {
                properties.load(inStream)
            } finally {
                inStream.close()
            }
        } catch (e: IOException) {
            throw RuntimeException("Error when loading properties file=" + propertiesFile, e)
        }
        return properties
    }
}
