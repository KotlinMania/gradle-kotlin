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
package org.gradle.plugins.ide.internal.tooling

import com.google.common.annotations.VisibleForTesting
import org.apache.commons.lang3.Strings
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.ProjectStateLookup
import org.gradle.api.internal.tasks.TaskDependencyUtil
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.build.AllProjectsAccess
import org.gradle.internal.build.IncludedBuildState
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry
import org.gradle.plugins.ide.eclipse.model.AbstractLibrary
import org.gradle.plugins.ide.eclipse.model.AccessRule
import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry
import org.gradle.plugins.ide.eclipse.model.Container
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath
import org.gradle.plugins.ide.eclipse.model.EclipseJdt
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.eclipse.model.Library
import org.gradle.plugins.ide.eclipse.model.Output
import org.gradle.plugins.ide.eclipse.model.Project
import org.gradle.plugins.ide.eclipse.model.Project.configure
import org.gradle.plugins.ide.eclipse.model.ProjectDependency
import org.gradle.plugins.ide.eclipse.model.SourceFolder
import org.gradle.plugins.ide.eclipse.model.UnresolvedLibrary
import org.gradle.plugins.ide.idea.model.Module.configure
import org.gradle.plugins.ide.internal.configurer.EclipseModelAwareUniqueProjectNameProvider
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultAccessRule
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultClasspathAttribute
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseBuildCommand
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseClasspathContainer
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseExternalDependency
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseJavaSourceSettings
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseLinkedResource
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseOutputLocation
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseProject
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseProjectDependency
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseProjectNature
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseSourceDirectory
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseTask
import org.gradle.plugins.ide.internal.tooling.java.DefaultInstalledJdk
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleModuleVersion
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject
import org.gradle.tooling.model.GradleModuleVersion
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.eclipse.EclipseRuntime
import org.gradle.tooling.model.eclipse.EclipseWorkspaceProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import org.gradle.util.Path
import org.gradle.util.internal.GUtil
import java.io.File
import java.util.LinkedList
import java.util.function.BinaryOperator
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors

