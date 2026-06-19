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

import java.io.Closeable
import java.io.File
import java.io.FilenameFilter
import java.net.URL
import java.net.URLClassLoader

class BootstrapMainStarter {
    @Throws(Exception::class)
    fun start(args: Array<String>, gradleHome: File?) {
        val gradleJar: File? = findLauncherJar(gradleHome)
        if (gradleJar == null) {
            throw RuntimeException(String.format("Could not locate the Gradle launcher JAR in Gradle distribution '%s'.", gradleHome))
        }
        // The URLClassloader will also include the jars listed in the launcher jar's
        // Class-Path manifest attributes as candidates for loading classes.
        val contextClassLoader = URLClassLoader(arrayOf<URL>(gradleJar.toURI().toURL()), ClassLoader.getSystemClassLoader().getParent())
        Thread.currentThread().setContextClassLoader(contextClassLoader)
        val mainClass = contextClassLoader.loadClass("org.gradle.launcher.GradleMain")
        val mainMethod = mainClass.getMethod("main", Array<String>::class.java)
        mainMethod.invoke(null, *arrayOf<Any?>(args))
        (contextClassLoader as Closeable).close()
    }

    companion object {
        fun findLauncherJar(gradleHome: File?): File? {
            val libDirectory = File(gradleHome, "lib")
            if (libDirectory.exists() && libDirectory.isDirectory()) {
                val launcherJars = libDirectory.listFiles(object : FilenameFilter {
                    override fun accept(dir: File?, name: String): Boolean {
                        return name.matches("gradle-launcher-.*\\.jar".toRegex())
                    }
                })
                if (launcherJars != null && launcherJars.size == 1) {
                    return launcherJars[0]
                }
            }
            return null
        }
    }
}
