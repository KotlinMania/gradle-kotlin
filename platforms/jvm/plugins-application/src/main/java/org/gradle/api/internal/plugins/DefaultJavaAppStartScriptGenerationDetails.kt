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
package org.gradle.api.internal.plugins

import org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails

class DefaultJavaAppStartScriptGenerationDetails(
    private val applicationName: String?,
    private val gitRef: String?,
    private val optsEnvironmentVar: String?,
    private val exitEnvironmentVar: String?,
    val entryPoint: AppEntryPoint?,
    private val defaultJvmOpts: MutableList<String?>?,
    private val classpath: MutableList<String?>?,
    private val modulePath: MutableList<String?>?,
    private val scriptRelPath: String?,
    private val appNameSystemProperty: String?
) : JavaAppStartScriptGenerationDetails {
    override fun getApplicationName(): String {
        return applicationName!!
    }

    override fun getGitRef(): String {
        return gitRef!!
    }

    override fun getOptsEnvironmentVar(): String {
        return optsEnvironmentVar!!
    }

    override fun getExitEnvironmentVar(): String {
        return exitEnvironmentVar!!
    }

    override fun getMainClassName(): String {
        check(entryPoint is MainClass) { "Entry point is not a main class: " + entryPoint }
        return entryPoint.getMainClassName()
    }

    override fun getDefaultJvmOpts(): MutableList<String?> {
        return defaultJvmOpts!!
    }

    override fun getClasspath(): MutableList<String?> {
        return classpath!!
    }

    override fun getModulePath(): MutableList<String?> {
        return modulePath!!
    }

    override fun getScriptRelPath(): String {
        return scriptRelPath!!
    }

    override fun getAppNameSystemProperty(): String? {
        return appNameSystemProperty
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultJavaAppStartScriptGenerationDetails

        if (appNameSystemProperty != that.appNameSystemProperty) {
            return false
        }
        if (applicationName != that.applicationName) {
            return false
        }
        if (gitRef != that.gitRef) {
            return false
        }
        if (classpath != that.classpath) {
            return false
        }
        if (modulePath != that.modulePath) {
            return false
        }
        if (defaultJvmOpts != that.defaultJvmOpts) {
            return false
        }
        if (exitEnvironmentVar != that.exitEnvironmentVar) {
            return false
        }
        if (entryPoint != that.entryPoint) {
            return false
        }
        if (optsEnvironmentVar != that.optsEnvironmentVar) {
            return false
        }
        if (scriptRelPath != that.scriptRelPath) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = if (applicationName != null) applicationName.hashCode() else 0
        result = 31 * result + (if (gitRef != null) gitRef.hashCode() else 0)
        result = 31 * result + (if (optsEnvironmentVar != null) optsEnvironmentVar.hashCode() else 0)
        result = 31 * result + (if (exitEnvironmentVar != null) exitEnvironmentVar.hashCode() else 0)
        result = 31 * result + (if (entryPoint != null) entryPoint.hashCode() else 0)
        result = 31 * result + (if (defaultJvmOpts != null) defaultJvmOpts.hashCode() else 0)
        result = 31 * result + (if (classpath != null) classpath.hashCode() else 0)
        result = 31 * result + (if (modulePath != null) modulePath.hashCode() else 0)
        result = 31 * result + (if (scriptRelPath != null) scriptRelPath.hashCode() else 0)
        result = 31 * result + (if (appNameSystemProperty != null) appNameSystemProperty.hashCode() else 0)
        return result
    }
}
