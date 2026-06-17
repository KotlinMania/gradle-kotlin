/*
 * Copyright 2018 the original author or authors.
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
import java.lang.reflect.Field
import java.util.prefs.AbstractPreferences
import java.util.prefs.PreferenceChangeListener
import java.util.prefs.Preferences

class PreferenceCleaningGroovySystemLoader(private val leakingLoader: ClassLoader) : GroovySystemLoader {
    private val prefListenerField: Field

    init {
        prefListenerField = AbstractPreferences::class.java.getDeclaredField("prefListeners")
        prefListenerField.setAccessible(true)
    }

    override fun shutdown() {
        try {
            val groovyNode = Preferences.userRoot().node("/org/codehaus/groovy/tools/shell")
            val prefListeners = prefListenerField.get(groovyNode) as Array<PreferenceChangeListener>?
            if (prefListeners == null) {
                return
            }
            for (prefListener in prefListeners) {
                val prefListenerLoader = prefListener.javaClass.getClassLoader()
                if (leakingLoader === prefListenerLoader) {
                    groovyNode.removePreferenceChangeListener(prefListener)
                }
            }
        } catch (e: Exception) {
            throw GradleException("Could not shut down the Groovy system for " + leakingLoader, e)
        }
    }

    override fun discardTypesFrom(classLoader: ClassLoader) {
    }
}
