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

import com.google.common.base.Joiner
import com.google.common.base.Splitter
import org.gradle.api.Action
import org.gradle.api.Buildable
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.file.collections.FailingFileCollection
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.plugins.jvm.internal.JvmPluginServices
import org.gradle.api.plugins.scala.ScalaPluginExtension
import org.gradle.api.tasks.scala.internal.ScalaRuntimeHelper
import java.io.File

/**
 * Provides information related to the Scala runtime(s) used in a project. Added by the
 * `org.gradle.api.plugins.scala.ScalaBasePlugin` as a project extension named `scalaRuntime`.
 *
 *
 * Example usage:
 *
 * <pre class='autoTested'>
 * plugins {
 * id 'scala'
 * }
 *
 * repositories {
 * mavenCentral()
 * }
 *
 * dependencies {
 * implementation "org.scala-lang:scala-library:2.10.1"
 * }
 *
 * def scalaClasspath = scalaRuntime.inferScalaClasspath(configurations.compileClasspath)
 * // The returned class path can be used to configure the 'scalaClasspath' property of tasks
 * // such as 'ScalaCompile' or 'ScalaDoc', or to execute these and other Scala tools directly.
</pre> *
 */
abstract class ScalaRuntime(// TODO: Deprecate this class in 9.x when we de-incubate ScalaPluginExtension#getScalaVersion()
    private val project: Project
) {
    private val jvmPluginServices: JvmPluginServices?

    init {
        this.jvmPluginServices = (project as ProjectInternal).getServices().get<JvmPluginServices?>(JvmPluginServices::class.java)
    }

    /**
     * Searches the specified class path for a 'scala-library' Jar, and returns a class path
     * containing a corresponding (same version) 'scala-compiler' Jar and its dependencies.
     *
     *
     * The returned class path may be empty, or may fail to resolve when asked for its contents.
     *
     * @param classpath a class path containing a 'scala-library' Jar
     * @return a class path containing a corresponding 'scala-compiler' Jar and its dependencies
     */
    fun inferScalaClasspath(classpath: Iterable<File?>): FileCollection {
        // alternatively, we could return project.getLayout().files(Runnable)
        // would differ in the following ways: 1. live (not sure if we want live here) 2. no autowiring (probably want autowiring here)
        return object : LazilyInitializedFileCollection((project as ProjectInternal).getTaskDependencyFactory()) {
            override fun getDisplayName(): String {
                return "Scala runtime classpath"
            }

            override fun createDelegate(): FileCollection {
                try {
                    return inferScalaClasspath()
                } catch (e: RuntimeException) {
                    return FailingFileCollection(getDisplayName(), e)
                }
            }

            fun inferScalaClasspath(): Configuration {
                val scalaLibraryJar = findScalaJar(classpath, "library")
                val scala3LibraryJar = findScalaJar(classpath, "library_3")
                val isScala3 = scala3LibraryJar != null
                if (scalaLibraryJar == null && scala3LibraryJar == null) {
                    throw GradleException(
                        String.format(
                            "Cannot infer Scala class path because no Scala library Jar was found. "
                                    + "Does %s declare dependency to scala-library? Searched classpath: %s.", project, classpath
                        )
                    )
                }

                val scalaVersion: String?
                if (isScala3) {
                    scalaVersion = getScalaVersion(scala3LibraryJar)
                } else {
                    scalaVersion = getScalaVersion(scalaLibraryJar!!)
                }

                if (scalaVersion == null) {
                    throw AssertionError(String.format("Unexpectedly failed to parse version of Scala Jar file: %s in %s", scalaLibraryJar, project))
                }

                val zincVersion: String = project.getExtensions().getByType<ScalaPluginExtension?>(ScalaPluginExtension::class.java).getZincVersion().get()

                val compilerBridgeJar = getScalaBridgeDependency(scalaVersion, zincVersion)
                compilerBridgeJar.setTransitive(false)
                compilerBridgeJar.artifact(Action { artifact: DependencyArtifact? ->
                    if (!isScala3) {
                        artifact!!.setClassifier("sources")
                    }
                    artifact!!.setType("jar")
                    artifact.setExtension("jar")
                    artifact.setName(compilerBridgeJar.getName())
                })
                val compilerInterfaceJar = getScalaCompilerInterfaceDependency(scalaVersion, zincVersion)

                val scalaRuntimeClasspath = if (isScala3) project.getConfigurations()
                    .detachedConfiguration(getScalaCompilerDependency(scalaVersion), compilerBridgeJar, compilerInterfaceJar, getScaladocDependency(scalaVersion)!!) else project.getConfigurations()
                    .detachedConfiguration(getScalaCompilerDependency(scalaVersion), compilerBridgeJar, compilerInterfaceJar)
                jvmPluginServices!!.configureAsRuntimeClasspath(scalaRuntimeClasspath)
                return scalaRuntimeClasspath
            }

            // let's override this so that delegate isn't created at autowiring time (which would mean on every build)
            override fun visitDependencies(context: TaskDependencyResolveContext) {
                if (classpath is Buildable) {
                    context.add(classpath)
                }
            }
        }
    }

    /**
     * Searches the specified class path for a Scala Jar file (scala-compiler, scala-library,
     * scala-jdbc, etc.) with the specified appendix (compiler, library, jdbc, etc.).
     * If no such file is found, `null` is returned.
     *
     * @param classpath the class path to search
     * @param appendix the appendix to search for
     * @return a Scala Jar file with the specified appendix
     */
    fun findScalaJar(classpath: Iterable<File?>, appendix: String?): File? {
        return ScalaRuntimeHelper.findScalaJar(classpath, appendix)
    }

    /**
     * Determines the version of a Scala Jar file (scala-compiler, scala-library,
     * scala-jdbc, etc.). If the version cannot be determined, or the file is not a Scala
     * Jar file, `null` is returned.
     *
     *
     * Implementation note: The version is determined by parsing the file name, which
     * is expected to match the pattern 'scala-[component]-[version].jar'.
     *
     * @param scalaJar a Scala Jar file
     * @return the version of the Scala Jar file
     */
    fun getScalaVersion(scalaJar: File): String? {
        return ScalaRuntimeHelper.getScalaVersion(scalaJar)
    }

    /**
     * Determines Scala bridge jar to download. In Scala 3 it is released for each Scala
     * version together with the compiler jars. For Scala 2 we download sources jar and compile
     * it later on.
     *
     * @param scalaVersion version of scala to download the bridge for
     * @param zincVersion version of zinc relevant for Scala 2
     * @return bridge dependency to download
     */
    private fun getScalaBridgeDependency(scalaVersion: String, zincVersion: String): DefaultExternalModuleDependency {
        if (ScalaRuntimeHelper.isScala3(scalaVersion)) {
            return DefaultExternalModuleDependency("org.scala-lang", "scala3-sbt-bridge", scalaVersion)
        } else {
            val scalaMajorMinorVersion = Joiner.on('.').join(Splitter.on('.').splitToList(scalaVersion).subList(0, 2))
            return DefaultExternalModuleDependency("org.scala-sbt", "compiler-bridge_" + scalaMajorMinorVersion, zincVersion)
        }
    }

    /**
     * Determines Scala compiler jar to download.
     *
     * @param scalaVersion version of scala to download the compiler for
     * @return compiler dependency to download
     */
    private fun getScalaCompilerDependency(scalaVersion: String): DefaultExternalModuleDependency {
        if (ScalaRuntimeHelper.isScala3(scalaVersion)) {
            return DefaultExternalModuleDependency("org.scala-lang", "scala3-compiler_3", scalaVersion)
        } else {
            return DefaultExternalModuleDependency("org.scala-lang", "scala-compiler", scalaVersion)
        }
    }

    /**
     * Determines Scala compiler interfaces jar to download.
     *
     * @param scalaVersion version of scala to download the compiler interfaces for
     * @param zincVersion version of zinc to download the compiler interfaces for as fallback for Scala 2
     * @return compiler interfaces dependency to download
     */
    private fun getScalaCompilerInterfaceDependency(scalaVersion: String, zincVersion: String): DefaultExternalModuleDependency {
        if (ScalaRuntimeHelper.isScala3(scalaVersion)) {
            return DefaultExternalModuleDependency("org.scala-lang", "scala3-interfaces", scalaVersion)
        } else {
            return DefaultExternalModuleDependency("org.scala-sbt", "compiler-interface", zincVersion)
        }
    }

    /**
     * Determines Scaladoc jar to download. Note that scaladoc for Scala 2 is packaged along the compiler
     *
     * @param scalaVersion version of scala to download the scaladoc for
     * @return scaladoc dependency to download
     */
    private fun getScaladocDependency(scalaVersion: String): DefaultExternalModuleDependency? {
        if (scalaVersion.startsWith("3.")) {
            return DefaultExternalModuleDependency("org.scala-lang", "scaladoc_3", scalaVersion)
        } else {
            return null
        }
    }
}
