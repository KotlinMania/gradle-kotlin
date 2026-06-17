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
package org.gradle.api.tasks

import com.google.common.collect.Iterables
import org.gradle.api.Buildable
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.DependencyClassPathProvider
import org.gradle.api.internal.file.collections.FailingFileCollection
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection
import org.gradle.api.internal.plugins.GroovyJarFile
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.plugins.jvm.internal.JvmEcosystemUtilities.configureAsRuntimeClasspath
import org.gradle.api.plugins.jvm.internal.JvmPluginServices
import org.gradle.util.internal.GroovyDependencyUtil
import org.gradle.util.internal.VersionNumber
import java.io.File
import java.util.stream.Collectors
import java.util.stream.StreamSupport

/**
 * Provides information related to the Groovy runtime(s) used in a project. Added by the
 * [org.gradle.api.plugins.GroovyBasePlugin] as a project extension named `groovyRuntime`.
 *
 *
 * Example usage:
 *
 * <pre class='autoTested'>
 * plugins {
 * id 'groovy'
 * }
 *
 * repositories {
 * mavenCentral()
 * }
 *
 * dependencies {
 * implementation "org.codehaus.groovy:groovy-all:2.1.2"
 * }
 *
 * def groovyClasspath = groovyRuntime.inferGroovyClasspath(configurations.compileClasspath)
 * // The returned class path can be used to configure the 'groovyClasspath' property of tasks
 * // such as 'GroovyCompile' or 'Groovydoc', or to execute these and other Groovy tools directly.
</pre> *
 */
abstract class GroovyRuntime(project: Project?) {
    private val project: ProjectInternal

    init {
        this.project = project as ProjectInternal
    }

