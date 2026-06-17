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
package org.gradle.plugins.ide.internal.tooling.model

import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier
import org.gradle.tooling.internal.gradle.GradleBuildIdentity
import org.gradle.tooling.model.BuildIdentifier
import java.io.File
import java.io.Serializable

/**
 * Structurally implements [org.gradle.tooling.model.gradle.GradleBuild] model.
 */
class DefaultGradleBuild : Serializable, GradleBuildIdentity {
    var rootProject: PartialBasicGradleProject? = null
        private set
    private var buildIdentifier: BuildIdentifier? = null
    private val projects: MutableSet<PartialBasicGradleProject?> = LinkedHashSet<PartialBasicGradleProject?>()
    val includedBuilds: MutableSet<DefaultGradleBuild?> = LinkedHashSet<DefaultGradleBuild?>()
    val editableBuilds: MutableSet<DefaultGradleBuild?> = LinkedHashSet<DefaultGradleBuild?>()

    override fun toString(): String {
        return buildIdentifier.toString()
    }

    fun setRootProject(rootProject: PartialBasicGradleProject): DefaultGradleBuild {
        this.rootProject = rootProject
        this.buildIdentifier = DefaultBuildIdentifier(rootProject.getRootDir())
        return this
    }

    fun getProjects(): MutableSet<out PartialBasicGradleProject?> {
        return projects
    }

    fun addProject(project: PartialBasicGradleProject?) {
        projects.add(project)
    }

    fun addBuilds(builds: MutableCollection<DefaultGradleBuild?>) {
        editableBuilds.addAll(builds)
    }

    fun addIncludedBuild(includedBuild: DefaultGradleBuild?) {
        includedBuilds.add(includedBuild)
    }

    fun getBuildIdentifier(): BuildIdentifier {
        return buildIdentifier!!
    }

    fun setBuildIdentifier(buildIdentifier: BuildIdentifier) {
        this.buildIdentifier = buildIdentifier
    }

    override fun getRootDir(): File? {
        return buildIdentifier!!.rootDir
    }
}
