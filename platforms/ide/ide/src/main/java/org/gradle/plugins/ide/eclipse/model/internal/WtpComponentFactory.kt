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
package org.gradle.plugins.ide.eclipse.model.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.plugins.JavaPlugin
import org.gradle.internal.jvm.JavaModuleDetector
import org.gradle.plugins.ide.eclipse.model.EclipseWtpComponent
import org.gradle.plugins.ide.eclipse.model.WbDependentModule
import org.gradle.plugins.ide.eclipse.model.WbModuleEntry
import org.gradle.plugins.ide.eclipse.model.WbResource
import org.gradle.plugins.ide.eclipse.model.WtpComponent
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.plugins.ide.internal.resolver.IdeDependencySet
import org.gradle.plugins.ide.internal.resolver.IdeDependencyVisitor
import org.gradle.plugins.ide.internal.resolver.NullGradleApiSourcesResolver
import org.gradle.plugins.ide.internal.resolver.UnresolvedIdeDependencyHandler
import java.io.File

@Suppress("deprecation")
class WtpComponentFactory(project: Project, artifactRegistry: IdeArtifactRegistry?, projectRegistry: ProjectStateRegistry) {
    private val projectDependencyBuilder: ProjectDependencyBuilder
    private val currentProjectId: ProjectComponentIdentifier

    init {
        projectDependencyBuilder = ProjectDependencyBuilder(artifactRegistry)
        currentProjectId = projectRegistry.stateFor(project).getComponentIdentifier()
    }

    fun configure(wtp: EclipseWtpComponent, component: WtpComponent) {
        val entries: MutableList<WbModuleEntry?> = ArrayList<WbModuleEntry?>()
        entries.addAll(getEntriesFromSourceDirs(wtp))
        for (element in wtp.getResources()) {
            if (wtp.getProject().file(element.getSourcePath()).isDirectory()) {
                entries.add(element)
            }
        }
        entries.addAll(wtp.getProperties())
        val project = wtp.getProject()
        entries.addAll(getEntriesFromConfigurations(project, configOrEmptySet(wtp.getRootConfigurations()), configOrEmptySet(wtp.getMinusConfigurations()), wtp, "/"))
        entries.addAll(getEntriesFromConfigurations(project, configOrEmptySet(wtp.getLibConfigurations()), configOrEmptySet(wtp.getMinusConfigurations()), wtp, wtp.getLibDeployPath()))
        component.configure(wtp.getDeployName(), wtp.getContextPath(), entries)
    }

    private fun getEntriesFromConfigurations(
        project: Project,
        plusConfigurations: MutableSet<Configuration?>?,
        minusConfigurations: MutableSet<Configuration?>?,
        wtp: EclipseWtpComponent,
        deployPath: String?
    ): MutableList<WbDependentModule?> {
        val visitor: WtpDependenciesVisitor = WtpComponentFactory.WtpDependenciesVisitor(project, wtp, deployPath)
        IdeDependencySet(
            project.getDependencies(), (project as ProjectInternal).getServices().get<JavaModuleDetector?>(JavaModuleDetector::class.java),
            plusConfigurations, minusConfigurations, false, NullGradleApiSourcesResolver.Companion.INSTANCE
        ).visit(visitor)
        return visitor.entries
    }

    private inner class WtpDependenciesVisitor(private val project: Project, private val wtp: EclipseWtpComponent, private val deployPath: String?) : IdeDependencyVisitor {
        private val projectEntries: MutableList<WbDependentModule?> = ArrayList<WbDependentModule?>()
        private val moduleEntries: MutableList<WbDependentModule?> = ArrayList<WbDependentModule?>()
        private val fileEntries: MutableList<WbDependentModule?> = ArrayList<WbDependentModule?>()

        private val unresolvedIdeDependencyHandler = UnresolvedIdeDependencyHandler()

        override fun isOffline(): Boolean {
            return !includeLibraries()
        }

        fun includeLibraries(): Boolean {
            return !project.getPlugins().hasPlugin(JavaPlugin::class.java)
        }

        override fun downloadSources(): Boolean {
            return false
        }

        override fun downloadJavaDoc(): Boolean {
            return false
        }

        override fun visitProjectDependency(artifact: ResolvedArtifactResult, testDependency: Boolean, asJavaModule: Boolean) {
            val projectId = artifact.getId().getComponentIdentifier() as ProjectComponentIdentifier
            if (projectId != currentProjectId) {
                val targetProjectPath = projectDependencyBuilder.determineTargetProjectName(projectId)
                projectEntries.add(WbDependentModule(artifact.getFile().getName(), deployPath, "module:/resource/" + targetProjectPath + "/" + targetProjectPath))
            }
        }

        override fun visitModuleDependency(
            artifact: ResolvedArtifactResult,
            sources: MutableSet<ResolvedArtifactResult?>?,
            javaDoc: MutableSet<ResolvedArtifactResult?>?,
            testDependency: Boolean,
            asJavaModule: Boolean
        ) {
            if (includeLibraries()) {
                moduleEntries.add(createWbDependentModuleEntry(artifact.getFile(), wtp.getFileReferenceFactory(), deployPath))
            }
        }

        override fun visitFileDependency(artifact: ResolvedArtifactResult, testDependency: Boolean) {
            if (includeLibraries()) {
                fileEntries.add(createWbDependentModuleEntry(artifact.getFile(), wtp.getFileReferenceFactory(), deployPath))
            }
        }

        override fun visitGradleApiDependency(artifact: ResolvedArtifactResult, sources: File?, testDependency: Boolean) {
            visitFileDependency(artifact, testDependency)
        }

        override fun visitUnresolvedDependency(unresolvedDependency: UnresolvedDependencyResult) {
            unresolvedIdeDependencyHandler.log(unresolvedDependency)
        }

        val entries: MutableList<WbDependentModule?>
            /*
                     * This method returns the dependencies in buckets (projects first, then modules, then files),
                     * because that's what we used to do since 1.0. It would be better to return the dependencies
                     * in the same order as they come from the resolver, but we'll need to change all the tests for
                     * that, so defer that until later.
                     */
            get() {
                val entries: MutableList<WbDependentModule?> =
                    ArrayList<WbDependentModule?>(projectEntries.size + moduleEntries.size + fileEntries.size)
                entries.addAll(projectEntries)
                entries.addAll(moduleEntries)
                entries.addAll(fileEntries)
                return entries
            }

        fun createWbDependentModuleEntry(file: File?, fileReferenceFactory: FileReferenceFactory, deployPath: String?): WbDependentModule {
            val ref = fileReferenceFactory.fromFile(file)
            val handleSnippet = if (ref.isRelativeToPathVariable()) "var/" + ref.getPath() else "lib/" + ref.getPath()
            return WbDependentModule(ref.getFile().getName(), deployPath, "module:/classpath/" + handleSnippet)
        }
    }

    companion object {
        private fun configOrEmptySet(configuration: MutableSet<Configuration?>?): MutableSet<Configuration?> {
            if (configuration == null) {
                return mutableSetOf<Configuration?>()
            } else {
                return configuration
            }
        }

        private fun getEntriesFromSourceDirs(wtp: EclipseWtpComponent): MutableList<WbResource?> {
            val result: MutableList<WbResource?> = ArrayList<WbResource?>()
            if (wtp.getSourceDirs() != null) {
                for (dir in wtp.getSourceDirs()) {
                    if (dir.isDirectory()) {
                        result.add(WbResource(wtp.getClassesDeployPath(), wtp.getProject().relativePath(dir)))
                    }
                }
            }
            return result
        }
    }
}