    /**
     * Searches the specified class path for Groovy Jars (`groovy(-indy)`, `groovy-all(-indy)`) and returns a corresponding class path for executing Groovy tools such as the Groovy
     * compiler and Groovydoc tool. The tool versions will match those of the Groovy Jars found. If no Groovy Jars are found on the specified class path, a class path with the contents of the `groovy` configuration will be returned.
     *
     *
     * The returned class path may be empty, or may fail to resolve when asked for its contents.
     *
     * @param classpath a class path containing Groovy Jars
     * @return a corresponding class path for executing Groovy tools such as the Groovy compiler and Groovydoc tool
     */
    fun inferGroovyClasspath(classpath: Iterable<File>): FileCollection {
        // alternatively, we could return project.getLayout().files(Runnable)
        // would differ in at least the following ways: 1. live 2. no autowiring
        return object : LazilyInitializedFileCollection(project.getTaskDependencyFactory()) {
            override fun getDisplayName(): String {
                return "Groovy runtime classpath"
            }

            override fun createDelegate(): FileCollection {
                try {
                    return inferGroovyClasspath()
                } catch (e: RuntimeException) {
                    return FailingFileCollection(getDisplayName(), e)
                }
            }

            fun inferGroovyClasspath(): FileCollection {
                val groovyJar: GroovyJarFile? = findGroovyJarFile(classpath)
                if (groovyJar == null) {
                    throw GradleException(
                        String.format(
                            "Cannot infer Groovy class path because no Groovy Jar was found on class path: %s",
                            Iterables.toString(classpath)
                        )
                    )
                }

                if (groovyJar.isGroovyAll()) {
                    return project.getLayout().files(groovyJar.getFile())
                }

                val groovyVersion = groovyJar.getVersion()

                if (groovyVersion.getMajor() <= 2) {
                    return inferGroovyAllClasspath(groovyJar.getDependencyNotation(), groovyVersion)
                } else {
                    return inferGroovyClasspath(groovyVersion)
                }
            }

            fun addGroovyDependency(groovyDependencyNotion: String, dependencies: MutableList<Dependency?>, otherDependency: String?) {
                val notation = groovyDependencyNotion.replace(":groovy:", ":" + otherDependency + ":")
                addDependencyTo(dependencies, notation)
            }

            fun addDependencyTo(dependencies: MutableList<Dependency?>, notation: String) {
                // project.getDependencies().create(String) seems to be the only feasible way to create a Dependency with a classifier
                dependencies.add(project.getDependencies().create(notation))
            }

            fun inferGroovyAllClasspath(notation: String, groovyVersion: VersionNumber): FileCollection {
                val dependencies: MutableList<Dependency?> = ArrayList<Dependency?>()
                addDependencyTo(dependencies, notation)

                if (groovyVersion.compareTo(GROOVY_VERSION_WITH_SEPARATE_ANT) >= 0) {
                    // add groovy-ant to bring in Groovydoc for Groovy 2.0+
                    addGroovyDependency(notation, dependencies, "groovy-ant")
                }
                if (groovyVersion.compareTo(GROOVY_VERSION_REQUIRING_TEMPLATES) >= 0) {
                    // add groovy-templates for Groovy 2.5+
                    addGroovyDependency(notation, dependencies, "groovy-templates")
                }

                return detachedRuntimeClasspath(*dependencies.toTypedArray<Dependency?>())
            }

            fun inferGroovyClasspath(groovyVersion: VersionNumber?): FileCollection {
                // We may already have the required pieces on classpath via localGroovy()
                val groovyJarNames: MutableSet<String?> = groovyJarNamesFor(groovyVersion)
                val groovyClasspath: MutableList<File?> = collectJarsFromClasspath(classpath, groovyJarNames)
                if (groovyClasspath.size == DependencyClassPathProvider.GROOVY_MODULES.size) {
                    return project.getLayout().files(groovyClasspath)
                }

                return detachedRuntimeClasspath(
                    *DependencyClassPathProvider.GROOVY_MODULES.stream()
                        .map<Dependency?> { libName: String? -> project.getDependencies().create(GroovyDependencyUtil.groovyModuleDependency(libName, groovyVersion)) }
                        .toArray<Dependency?> { _Dummy_.__Array__() }
                )
            }

            fun detachedRuntimeClasspath(vararg dependencies: Dependency?): Configuration {
                val classpath = project.getConfigurations().detachedConfiguration(*dependencies)
                this.jvmPluginServices.configureAsRuntimeClasspath(classpath)
                return classpath
            }

            // let's override this so that delegate isn't created at autowiring time (which would mean on every build)
            override fun visitDependencies(context: TaskDependencyResolveContext) {
                if (classpath is Buildable) {
                    context.add(classpath)
                }
            }
        }
    }

    private val jvmPluginServices: JvmPluginServices?
        get() = project.getServices().get<JvmPluginServices?>(JvmPluginServices::class.java)

    companion object {
        private val GROOVY_VERSION_WITH_SEPARATE_ANT: VersionNumber = VersionNumber.parse("2.0")
        private val GROOVY_VERSION_REQUIRING_TEMPLATES: VersionNumber = VersionNumber.parse("2.5")

        private fun collectJarsFromClasspath(classpath: Iterable<File>, jarNames: MutableSet<String?>): MutableList<File?> {
            return StreamSupport.stream<File?>(classpath.spliterator(), false)
                .filter { file: File? -> jarNames.contains(file!!.getName()) }
                .collect(Collectors.toList())
        }

        private fun groovyJarNamesFor(groovyVersion: VersionNumber?): MutableSet<String?> {
            return DependencyClassPathProvider.GROOVY_MODULES.stream()
                .map<String?> { libName: String? -> libName + "-" + groovyVersion + ".jar" }
                .collect(Collectors.toSet())
        }

        private fun findGroovyJarFile(classpath: Iterable<File>): GroovyJarFile? {
            for (file in classpath) {
                val groovyJar: GroovyJarFile? = GroovyJarFile.Companion.parse(file)
                if (groovyJar != null) {
                    return groovyJar
                }
            }
            return null
        }
    }
}
