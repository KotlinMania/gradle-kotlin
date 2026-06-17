/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model

import com.google.common.base.Preconditions
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.internal.IdeDeprecations
import org.gradle.util.internal.ConfigureUtil
import java.io.File
import javax.inject.Inject

/**
 * DSL-friendly model of the Eclipse project information.
 * First point of entry for customizing Eclipse project generation.
 *
 * <pre class='autoTestedWithDeprecations'>
 * plugins {
 * id 'java'
 * id 'eclipse'
 * id 'eclipse-wtp' // for web projects only
 * }
 *
 * eclipse {
 * pathVariables 'GRADLE_HOME': file('/best/software/gradle'), 'TOMCAT_HOME': file('../tomcat')
 *
 * project {
 * //see docs for [EclipseProject]
 * }
 *
 * classpath {
 * //see docs for [EclipseClasspath]
 * }
 *
 * wtp {
 * //see docs for [EclipseWtp]
 * }
 * }
</pre> *
 *
 * More examples in docs for [EclipseProject], [EclipseClasspath], [EclipseWtp]
 */
abstract class EclipseModel {
    var project: EclipseProject? = null
        /**
         * Configures eclipse project information
         *
         *
         * For examples see docs for [EclipseProject]
         */
        get() {
            if (field == null) {
                val xmlTransformer = XmlTransformer()
                xmlTransformer.setIndentation("\t")
                field = this.objectFactory.newInstance<EclipseProject?>(
                    EclipseProject::class.java,
                    XmlFileContentMerger(xmlTransformer)
                )
            }
            return field
        }

    private var classpath: EclipseClasspath? = null

    /**
     * Configures eclipse java compatibility information (jdt)
     *
     *
     * For examples see docs for [EclipseProject]
     */
    var jdt: EclipseJdt? = null

    @Suppress("deprecation")
    private var wtp: EclipseWtp? = null

    private val synchronizationTasks: DefaultTaskDependency

    private val autoBuildTasks: DefaultTaskDependency

    constructor() {
        synchronizationTasks = DefaultTaskDependency()
        autoBuildTasks = DefaultTaskDependency()
    }

    /**
     * Constructor.
     *
     * @since 5.4
     */
    @Inject
    constructor(project: Project) {
        val taskDependencyFactory = (project as ProjectInternal).getTaskDependencyFactory()
        this.synchronizationTasks = taskDependencyFactory.configurableDependency()
        this.autoBuildTasks = taskDependencyFactory.configurableDependency()
    }

    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    /**
     * Configures eclipse classpath information
     *
     *
     * For examples see docs for [EclipseClasspath]
     */
    fun getClasspath(): EclipseClasspath {
        return classpath!!
    }

    fun setClasspath(classpath: EclipseClasspath) {
        this.classpath = classpath
    }

    /**
     * Configures eclipse wtp information
     *
     *
     * For examples see docs for [EclipseWtp]
     *
     */
    @Deprecated("Will be removed in Gradle 10.")
    fun getWtp(): EclipseWtp {
        if (wtp == null) {
            wtp = this.objectFactory.newInstance<EclipseWtp?>(EclipseWtp::class.java)
        }
        return wtp!!
    }

    /**
     * Set [EclipseWtp].
     *
     */
    @Deprecated("Will be removed in Gradle 10.")
    fun setWtp(wtp: EclipseWtp?) {
        IdeDeprecations.nagDeprecatedType(EclipseWtp::class.java)
        this.wtp = wtp
    }

    /**
     * Configures eclipse project information
     *
     *
     * For examples see docs for [EclipseProject]
     */
    fun project(@DelegatesTo(EclipseProject::class) closure: Closure<*>?) {
        ConfigureUtil.configure<EclipseProject?>(closure, this.project)
    }

    /**
     * Configures eclipse project information
     *
     *
     * For examples see docs for [EclipseProject]
     *
     * @since 3.5
     */
    fun project(action: Action<in EclipseProject?>) {
        action.execute(this.project)
    }

