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
package org.gradle.plugins.ide.idea.internal

import com.google.common.base.Objects
import com.google.common.base.Predicate
import com.google.common.base.Predicates
import com.google.common.collect.Collections2
import com.google.common.collect.Iterables
import com.google.common.collect.Sets
import groovy.util.Node
import org.gradle.api.Action
import org.gradle.api.GradleScriptException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.XmlProvider
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.api.plugins.scala.ScalaPluginExtension
import org.gradle.api.tasks.ScalaRuntime
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath.file
import org.gradle.plugins.ide.eclipse.model.EclipseProject.file
import org.gradle.plugins.ide.eclipse.model.FileReference.file
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.FilePath
import org.gradle.plugins.ide.idea.model.FilePath.file
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.IdeaModuleIml
import org.gradle.plugins.ide.idea.model.ModuleLibrary
import org.gradle.plugins.ide.idea.model.ModuleLibrary.classes
import org.gradle.plugins.ide.idea.model.ProjectLibrary
import org.gradle.plugins.ide.idea.model.ProjectLibrary.classes
import org.gradle.plugins.ide.internal.IdeProjectMetadata.file
import org.gradle.util.internal.VersionNumber
import java.io.File
import java.util.function.Consumer
import java.util.function.Function

class IdeaScalaConfigurer(private val rootProject: Project, private val onScalaProjects: Consumer<MutableCollection<Project?>?>) {
    @Suppress("deprecation")
    fun configure() {
        rootProject.getGradle().projectsEvaluated(object : Action<Gradle?> {
            override fun execute(gradle: Gradle?) {
                val ideaTargetVersion = findIdeaTargetVersion()
                val useScalaSdk = ideaTargetVersion == null || IDEA_VERSION_WHEN_SCALA_SDK_WAS_INTRODUCED.compareTo(ideaTargetVersion) <= 0
                val scalaProjects = findProjectsApplyingIdeaAndScalaPlugins()

                onScalaProjects.accept(scalaProjects)

                val scalaCompilerLibraries: MutableMap<String?, ProjectLibrary?> = LinkedHashMap<String?, ProjectLibrary?>()
                rootProject.getTasks().named("ideaProject", object : Action<Task?> {
                    override fun execute(task: Task) {
                        task.doFirst(object : Action<Task?> {
                            override fun execute(task: Task?) {
                                if (scalaProjects.size > 0) {
                                    scalaCompilerLibraries.clear()
                                    scalaCompilerLibraries.putAll(resolveScalaCompilerLibraries(scalaProjects, useScalaSdk))
                                    declareUniqueProjectLibraries(Sets.newLinkedHashSet<ProjectLibrary?>(scalaCompilerLibraries.values))
                                }
                            }
                        })
                    }
                })
                rootProject.configure<Project?>(scalaProjects, object : Action<Project?> {
                    override fun execute(project: Project) {
                        val iml = whileDisabled<IdeaModuleIml?>(org.gradle.internal.Factory { project.getExtensions().getByType<IdeaModel?>(IdeaModel::class.java).getModule().getIml() })
                        iml!!.withXml(object : Action<XmlProvider?> {
                            override fun execute(xmlProvider: XmlProvider) {
                                if (useScalaSdk) {
                                    declareScalaSdk(scalaCompilerLibraries.get(project.getPath()), xmlProvider.asNode())
                                } else {
                                    Companion.declareScalaFacet(scalaCompilerLibraries.get(project.getPath())!!, xmlProvider.asNode())
                                }
                            }
                        })
                    }
                })
            }
        })
    }

    private fun declareUniqueProjectLibraries(projectLibraries: MutableSet<ProjectLibrary?>) {
        val existingLibraries: MutableSet<ProjectLibrary?> = rootProject.getExtensions().getByType<IdeaModel?>(IdeaModel::class.java).getProject().getProjectLibraries()
        val newLibraries: MutableSet<ProjectLibrary> = Sets.difference<ProjectLibrary?>(projectLibraries, existingLibraries)
        for (newLibrary in newLibraries) {
            val originalName = newLibrary.getName()
            var suffix = 1
            while (containsLibraryWithSameName(existingLibraries, newLibrary.getName())) {
                newLibrary.setName(originalName + "-" + suffix++)
            }
            existingLibraries.add(newLibrary)
        }
    }

