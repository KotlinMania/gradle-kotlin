/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.internal.tooling.eclipse

import com.google.common.collect.Lists
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject
import org.gradle.tooling.internal.gradle.GradleProjectIdentity
import java.io.File
import java.io.Serializable

/**
 * An implementation for [org.gradle.tooling.model.eclipse.EclipseProject].
 */
class DefaultEclipseProject(val name: String?, val path: String, val description: String?, val projectDirectory: File?, children: Iterable<out DefaultEclipseProject?>) : Serializable,
    GradleProjectIdentity {
    @JvmField
    var parent: DefaultEclipseProject? = null
    var classpath: MutableList<DefaultEclipseExternalDependency?>?
    val children: MutableList<DefaultEclipseProject?>
    private var sourceDirectories: MutableList<DefaultEclipseSourceDirectory?>?
    private var projectDependencies: MutableList<DefaultEclipseProjectDependency?>?
    var tasks: Iterable<out DefaultEclipseTask?>?
    var linkedResources: Iterable<out DefaultEclipseLinkedResource?>? = null
    private var gradleProject: DefaultGradleProject? = null
    var projectNatures: MutableList<DefaultEclipseProjectNature?>?
    var buildCommands: MutableList<DefaultEclipseBuildCommand?>?
    @JvmField
    var javaSourceSettings: DefaultEclipseJavaSourceSettings? = null
    var classpathContainers: MutableList<DefaultEclipseClasspathContainer?>?
    @JvmField
    var outputLocation: DefaultEclipseOutputLocation? = null
    private var hasAutoBuildTasks = false

    init {
        this.tasks = mutableListOf<DefaultEclipseTask?>()
        this.children = Lists.newArrayList<DefaultEclipseProject?>(children)
        this.classpath = mutableListOf<DefaultEclipseExternalDependency?>()
        this.sourceDirectories = mutableListOf<DefaultEclipseSourceDirectory?>()
        this.projectDependencies = mutableListOf<DefaultEclipseProjectDependency?>()
        this.projectNatures = mutableListOf<DefaultEclipseProjectNature?>()
        this.buildCommands = mutableListOf<DefaultEclipseBuildCommand?>()
        this.classpathContainers = mutableListOf<DefaultEclipseClasspathContainer?>()
    }

    override fun toString(): String {
        return "project '" + path + "'"
    }

    fun getSourceDirectories(): Iterable<out DefaultEclipseSourceDirectory?>? {
        return sourceDirectories
    }

    fun setSourceDirectories(sourceDirectories: MutableList<DefaultEclipseSourceDirectory?>?) {
        this.sourceDirectories = sourceDirectories
    }

    fun getProjectDependencies(): Iterable<out DefaultEclipseProjectDependency?>? {
        return projectDependencies
    }

    fun setProjectDependencies(projectDependencies: MutableList<DefaultEclipseProjectDependency?>?) {
        this.projectDependencies = projectDependencies
    }

    fun getGradleProject(): DefaultGradleProject {
        return gradleProject!!
    }

    fun setGradleProject(gradleProject: DefaultGradleProject): DefaultEclipseProject {
        this.gradleProject = gradleProject
        return this
    }

    val projectIdentifier: DefaultProjectIdentifier?
        get() = gradleProject!!.getProjectIdentifier()

    override fun getProjectPath(): String? {
        return this.projectIdentifier.getProjectPath()
    }

    override fun getRootDir(): File? {
        return this.projectIdentifier.getBuildIdentifier().getRootDir()
    }

    fun hasAutoBuildTasks(): Boolean {
        return hasAutoBuildTasks
    }

    fun setAutoBuildTasks(hasAutoBuildTasks: Boolean) {
        this.hasAutoBuildTasks = hasAutoBuildTasks
    }
}
