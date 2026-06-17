/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.internal.groovyloader

import org.gradle.api.GradleException
import org.gradle.util.internal.VersionNumber
import java.lang.reflect.InvocationTargetException

class GroovySystemLoaderFactory {
    fun forClassLoader(classLoader: ClassLoader): GroovySystemLoader {
        try {
            val groovySystem = getGroovySystem(classLoader)
            if (groovySystem == null || groovySystem.getClassLoader() !== classLoader) {
                return NO_OP
            }
            val classInfoCleaningLoader = createClassInfoCleaningLoader(groovySystem, classLoader)
            val preferenceCleaningLoader: GroovySystemLoader = PreferenceCleaningGroovySystemLoader(classLoader)
            return CompositeGroovySystemLoader(classInfoCleaningLoader, preferenceCleaningLoader)
        } catch (e: Exception) {
            throw GradleException("Could not inspect the Groovy system for ClassLoader " + classLoader, e)
        }
    }

    private fun getGroovySystem(classLoader: ClassLoader): Class<*>? {
        try {
            return classLoader.loadClass("groovy.lang.GroovySystem")
        } catch (e: ClassNotFoundException) {
            return null
        }
    }

    @Throws(Exception::class)
    private fun createClassInfoCleaningLoader(groovySystem: Class<*>, classLoader: ClassLoader): GroovySystemLoader {
        val groovyVersion = getGroovyVersion(groovySystem)
        return if (isGroovy24OrLater(groovyVersion)) ClassInfoCleaningGroovySystemLoader(classLoader) else NO_OP
    }

    @Throws(IllegalAccessException::class, InvocationTargetException::class)
    private fun getGroovyVersion(groovySystem: Class<*>): VersionNumber? {
        try {
            val getVersion = groovySystem.getDeclaredMethod("getVersion")
            val versionString = getVersion.invoke(null) as String?
            return VersionNumber.parse(versionString)
        } catch (ex: NoSuchMethodException) {
            return null
        }
    }

    private fun isGroovy24OrLater(groovyVersion: VersionNumber?): Boolean {
        if (groovyVersion == null) {
            return false
        }
        return (groovyVersion.getMajor() == 2 && groovyVersion.getMinor() >= 4) || groovyVersion.getMajor() > 2
    }

    companion object {
        private val NO_OP = NoOpGroovySystemLoader()
    }
}