    private fun findProjectsApplyingIdeaAndScalaPlugins(): MutableCollection<Project> {
        return Collections2.filter<Project?>(rootProject.getAllprojects(), object : Predicate<Project?> {
            override fun apply(project: Project): Boolean {
                val hasIdeaPlugin = IdeaIsolatedProjectsWorkarounds.hasPlugin(project, IdeaPlugin::class.java)
                val hasScalaPlugin = IdeaIsolatedProjectsWorkarounds.hasPlugin(project, ScalaBasePlugin::class.java)
                return hasIdeaPlugin && hasScalaPlugin
            }
        })
    }

    private fun findIdeaTargetVersion(): VersionNumber? {
        var targetVersion: VersionNumber? = null
        val targetVersionString: String? = rootProject.getExtensions().getByType<IdeaModel?>(IdeaModel::class.java).getTargetVersion()
        if (targetVersionString != null) {
            targetVersion = VersionNumber.parse(targetVersionString)
            if (targetVersion == VersionNumber.UNKNOWN) {
                throw GradleScriptException("String '" + targetVersionString + "' is not a valid value for IdeaModel.targetVersion.", null)
            }
        }
        return targetVersion
    }

    companion object {
        // More information: http://blog.jetbrains.com/scala/2014/10/30/scala-plugin-update-for-intellij-idea-14-rc-is-out/
        private val IDEA_VERSION_WHEN_SCALA_SDK_WAS_INTRODUCED: VersionNumber = VersionNumber.version(14)
        const val DEFAULT_SCALA_PLATFORM_VERSION: String = "2.10.7"
        private fun resolveScalaCompilerLibraries(scalaProjects: MutableCollection<Project>, useScalaSdk: Boolean): MutableMap<String?, ProjectLibrary?> {
            val scalaCompilerLibraries: MutableMap<String?, ProjectLibrary?> = LinkedHashMap<String?, ProjectLibrary?>()
            for (scalaProject in scalaProjects) {
                val owner = (scalaProject as ProjectInternal).getOwner()
                val library = owner.fromMutableState<ProjectLibrary?>(Function { p: ProjectInternal? -> Companion.createScalaSdkLibrary(p!!, useScalaSdk) })
                val duplicate = Iterables.find<ProjectLibrary?>(scalaCompilerLibraries.values, Predicates.equalTo<ProjectLibrary?>(library), null)
                scalaCompilerLibraries.put(owner.getIdentity().getProjectPath().toString(), if (duplicate == null) library else duplicate)
            }
            return scalaCompilerLibraries
        }

        private fun getIdeaModuleLibraryDependenciesAsFiles(ideaModule: IdeaModule): Iterable<File?> {
            // could make resolveDependencies() cache its result for later use by GenerateIdeaModule
            val dependencies = ideaModule.resolveDependencies()
            val files: MutableList<File?> = ArrayList<File?>()
            for (moduleLibrary in Iterables.filter<ModuleLibrary?>(dependencies, ModuleLibrary::class.java)) {
                for (filePath in Iterables.filter<FilePath?>(moduleLibrary.classes, FilePath::class.java)) {
                    files.add(filePath.file)
                }
            }
            return files
        }

        private fun createScalaSdkLibrary(scalaProject: Project, useScalaSdk: Boolean): ProjectLibrary {
            val scalaPluginExtension = scalaProject.getExtensions().getByType<ScalaPluginExtension>(ScalaPluginExtension::class.java)
            if (scalaPluginExtension.scalaVersion.isPresent()) {
                val scalaVersion = scalaPluginExtension.scalaVersion.get()
                val toolchainClasspath = scalaProject.getConfigurations().getByName("scalaToolchainRuntimeClasspath")
                return createScalaSdkFromScalaVersion(scalaVersion, toolchainClasspath.getIncoming().getFiles(), useScalaSdk)
            }

            // Otherwise, use legacy logic to scan classpath jars for version.
            val ideaModule: IdeaModule = scalaProject.getExtensions().getByType<IdeaModel?>(IdeaModel::class.java).getModule()
            val files: Iterable<File?> = getIdeaModuleLibraryDependenciesAsFiles(ideaModule)

            val runtime = scalaProject.getExtensions().getByType<ScalaRuntime>(ScalaRuntime::class.java)
            val scalaClasspath = runtime.inferScalaClasspath(files)

            var compilerJar = runtime.findScalaJar(scalaClasspath, "compiler")
            if (compilerJar == null) {
                compilerJar = runtime.findScalaJar(scalaClasspath, "compiler_3")
            }

            val scalaVersion = if (compilerJar != null) runtime.getScalaVersion(compilerJar) else DEFAULT_SCALA_PLATFORM_VERSION
            return createScalaSdkFromScalaVersion(scalaVersion, scalaClasspath, useScalaSdk)
        }

        private fun createScalaSdkFromScalaVersion(version: String?, scalaClasspath: FileCollection, useScalaSdk: Boolean): ProjectLibrary {
            if (useScalaSdk) {
                return createScalaSdkLibrary("scala-sdk-" + version, scalaClasspath)
            }
            return createProjectLibrary("scala-compiler-" + version, scalaClasspath)
        }

        private fun containsLibraryWithSameName(libraries: MutableSet<ProjectLibrary?>, name: String?): Boolean {
            return libraries.stream().anyMatch { library: ProjectLibrary? -> Objects.equal(library!!.getName(), name) }
        }

        private fun declareScalaSdk(scalaSdkLibrary: ProjectLibrary?, iml: Node?) {
            // only define a Scala SDK for a module if we could create a scalaSdkLibrary
            if (scalaSdkLibrary != null) {
                val newModuleRootManager: Node? = findOrCreateFirstChildWithAttributeValue(iml, "component", "name", "NewModuleRootManager")

                val sdkLibrary: Node = findOrCreateFirstChildWithAttributeValue(newModuleRootManager, "orderEntry", "name", scalaSdkLibrary.getName())
                setNodeAttribute(sdkLibrary, "type", "library")
                setNodeAttribute(sdkLibrary, "level", "project")
            }
        }

        private fun declareScalaFacet(scalaCompilerLibrary: ProjectLibrary, iml: Node?) {
            val facetManager: Node? = findOrCreateFirstChildWithAttributeValue(iml, "component", "name", "FacetManager")

            val scalaFacet: Node = findOrCreateFirstChildWithAttributeValue(facetManager, "facet", "type", "scala")
            setNodeAttribute(scalaFacet, "name", "Scala")


            val configuration: Node? = findOrCreateFirstChildNamed(scalaFacet, "configuration")

            val libraryLevel: Node = findOrCreateFirstChildWithAttributeValue(configuration, "option", "name", "compilerLibraryLevel")
            setNodeAttribute(libraryLevel, "value", "Project")

            val libraryName: Node = findOrCreateFirstChildWithAttributeValue(configuration, "option", "name", "compilerLibraryName")
            setNodeAttribute(libraryName, "value", scalaCompilerLibrary.getName())
        }

        private fun setNodeAttribute(node: Node, key: String?, value: String?) {
            val attributes = uncheckedCast<MutableMap<String?, String?>?>(node.attributes())
            attributes!!.put(key, value)
        }

        private fun createProjectLibrary(name: String, jars: Iterable<File?>): ProjectLibrary {
            val projectLibrary = ProjectLibrary()
            projectLibrary.setName(name)
            projectLibrary.classes = Sets.newLinkedHashSet<File?>(jars)
            return projectLibrary
        }

        private fun createScalaSdkLibrary(name: String, jars: Iterable<File?>): ProjectLibrary {
            val projectLibrary = ProjectLibrary()
            projectLibrary.setName(name)
            projectLibrary.type = "Scala"
            projectLibrary.compilerClasspath = Sets.newLinkedHashSet<File?>(jars)
            return projectLibrary
        }
    }
}