    /**
     * Configures eclipse classpath information
     *
     *
     * For examples see docs for [EclipseClasspath]
     */
    fun classpath(@DelegatesTo(EclipseClasspath::class) closure: Closure<*>?) {
        ConfigureUtil.configure<EclipseClasspath?>(closure, classpath)
    }

    /**
     * Configures eclipse classpath information
     *
     *
     * For examples see docs for [EclipseClasspath]
     *
     * @since 3.5
     */
    fun classpath(action: Action<in EclipseClasspath?>) {
        action.execute(classpath)
    }

    /**
     * Configures eclipse wtp information
     *
     *
     * For examples see docs for [EclipseWtp]
     *
     */
    @Deprecated("Will be removed in Gradle 10.")
    fun wtp(@DelegatesTo(EclipseWtp::class) closure: Closure<*>?) {
        IdeDeprecations.nagDeprecatedType(EclipseWtp::class.java)
        ConfigureUtil.configure<EclipseWtp?>(closure, wtp)
    }

    /**
     * Configures eclipse wtp information
     *
     *
     * For examples see docs for [EclipseWtp]
     *
     * @since 3.5
     */
    @Deprecated("Will be removed in Gradle 10.")
    fun wtp(action: Action<in EclipseWtp?>) {
        IdeDeprecations.nagDeprecatedType(EclipseWtp::class.java)
        action.execute(wtp)
    }

    /**
     * Configures eclipse java compatibility information (jdt)
     *
     *
     * For examples see docs for [EclipseProject]
     */
    fun jdt(@DelegatesTo(EclipseJdt::class) closure: Closure<*>?) {
        ConfigureUtil.configure<EclipseJdt?>(closure, this.jdt)
    }

    /**
     * Configures eclipse java compatibility information (jdt)
     *
     *
     * For examples see docs for [EclipseProject]
     *
     * @since 3.5
     */
    fun jdt(action: Action<in EclipseJdt?>) {
        action.execute(this.jdt)
    }

    /**
     * Returns the tasks to be executed before the Eclipse synchronization starts.
     *
     *
     * This property doesn't have a direct effect to the Gradle Eclipse plugin's behaviour. It is used, however, by
     * Buildship to execute the configured tasks each time before the user imports the project or before a project
     * synchronization starts.
     *
     * @return the tasks names
     * @since 5.4
     */
    fun getSynchronizationTasks(): TaskDependency {
        return synchronizationTasks
    }

    /**
     * Set tasks to be executed before the Eclipse synchronization.
     *
     * @see .getSynchronizationTasks
     * @since 5.4
     */
    fun synchronizationTasks(vararg synchronizationTasks: Any?) {
        this.synchronizationTasks.add(*synchronizationTasks)
    }

    /**
     * Returns the tasks to be executed during the Eclipse auto-build.
     *
     *
     * This property doesn't have a direct effect to the Gradle Eclipse plugin's behaviour. It is used, however, by
     * Buildship to execute the configured tasks each time when the Eclipse automatic build is triggered for the project.
     *
     * @return the tasks names
     * @since 5.4
     */
    fun getAutoBuildTasks(): TaskDependency {
        return autoBuildTasks
    }

    /**
     * Set tasks to be executed during the Eclipse auto-build.
     *
     * @see .getAutoBuildTasks
     * @since 5.4
     */
    fun autoBuildTasks(vararg autoBuildTasks: Any?) {
        this.autoBuildTasks.add(*autoBuildTasks)
    }

    /**
     * Adds path variables to be used for replacing absolute paths in classpath entries.
     *
     *
     * If the beginning of the absolute path of a library or other path-related element matches a value of a variable,
     * a variable entry is used. The matching part of the library path is replaced with the variable name.
     *
     *
     * For example see docs for [EclipseModel]
     *
     * @param pathVariables A map with String-&gt;File pairs.
     */
    fun pathVariables(pathVariables: MutableMap<String?, File?>?) {
        Preconditions.checkNotNull<MutableMap<String?, File?>?>(pathVariables)
        classpath!!.getPathVariables().putAll(pathVariables!!)
        if (wtp != null && wtp!!.getComponent() != null) {
            wtp!!.getComponent().getPathVariables().putAll(pathVariables)
        }
    }
}
