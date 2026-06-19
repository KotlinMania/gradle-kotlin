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
package org.gradle.api.internal.project

import com.google.common.collect.Sets
import groovy.lang.GroovyObject
import groovy.lang.MissingPropertyException
import groovy.util.ObservableMap
import org.apache.tools.ant.MagicNames
import org.apache.tools.ant.ProjectHelper
import org.apache.tools.ant.PropertyHelper
import org.apache.tools.ant.Target
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.UnknownTaskException
import org.gradle.api.internal.project.ant.AntLoggingAdapter
import org.gradle.api.internal.project.ant.BasicAntBuilder
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.ant.AntTarget
import org.gradle.internal.Transformers
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.File
import java.util.LinkedList

class DefaultAntBuilder(private val gradleProject: Project, private val loggingAdapter: AntLoggingAdapter) : BasicAntBuilder(), GroovyObject {
    fun propertyMissing(property: String?, newValue: Any?) {
        doSetProperty(property, newValue)
    }

    @Suppress("deprecation")
    private fun doSetProperty(property: String?, newValue: Any?) {
        PropertyHelper.getPropertyHelper(getProject()).setUserProperty(null, property, newValue)
    }

    fun propertyMissing(name: String?): Any? {
        if (getProject().getProperties().containsKey(name)) {
            return getProject().getProperties().get(name)
        }

        throw MissingPropertyException(name, javaClass)
    }

    public override val properties: MutableMap<String, Any?>?
        get() {
        val map = ObservableMap(getProject().getProperties())
        map.addPropertyChangeListener(object : PropertyChangeListener {
            override fun propertyChange(event: PropertyChangeEvent) {
                doSetProperty(event.getPropertyName(), event.getNewValue())
            }
        })

        val castMap = map as MutableMap<String, Any?>
        return castMap
        }

    public override val references: MutableMap<String, Any?>?
        get() {
        val map = ObservableMap(getProject().getReferences())
        map.addPropertyChangeListener(object : PropertyChangeListener {
            override fun propertyChange(event: PropertyChangeEvent) {
                getProject().addReference(event.getPropertyName(), event.getNewValue())
            }
        })

        val castMap = map as MutableMap<String, Any?>
        return castMap
        }

    public override fun importBuild(antBuildFile: Any) {
        importBuild(antBuildFile, Transformers.noOpTransformer<String>())
    }

    public override fun importBuild(antBuildFile: Any, baseDirectory: String) {
        importBuild(antBuildFile, baseDirectory, Transformers.noOpTransformer<String>())
    }

    public override fun importBuild(antBuildFile: Any, taskNamer: Transformer<out String, in String>) {
        doImportBuild(antBuildFile, null, taskNamer)
    }

    public override fun importBuild(antBuildFile: Any, baseDirectory: String, taskNamer: Transformer<out String, in String>) {
        doImportBuild(antBuildFile, baseDirectory, taskNamer)
    }

    private fun doImportBuild(antBuildFile: Any, baseDirectory: String?, taskNamer: Transformer<out String, in String>) {
        val file = gradleProject.file(antBuildFile)

        val baseDir = gradleProject.file(baseDirectory ?: file.getParentFile().getAbsolutePath())

        val existingAntTargets: MutableSet<String?> = HashSet<String?>(getAntProject().getTargets().keys)
        val oldBaseDir = getAntProject().getBaseDir()
        getAntProject().setBaseDir(baseDir)
        try {
            getAntProject().setUserProperty(MagicNames.ANT_FILE, file.getAbsolutePath())
            ProjectHelper.configureProject(getAntProject(), file)
        } catch (e: Exception) {
            throw GradleException("Could not import Ant build file '" + file.toString() + "'.", e)
        } finally {
            getAntProject().setBaseDir(oldBaseDir)
        }

        // Chuck away the implicit target. It has already been executed
        getAntProject().getTargets().remove("")

        // Add an adapter for each newly added target
        val newAntTargets: MutableSet<String?> = HashSet<String?>(getAntProject().getTargets().keys)
        newAntTargets.removeAll(existingAntTargets)
        for (name in newAntTargets) {
            val target: Target = getAntProject().getTargets().get(name)!!
            val taskName: String? = taskNamer.transform(target.getName())
            @Suppress("deprecation") val task = gradleProject.getTasks().create<AntTarget>(taskName!!, AntTarget::class.java)
            configureTask(target, task, baseDir, taskNamer)
        }
    }

    public override var lifecycleLogLevel: AntMessagePriority?
        get() = loggingAdapter.lifecycleLogLevel
        set(value) {
            loggingAdapter.setLifecycleLogLevel(value)
    }

    private class AntTargetsTaskDependency(private val taskDependencyNames: MutableList<String>) : TaskDependencyInternal {
        override fun getDependenciesForInternalUse(task: Task?): MutableSet<out Task> {
            val tasks: MutableSet<Task> = Sets.newHashSetWithExpectedSize<Task>(taskDependencyNames.size)
            for (dependedOnTaskName in taskDependencyNames) {
                val dependency = task!!.getProject().getTasks().findByName(dependedOnTaskName)
                if (dependency == null) {
                    throw UnknownTaskException(String.format("Imported Ant target '%s' depends on target or task '%s' which does not exist", task.getName(), dependedOnTaskName))
                }
                tasks.add(dependency)
            }
            return tasks
        }

        override fun getDependencies(task: Task?): MutableSet<out Task> {
            return getDependenciesForInternalUse(task)
        }
    }

    companion object {
        private fun configureTask(target: Target, task: AntTarget, baseDir: File?, taskNamer: Transformer<out String, in String>) {
            task.target = target
            task.baseDir = baseDir

            val taskDependencyNames: MutableList<String> = getTaskDependencyNames(target, taskNamer)
            task.dependsOn(AntTargetsTaskDependency(taskDependencyNames))
            addDependencyOrdering(taskDependencyNames, task.getProject().getTasks())
        }

        private fun getTaskDependencyNames(target: Target, taskNamer: Transformer<out String, in String>): MutableList<String> {
            val dependencies = target.getDependencies()
            val taskDependencyNames: MutableList<String> = LinkedList<String>()
            while (dependencies.hasMoreElements()) {
                val targetName = dependencies.nextElement()
                val taskName: String? = taskNamer.transform(targetName)
                taskDependencyNames.add(taskName!!)
            }
            return taskDependencyNames
        }

        private fun addDependencyOrdering(dependencies: MutableList<String>, tasks: TaskContainer) {
            var previous: String? = null
            for (dependency in dependencies) {
                if (previous != null) {
                    val finalPrevious: String? = previous
                    tasks.all(object : Action<Task> {
                        override fun execute(task: Task) {
                            if (task.getName() == dependency) {
                                task.shouldRunAfter(finalPrevious!!)
                            }
                        }
                    })
                }

                previous = dependency
            }
        }
    }
}
