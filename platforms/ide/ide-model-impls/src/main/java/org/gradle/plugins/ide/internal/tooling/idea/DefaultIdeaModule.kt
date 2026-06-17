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
package org.gradle.plugins.ide.internal.tooling.idea

import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject
import org.gradle.tooling.internal.gradle.GradleProjectIdentity
import java.io.File
import java.io.Serializable
import java.util.LinkedList

/**
 * Structurally implements [org.gradle.tooling.model.idea.IdeaModule] model.
 */
class DefaultIdeaModule : Serializable, GradleProjectIdentity {
    var name: String? = null
        private set
    private var contentRoots: MutableList<DefaultIdeaContentRoot?>? = LinkedList<DefaultIdeaContentRoot?>()
    var project: DefaultIdeaProject? = null
        private set
    set

    private var dependencies: MutableList<DefaultIdeaDependency?> = LinkedList<DefaultIdeaDependency?>()
    private var gradleProject: DefaultGradleProject? = null

    var compilerOutput: DefaultIdeaCompilerOutput? = null
        private set

    var javaLanguageSettings: DefaultIdeaJavaLanguageSettings? = null
        private set
    var jdkName: String? = null
        private set

    fun setName(name: String?): DefaultIdeaModule {
        this.name = name
        return this
    }

    fun getContentRoots(): MutableCollection<DefaultIdeaContentRoot?>? {
        return contentRoots
    }

    fun setContentRoots(contentRoots: MutableList<DefaultIdeaContentRoot?>?): DefaultIdeaModule {
        this.contentRoots = contentRoots
        return this
    }

    fun setParent(parent: DefaultIdeaProject?): DefaultIdeaModule {
        this.project = parent
        return this
    }

    fun getDependencies(): MutableCollection<DefaultIdeaDependency?> {
        return dependencies
    }

    fun setDependencies(dependencies: MutableList<DefaultIdeaDependency?>): DefaultIdeaModule {
        this.dependencies = dependencies
        return this
    }

    val children: MutableCollection<Any?>
        get() = mutableSetOf<Any?>()

    val description: String?
        get() = null

    fun getGradleProject(): DefaultGradleProject {
        return gradleProject!!
    }

    fun setGradleProject(gradleProject: DefaultGradleProject): DefaultIdeaModule {
        this.gradleProject = gradleProject
        return this
    }

    fun setCompilerOutput(compilerOutput: DefaultIdeaCompilerOutput?): DefaultIdeaModule {
        this.compilerOutput = compilerOutput
        return this
    }

    fun setJavaLanguageSettings(javaLanguageSettings: DefaultIdeaJavaLanguageSettings?): DefaultIdeaModule {
        this.javaLanguageSettings = javaLanguageSettings
        return this
    }

    fun setJdkName(jdkName: String?): DefaultIdeaModule {
        this.jdkName = jdkName
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

    override fun toString(): String {
        return ("IdeaModule{"
                + "name='" + name + '\''
                + ", gradleProject='" + gradleProject + '\''
                + ", contentRoots=" + contentRoots
                + ", compilerOutput=" + compilerOutput
                + ", dependencies count=" + dependencies.size
                + '}')
    }
}
