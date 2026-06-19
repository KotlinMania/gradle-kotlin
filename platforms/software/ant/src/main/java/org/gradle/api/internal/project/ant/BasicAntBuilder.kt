/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.project.ant

import groovy.ant.AntBuilder
import org.apache.tools.ant.ComponentHelper
import org.apache.tools.ant.Project
import org.apache.tools.ant.Target
import org.gradle.api.Transformer
import org.gradle.api.internal.file.ant.AntFileResource
import org.gradle.api.internal.file.ant.BaseDirSelector
import java.io.Closeable
import java.lang.reflect.Field

@Suppress("deprecation")
open class BasicAntBuilder : org.gradle.api.AntBuilder(), Closeable {
    private val nodeField: Field
    private val children: MutableList<*>

    init {
        // These are used to discard references to tasks so they can be garbage collected
        val collectorField: Field?
        try {
            nodeField = AntBuilder::class.java.getDeclaredField("lastCompletedNode")
            nodeField.setAccessible(true)
            collectorField = AntBuilder::class.java.getDeclaredField("collectorTarget")
            collectorField.setAccessible(true)
            val target = collectorField.get(this) as Target?
            val childrenField = Target::class.java.getDeclaredField("children")
            childrenField.setAccessible(true)
            children = childrenField.get(target) as MutableList<*>
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

        getAntProject().addDataTypeDefinition("gradleFileResource", AntFileResource::class.java)
        getAntProject().addDataTypeDefinition("gradleBaseDirSelector", BaseDirSelector::class.java)
    }

    override val properties: MutableMap<String, Any?>?
        get() = throw UnsupportedOperationException()

    override val references: MutableMap<String, Any?>?
        get() = throw UnsupportedOperationException()

    override fun importBuild(antBuildFile: Any) {
        throw UnsupportedOperationException()
    }

    override fun importBuild(antBuildFile: Any, baseDirectory: String) {
        throw UnsupportedOperationException()
    }

    override fun importBuild(antBuildFile: Any, taskNamer: Transformer<out String, in String>) {
        throw UnsupportedOperationException()
    }

    override fun importBuild(antBuildFile: Any, baseDirectory: String, taskNamer: Transformer<out String, in String>) {
        throw UnsupportedOperationException()
    }

    override fun nodeCompleted(parent: Any?, node: Any?) {
        val original = Thread.currentThread().getContextClassLoader()
        Thread.currentThread().setContextClassLoader(Project::class.java.getClassLoader())
        try {
            super.nodeCompleted(parent, node)
        } finally {
            Thread.currentThread().setContextClassLoader(original)
        }
    }

    override var lifecycleLogLevel: AntMessagePriority?
        get() = throw UnsupportedOperationException()
        set(value) {
            throw UnsupportedOperationException()
        }

    override fun postNodeCompletion(parent: Any?, node: Any?): Any? {
        try {
            return nodeField.get(this)
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
    }

    override fun doInvokeMethod(methodName: String?, name: Any?, args: Any?): Any? {
        val value = super.doInvokeMethod(methodName, name, args)
        // Discard the node so it can be garbage collected. Some Ant tasks cache a potentially large amount of state
        // in fields.
        try {
            nodeField.set(this, null)
            children.clear()
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
        return value
    }

    override fun close() {
        val project = getProject()
        project.fireBuildFinished(null)
        val helper = ComponentHelper.getComponentHelper(project)
        helper.getAntTypeTable().clear()
        helper.getDataTypeDefinitions().clear()
        project.getReferences().clear()
    }
}
