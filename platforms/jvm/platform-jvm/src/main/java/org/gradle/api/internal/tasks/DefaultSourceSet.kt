/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.api.internal.tasks

import groovy.lang.Closure
import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.jvm.ClassDirectoryBinaryNamingScheme
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.util.internal.ConfigureUtil
import org.gradle.util.internal.GUtil
import javax.inject.Inject

abstract class DefaultSourceSet @Inject constructor(name: String, objectFactory: ObjectFactory) : SourceSet {
    private val name: String?
    private val taskBaseName: String
    private var compileClasspath: FileCollection? = null
    private var annotationProcessorPath: FileCollection? = null
    private var runtimeClasspath: FileCollection? = null
    private val javaSource: SourceDirectorySet
    private val allJavaSource: SourceDirectorySet
    private val resources: SourceDirectorySet
    @JvmField
    val displayName: String
    private val allSource: SourceDirectorySet
    private val namingScheme: ClassDirectoryBinaryNamingScheme
    private var output: DefaultSourceSetOutput? = null

    init {
        this.name = name
        this.taskBaseName = (if (name == org.gradle.api.tasks.SourceSet.Companion.MAIN_SOURCE_SET_NAME) "" else org.gradle.util.internal.TextUtil.toCamelCase(name))!!
        displayName = GUtil.toWords(this.name)!!
        namingScheme = ClassDirectoryBinaryNamingScheme(name)

        val javaSrcDisplayName = displayName + " Java source"

        javaSource = objectFactory.sourceDirectorySet("java", javaSrcDisplayName)
        javaSource.getFilter().include("**/*.java")

        allJavaSource = objectFactory.sourceDirectorySet("alljava", javaSrcDisplayName)
        allJavaSource.getFilter().include("**/*.java")
        allJavaSource.source(javaSource)

        val resourcesDisplayName = displayName + " resources"
        resources = objectFactory.sourceDirectorySet("resources", resourcesDisplayName)

        // Explicitly capture only a FileCollection in the lambda below for compatibility with configuration-cache.
        val javaSourceFiles: FileCollection = javaSource
        resources.getFilter().exclude(
            SerializableLambdas.spec<FileTreeElement?>(SerializableLambdas.SerializableSpec { element: FileTreeElement? -> javaSourceFiles.contains(element!!.getFile()) })
        )

        val allSourceDisplayName = displayName + " source"
        allSource = objectFactory.sourceDirectorySet("allsource", allSourceDisplayName)
        allSource.source(resources)
        allSource.source(javaSource)
    }

    override fun getName(): String? {
        return name
    }

    override fun toString(): String {
        return "source set '" + this.displayName + "'"
    }

    override fun getClassesTaskName(): String? {
        return getTaskName(null, "classes")
    }

    override fun getCompileTaskName(language: String?): String? {
        return getTaskName("compile", language)
    }

    override fun getCompileJavaTaskName(): String? {
        return getCompileTaskName("java")
    }

    override fun getProcessResourcesTaskName(): String? {
        return getTaskName("process", "resources")
    }

    override fun getJavadocTaskName(): String? {
        return getTaskName(null, JvmConstants.JAVADOC_TASK_NAME)
    }

    override fun getJarTaskName(): String? {
        return getTaskName(null, "jar")
    }

    override fun getJavadocJarTaskName(): String? {
        return getTaskName(null, "javadocJar")
    }

    override fun getSourcesJarTaskName(): String? {
        return getTaskName(null, "sourcesJar")
    }

    override fun getTaskName(verb: String?, target: String?): String? {
        return namingScheme.getTaskName(verb, target)
    }

    /**
     * Determines the name of a configuration owned by this source set, with the given `baseName`.
     *
     *
     * If this is the main source set, returns the uncapitalized `baseName`, otherwise, returns the
     * base name prefixed with this source set's name.
     */
    fun configurationNameOf(baseName: String?): String {
        return StringUtils.uncapitalize(this.taskBaseName + StringUtils.capitalize(baseName))
    }