class EclipseModelBuilder @VisibleForTesting constructor(
    private val gradleProjectBuilder: GradleProjectBuilderInternal,
    private val uniqueProjectNameProvider: EclipseModelAwareUniqueProjectNameProvider
) : ParameterizedToolingModelBuilder<EclipseRuntime?> {
    private var projectDependenciesOnly = false
    private var result: DefaultEclipseProject? = null
    private var eclipseProjects: MutableList<DefaultEclipseProject?>? = null
    private var tasksFactory: TasksFactory? = null
    private var rootGradleProject: DefaultGradleProject? = null
    private var currentProjectId: ProjectIdentity? = null
    private var eclipseRuntime: EclipseRuntime? = null
    private var projectOpenStatus: MutableMap<String?, Boolean?> = HashMap<String?, Boolean?>()

    constructor(gradleProjectBuilder: GradleProjectBuilderInternal, projectStateLookup: ProjectStateLookup) : this(gradleProjectBuilder, EclipseModelAwareUniqueProjectNameProvider(projectStateLookup))

    override fun canBuild(modelName: String): Boolean {
        return modelName == ECLIPSE_PROJECT_MODEL_NAME || modelName == ECLIPSE_HIERARCHICAL_PROJECT_MODEL_NAME
    }

    override fun getParameterType(): Class<EclipseRuntime?> {
        return EclipseRuntime::class.java
    }

    override fun buildAll(modelName: String, eclipseRuntime: EclipseRuntime, project: Project): Any? {
        this.eclipseRuntime = eclipseRuntime
        val projects = eclipseRuntime.getWorkspace().getProjects()
        val projectsInBuild = HashSet<EclipseWorkspaceProject?>(projects)
        projectsInBuild.removeAll(gatherExternalProjects(project.getRootProject() as ProjectInternal, projects))
        projectOpenStatus = projectsInBuild.stream().collect(
            Collectors.toMap(
                Function { obj: EclipseWorkspaceProject? -> obj!!.getName() },
                Function { project: EclipseWorkspaceProject? -> Companion.isProjectOpen(project!!) },
                BinaryOperator { a: Boolean?, b: Boolean? -> a || b })
        )

        return buildAll(modelName, project)
    }

    override fun buildAll(modelName: String, project: Project): DefaultEclipseProject? {
        val includeTasks = modelName == ECLIPSE_PROJECT_MODEL_NAME
        tasksFactory = TasksFactory(includeTasks)
        projectDependenciesOnly = modelName == ECLIPSE_HIERARCHICAL_PROJECT_MODEL_NAME
        currentProjectId = (project as ProjectInternal).getProjectIdentity()
        eclipseProjects = ArrayList<DefaultEclipseProject?>()
        val root = project.getRootProject()
        val rootProjectState = root.getOwner()
        rootGradleProject = gradleProjectBuilder.buildForRoot(project)
        tasksFactory!!.collectTasks(root)
        applyEclipsePlugin(rootProjectState, HashSet<Path?>())
        deduplicateProjectNames(root)
        buildHierarchy(rootProjectState)
        populate(rootProjectState)
        return result
    }

    private fun deduplicateProjectNames(root: ProjectInternal) {
        uniqueProjectNameProvider.setReservedProjectNames(calculateReservedProjectNames(root, eclipseRuntime))
        for (project in root.getAllprojects()) {
            val eclipseModel: EclipseModel? = project.getExtensions().findByType<EclipseModel?>(EclipseModel::class.java)
            if (eclipseModel != null) {
                eclipseModel.project!!.name = uniqueProjectNameProvider.getUniqueName((project as ProjectInternal).getProjectIdentity())
            }
        }
    }

    private fun buildHierarchy(projectState: ProjectState): DefaultEclipseProject {
        val children: MutableList<DefaultEclipseProject> = ArrayList<DefaultEclipseProject>()
        for (child in projectState.getChildProjects()) {
            children.add(buildHierarchy(child))
        }

        data class NameAndDescription(val name: String?, val description: String?)

        val data = projectState.fromMutableState<NameAndDescription?>(Function { project: ProjectInternal? ->
            val eclipseModel = project!!.getExtensions().getByType<EclipseModel>(EclipseModel::class.java)
            val internalProject = eclipseModel.project

            val name = internalProject!!.name
            val description = GUtil.elvis<String?>(internalProject.comment, null)
            NameAndDescription(name, description)
        })
        val path = projectState.getIdentity().getProjectPath().asString()
        val projectDir = projectState.getProjectDir()
        val eclipseProject = DefaultEclipseProject(data!!.name, path, data.description, projectDir, children).setGradleProject(rootGradleProject!!.findByPath(path)!!)

        for (child in children) {
            child.parent = eclipseProject
        }
        addProject(projectState, eclipseProject)
        return eclipseProject
    }

    private fun addProject(project: ProjectState, eclipseProject: DefaultEclipseProject?) {
        if (project.getIdentity() == currentProjectId) {
            result = eclipseProject
        }
        eclipseProjects!!.add(eclipseProject)
    }

    private fun populate(p: ProjectState) {
        p.applyToMutableState(Consumer { project: ProjectInternal? ->
            val eclipseModel = project!!.getExtensions().getByType<EclipseModel>(EclipseModel::class.java)
            val projectDependenciesOnly = this.projectDependenciesOnly

            val classpathElements: ClasspathElements = gatherClasspathElements(projectOpenStatus, eclipseModel.getClasspath(), projectDependenciesOnly)

            val eclipseProject = findEclipseProject(project)

            eclipseProject.classpath = classpathElements.externalDependencies
            eclipseProject.setProjectDependencies(classpathElements.projectDependencies)
            eclipseProject.setSourceDirectories(classpathElements.sourceDirectories)
            eclipseProject.classpathContainers = classpathElements.classpathContainers
            eclipseProject.outputLocation = if (classpathElements.eclipseOutputLocation != null) classpathElements.eclipseOutputLocation else DefaultEclipseOutputLocation("bin")
            eclipseProject.setAutoBuildTasks(!TaskDependencyUtil.getDependenciesForInternalUse(eclipseModel.getAutoBuildTasks(), null).isEmpty())

            val xmlProject = Project(XmlTransformer())

            val projectFile = eclipseModel.project!!.file
            if (projectFile == null) {
                xmlProject.configure(eclipseModel.project!!)
            } else {
                eclipseModel.project!!.mergeXmlProject(xmlProject)
            }

            Companion.populateEclipseProjectTasks(eclipseProject, tasksFactory!!.getTasks(project))
            populateEclipseProject(eclipseProject, xmlProject)
            populateEclipseProjectJdt(eclipseProject, eclipseModel.jdt)
        })

        for (childProject in p.getChildProjects()) {
            populate(childProject)
        }
    }

    private fun findEclipseProject(project: Project): DefaultEclipseProject {
        return org.gradle.util.internal.CollectionUtils.findFirst<DefaultEclipseProject?>(
            eclipseProjects,
            org.gradle.api.specs.Spec { element: org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseProject? -> element.getGradleProject().path == project.getPath() })!!
    }

    private fun getRootBuild(gradle: GradleInternal): GradleInternal? {
        if (gradle.getParent() == null) {
            return gradle
        }
        return gradle.getParent()
    }

    private fun calculateReservedProjectNames(rootProject: ProjectInternal, parameter: EclipseRuntime?): MutableList<String?> {
        if (parameter == null) {
            return mutableListOf<String?>()
        }

        val workspace = parameter.getWorkspace()
        if (workspace == null) {
            return mutableListOf<String?>()
        }

        val projects = workspace.getProjects()
        if (projects == null) {
            return mutableListOf<String?>()
        }

        val reservedProjectNames: MutableList<String?> = ArrayList<String?>()
        val externalProjects = gatherExternalProjects(rootProject, projects)
        for (externalProject in externalProjects) {
            reservedProjectNames.add(externalProject.getName())
        }

        return reservedProjectNames
    }

    private fun gatherExternalProjects(rootProject: ProjectInternal, projects: MutableList<EclipseWorkspaceProject?>): MutableList<EclipseWorkspaceProject> {
        // The eclipse workspace contains projects from root and included builds. Check projects from all builds
        // so that models built for included builds do not consider projects from parent builds as external.
        val gradleProjectLocations: MutableSet<File?> = Companion.collectAllProjects(ArrayList<ProjectState?>(), getRootBuild(rootProject.getGradle())!!, HashSet<Gradle?>()).stream()
            .map<File?> { p: ProjectState? -> p!!.getProjectDir().getAbsoluteFile() }.collect(
                Collectors.toSet()
            )
        val externalProjects: MutableList<EclipseWorkspaceProject> = ArrayList<EclipseWorkspaceProject>()
        for (project in projects) {
            if (project == null || project.getLocation() == null || project.getName() == null || project.getLocation() == null) {
                continue
            }
            if (!gradleProjectLocations.contains(project.getLocation().getAbsoluteFile())) {
                externalProjects.add(project)
            }
        }
        return externalProjects
    }

    class ClasspathElements {
        val externalDependencies: MutableList<DefaultEclipseExternalDependency?> = ArrayList<DefaultEclipseExternalDependency?>()
        val projectDependencies: MutableList<DefaultEclipseProjectDependency?> = ArrayList<DefaultEclipseProjectDependency?>()
        val sourceDirectories: MutableList<DefaultEclipseSourceDirectory?> = ArrayList<DefaultEclipseSourceDirectory?>()
        val classpathContainers: MutableList<DefaultEclipseClasspathContainer?> = ArrayList<DefaultEclipseClasspathContainer?>()
        val buildDependencies: MutableList<TaskDependency?> = ArrayList<TaskDependency?>()
        var eclipseOutputLocation: DefaultEclipseOutputLocation? = null
    }

    companion object {
        private val ECLIPSE_PROJECT_MODEL_NAME: String = org.gradle.tooling.model.eclipse.EclipseProject::class.java.getName()
        private val ECLIPSE_HIERARCHICAL_PROJECT_MODEL_NAME: String = HierarchicalEclipseProject::class.java.getName()

        fun isProjectOpen(project: EclipseWorkspaceProject): Boolean {
            // TODO we should refactor this to general, compatibility mapping solution, as we have it for model loading. See HasCompatibilityMapping class.
            try {
                return project.isOpen()
            } catch (e: UnsupportedMethodException) {
                // isOpen was added in gradle 5.6. for 5.5 we default to true
                return true
            }
        }

        private fun applyEclipsePlugin(rootState: ProjectState, alreadyProcessed: MutableSet<Path?>) {
            val build = rootState.getOwner()
            build.getProjects().applyToMutableStateOfAllProjects(Consumer { access: AllProjectsAccess? ->
                for (p in access!!.getMutableModel(rootState).getAllprojects()) {
                    p.getPluginManager().apply(EclipsePlugin::class.java)
                }
            })
            for (reference in build.getMutableModel().includedBuilds()) {
                val target = reference.getTarget()
                if (target is IncludedBuildState) {
                    target.ensureProjectsConfigured()
                    if (alreadyProcessed.add(target.getIdentityPath())) {
                        applyEclipsePlugin(target.getProjects().getRootProject(), alreadyProcessed)
                    }
                }
            }
        }

        fun gatherClasspathElements(projectOpenStatus: MutableMap<String?, Boolean?>, eclipseClasspath: EclipseClasspath, projectDependenciesOnly: Boolean): ClasspathElements {
            val classpathElements = ClasspathElements()
            eclipseClasspath.isProjectDependenciesOnly = projectDependenciesOnly

            val classpathEntries: MutableList<ClasspathEntry?>?
            if (eclipseClasspath.file == null) {
                classpathEntries = eclipseClasspath.resolveDependencies()
            } else {
                val classpath = Classpath(eclipseClasspath.fileReferenceFactory)
                eclipseClasspath.mergeXmlClasspath(classpath)
                classpathEntries = classpath.entries
            }

            val projectDependencyMap: MutableMap<String?, DefaultEclipseProjectDependency?> = LinkedHashMap<String?, DefaultEclipseProjectDependency?>()

            for (entry in classpathEntries) {
                //we don't handle Variables at the moment because users didn't request it yet
                //and it would probably push us to add support in the tooling api to retrieve the variable mappings.
                if (entry is Library) {
                    val library = entry as AbstractLibrary
                    val file: File = library.getLibrary()!!.file
                    val source: File? = if (library.sourcePath == null) null else library.sourcePath!!.file
                    val javadoc: File? = if (library.getJavadocPath() == null) null else library.getJavadocPath()!!.file
                    val dependency: DefaultEclipseExternalDependency?
                    val moduleVersionId = library.moduleVersion
                    var moduleVersion: GradleModuleVersion? = null
                    if (moduleVersionId != null) {
                        moduleVersion = DefaultGradleModuleVersion(moduleVersionId.getGroup(), moduleVersionId.getName(), moduleVersionId.getVersion())
                    }
                    if (entry is UnresolvedLibrary) {
                        val unresolvedLibrary = entry
                        dependency = DefaultEclipseExternalDependency.createUnresolved(
                            file,
                            javadoc,
                            source,
                            moduleVersion,
                            library.isExported,
                            createAttributes(library),
                            createAccessRules(library),
                            unresolvedLibrary.attemptedSelector!!.getDisplayName()
                        )
                    } else {
                        dependency = DefaultEclipseExternalDependency.createResolved(file, javadoc, source, moduleVersion, library.isExported, createAttributes(library), createAccessRules(library))
                    }
                    classpathElements.externalDependencies.add(dependency)
                } else if (entry is ProjectDependency) {
                    val projectDependency = entry
                    // By removing the leading "/", this is no longer a "path" as defined by Eclipse
                    val path = Strings.CS.removeStart(projectDependency.path, "/")
                    val isProjectOpen: Boolean = projectOpenStatus.getOrDefault(path, true)!!
                    if (!isProjectOpen) {
                        val source: File? = if (projectDependency.publicationSourcePath == null) null else projectDependency.publicationSourcePath!!.file
                        val javadoc: File? = if (projectDependency.publicationJavadocPath == null) null else projectDependency.publicationJavadocPath!!.file
                        classpathElements.externalDependencies.add(
                            DefaultEclipseExternalDependency.createResolved(
                                projectDependency.publication!!.file,
                                javadoc,
                                source,
                                null,
                                projectDependency.isExported,
                                createAttributes(projectDependency),
                                createAccessRules(projectDependency)
                            )
                        )
                        classpathElements.buildDependencies.add(projectDependency.getBuildDependencies())
                    } else {
                        val dependency = DefaultEclipseProjectDependency(path, projectDependency.isExported, createAttributes(projectDependency), createAccessRules(projectDependency))
                        projectDependencyMap.merge(
                            path,
                            dependency
                        ) { oldDependency: DefaultEclipseProjectDependency?, newDependency: DefaultEclipseProjectDependency? ->
                            if (!Companion.hasTestSourcesAttribute(oldDependency!!) && Companion.hasTestSourcesAttribute(
                                    newDependency!!
                                )
                            ) oldDependency else newDependency
                        }
                    }
                } else if (entry is SourceFolder) {
                    val sourceFolder = entry
                    val path = sourceFolder.path
                    val excludes = sourceFolder.excludes
                    val includes = sourceFolder.includes
                    val output = sourceFolder.output
                    classpathElements.sourceDirectories.add(
                        DefaultEclipseSourceDirectory(
                            path,
                            sourceFolder.getDir(),
                            excludes,
                            includes,
                            output,
                            createAttributes(sourceFolder),
                            createAccessRules(sourceFolder)
                        )
                    )
                } else if (entry is Container) {
                    val container = entry
                    classpathElements.classpathContainers.add(DefaultEclipseClasspathContainer(container.path, container.isExported, createAttributes(container), createAccessRules(container)))
                } else if (entry is Output) {
                    classpathElements.eclipseOutputLocation = DefaultEclipseOutputLocation(entry.path)
                }
            }
            classpathElements.projectDependencies.addAll(projectDependencyMap.values)
            return classpathElements
        }

        private fun populateEclipseProjectTasks(eclipseProject: DefaultEclipseProject, projectTasks: Iterable<Task>) {
            val tasks: MutableList<DefaultEclipseTask?> = ArrayList<DefaultEclipseTask?>()
            for (t in projectTasks) {
                tasks.add(DefaultEclipseTask(eclipseProject, t.getPath(), t.getName(), t.getDescription()))
            }
            eclipseProject.tasks = tasks
        }

        private fun populateEclipseProject(eclipseProject: DefaultEclipseProject, xmlProject: org.gradle.plugins.ide.eclipse.model.Project) {
            val linkedResources: MutableList<DefaultEclipseLinkedResource?> = LinkedList<DefaultEclipseLinkedResource?>()
            for (r in xmlProject.linkedResources!!) {
                linkedResources.add(DefaultEclipseLinkedResource(r.name, r.type, r.location, r.locationUri))
            }
            eclipseProject.linkedResources = linkedResources

            val natures: MutableList<DefaultEclipseProjectNature?> = ArrayList<DefaultEclipseProjectNature?>()
            for (n in xmlProject.natures!!) {
                natures.add(DefaultEclipseProjectNature(n))
            }
            eclipseProject.projectNatures = natures

            val buildCommands: MutableList<DefaultEclipseBuildCommand?> = ArrayList<DefaultEclipseBuildCommand?>()
            for (b in xmlProject.buildCommands!!) {
                val arguments: MutableMap<String?, String?> = LinkedHashMap<String?, String?>()
                for (entry in b.arguments!!.entries) {
                    arguments.put(Companion.convertGString(entry.key!!), Companion.convertGString(entry.value!!))
                }
                buildCommands.add(DefaultEclipseBuildCommand(b.name, arguments))
            }
            eclipseProject.buildCommands = buildCommands
        }

        private fun populateEclipseProjectJdt(eclipseProject: DefaultEclipseProject, jdt: EclipseJdt?) {
            if (jdt != null) {
                eclipseProject.javaSourceSettings =
                    DefaultEclipseJavaSourceSettings().setSourceLanguageLevel(jdt.getSourceCompatibility()).setTargetBytecodeVersion(jdt.getTargetCompatibility()).setJdk(
                        DefaultInstalledJdk.current()
                    )
            }
        }

        private fun createAttributes(classpathEntry: AbstractClasspathEntry): MutableList<DefaultClasspathAttribute?> {
            val result: MutableList<DefaultClasspathAttribute?> = ArrayList<DefaultClasspathAttribute?>()
            val attributes = classpathEntry.entryAttributes
            for (entry in attributes.entries) {
                val value = entry.value
                result.add(DefaultClasspathAttribute(Companion.convertGString(entry.key!!), if (value == null) "" else value.toString()))
            }
            return result
        }

        private fun createAccessRules(classpathEntry: AbstractClasspathEntry): MutableList<DefaultAccessRule?> {
            val result: MutableList<DefaultAccessRule?> = ArrayList<DefaultAccessRule?>()
            for (accessRule in classpathEntry.accessRules!!) {
                result.add(createAccessRule(accessRule))
            }
            return result
        }

        private fun createAccessRule(accessRule: AccessRule): DefaultAccessRule {
            val kindCode: Int
            val kind = accessRule.kind
            when (kind) {
                "accessible", "0" -> kindCode = 0
                "nonaccessible", "1" -> kindCode = 1
                "discouraged", "2" -> kindCode = 2
                else -> kindCode = 0
            }
            return DefaultAccessRule(kindCode, accessRule.pattern)
        }

        private fun collectAllProjects(all: MutableList<ProjectState?>, gradle: GradleInternal, allBuilds: MutableSet<Gradle?>): MutableList<ProjectState?> {
            all.addAll(gradle.getOwner().getProjects().getAllProjects())
            for (reference in gradle.includedBuilds()) {
                val target = reference.getTarget()
                if (target is IncludedBuildState) {
                    target.ensureProjectsConfigured()
                    val build = target.getMutableModel()
                    if (!allBuilds.contains(build)) {
                        allBuilds.add(build)
                        collectAllProjects(all, build, allBuilds)
                    }
                }
            }
            return all
        }

        private fun hasTestSourcesAttribute(projectDependency: DefaultEclipseProjectDependency): Boolean {
            return projectDependency.classpathAttributes!!.stream()
                .anyMatch { attribute: DefaultClasspathAttribute? -> EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY == attribute!!.name && EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE == attribute.value }
        }


        /*
     * Groovy manipulates the JVM to let GString extend String.
     * Whenever we have a Set or Map containing Strings, it might also
     * contain GStrings. This breaks deserialization on the client.
     * This method forces GString to String conversion.
     */
        private fun convertGString(original: CharSequence): String {
            return original.toString()
        }
    }
}