    override fun getCompileOnlyConfigurationName(): String {
        return configurationNameOf(JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME)
    }

    override fun getCompileOnlyApiConfigurationName(): String {
        return configurationNameOf(JvmConstants.COMPILE_ONLY_API_CONFIGURATION_NAME)
    }

    override fun getCompileClasspathConfigurationName(): String {
        return configurationNameOf(JvmConstants.COMPILE_CLASSPATH_CONFIGURATION_NAME)
    }

    override fun getAnnotationProcessorConfigurationName(): String {
        return configurationNameOf(JvmConstants.ANNOTATION_PROCESSOR_CONFIGURATION_NAME)
    }

    override fun getApiConfigurationName(): String {
        return configurationNameOf(JvmConstants.API_CONFIGURATION_NAME)
    }

    override fun getImplementationConfigurationName(): String {
        return configurationNameOf(JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME)
    }

    override fun getApiElementsConfigurationName(): String {
        return configurationNameOf(JvmConstants.API_ELEMENTS_CONFIGURATION_NAME)
    }

    override fun getRuntimeOnlyConfigurationName(): String {
        return configurationNameOf(JvmConstants.RUNTIME_ONLY_CONFIGURATION_NAME)
    }

    override fun getRuntimeClasspathConfigurationName(): String {
        return configurationNameOf(JvmConstants.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
    }

    override fun getRuntimeElementsConfigurationName(): String {
        return configurationNameOf(JvmConstants.RUNTIME_ELEMENTS_CONFIGURATION_NAME)
    }

    override fun getJavadocElementsConfigurationName(): String {
        return configurationNameOf(JvmConstants.JAVADOC_ELEMENTS_CONFIGURATION_NAME)
    }

    override fun getSourcesElementsConfigurationName(): String {
        return configurationNameOf(JvmConstants.SOURCES_ELEMENTS_CONFIGURATION_NAME)
    }

    override fun getOutput(): SourceSetOutput {
        return output!!
    }

    fun setClasses(classes: DefaultSourceSetOutput) {
        this.output = classes
    }

    override fun compiledBy(vararg taskPaths: Any?): SourceSet {
        output!!.builtBy(*taskPaths)
        return this
    }

    override fun getCompileClasspath(): FileCollection? {
        return compileClasspath
    }

    override fun getAnnotationProcessorPath(): FileCollection? {
        return annotationProcessorPath
    }

    override fun getRuntimeClasspath(): FileCollection? {
        return runtimeClasspath
    }

    override fun setCompileClasspath(classpath: FileCollection?) {
        compileClasspath = classpath
    }

    override fun setAnnotationProcessorPath(annotationProcessorPath: FileCollection?) {
        this.annotationProcessorPath = annotationProcessorPath
    }

    override fun setRuntimeClasspath(classpath: FileCollection?) {
        runtimeClasspath = classpath
    }

    override fun getJava(): SourceDirectorySet {
        return javaSource
    }

    override fun java(configureClosure: Closure<*>?): SourceSet {
        ConfigureUtil.configure<SourceDirectorySet?>(configureClosure, getJava())
        return this
    }

    override fun java(configureAction: Action<in SourceDirectorySet?>): SourceSet {
        configureAction.execute(getJava())
        return this
    }

    override fun getAllJava(): SourceDirectorySet {
        return allJavaSource
    }

    override fun getResources(): SourceDirectorySet {
        return resources
    }

    override fun resources(configureClosure: Closure<*>?): SourceSet {
        ConfigureUtil.configure<SourceDirectorySet?>(configureClosure, getResources())
        return this
    }

    override fun resources(configureAction: Action<in SourceDirectorySet?>): SourceSet {
        configureAction.execute(getResources())
        return this
    }

    override fun getAllSource(): SourceDirectorySet {
        return allSource
    }
}
