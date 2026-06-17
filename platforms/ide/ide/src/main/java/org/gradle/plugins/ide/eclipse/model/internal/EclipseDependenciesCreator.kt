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

import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimap
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.jvm.JavaModuleDetector
import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry
import org.gradle.plugins.ide.eclipse.model.AbstractLibrary
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath
import org.gradle.plugins.ide.eclipse.model.Library
import org.gradle.plugins.ide.eclipse.model.UnresolvedLibrary
import org.gradle.plugins.ide.eclipse.model.Variable
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.plugins.ide.internal.resolver.GradleApiSourcesResolver
import org.gradle.plugins.ide.internal.resolver.IdeDependencySet
import org.gradle.plugins.ide.internal.resolver.IdeDependencyVisitor
import org.gradle.plugins.ide.internal.resolver.UnresolvedIdeDependencyHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class EclipseDependenciesCreator(
    private val classpath: EclipseClasspath,
    ideArtifactRegistry: IdeArtifactRegistry?,
    private val gradleApiSourcesResolver: GradleApiSourcesResolver?,
    private val inferModulePath: Boolean
) {
    private val projectDependencyBuilder: ProjectDependencyBuilder
    private val currentProjectId: ProjectComponentIdentifier

    init {
        this.projectDependencyBuilder = ProjectDependencyBuilder(ideArtifactRegistry)
        this.currentProjectId = (classpath.getProject() as ProjectInternal).getOwner().getComponentIdentifier()
    }

    fun createDependencyEntries(): MutableList<AbstractClasspathEntry?> {
        val visitor: EclipseDependenciesVisitor = EclipseDependenciesCreator.EclipseDependenciesVisitor(classpath.getProject())
        val testConfigurations: MutableSet<Configuration?> = classpath.getTestConfigurations().getOrElse(mutableSetOf<Configuration?>())
        IdeDependencySet(
            classpath.getProject().getDependencies(),
            (classpath.getProject() as ProjectInternal).getServices().get<JavaModuleDetector?>(JavaModuleDetector::class.java),
            classpath.getPlusConfigurations(),
            classpath.getMinusConfigurations(),
            inferModulePath,
            gradleApiSourcesResolver,
            testConfigurations
        ).visit(visitor)
        return visitor.dependencies
    }

    private inner class EclipseDependenciesVisitor(private val project: Project) : IdeDependencyVisitor {
        private val projects: MutableList<AbstractClasspathEntry?> = ArrayList<AbstractClasspathEntry?>()
        private val modules: MutableList<AbstractClasspathEntry?> = ArrayList<AbstractClasspathEntry?>()
        private val files: MutableList<AbstractClasspathEntry?> = ArrayList<AbstractClasspathEntry?>()
        private val pathToSourceSets: Multimap<String?, String?> = collectLibraryToSourceSetMapping()
        private val unresolvedIdeDependencyHandler = UnresolvedIdeDependencyHandler()

        override fun isOffline(): Boolean {
            return classpath.isProjectDependenciesOnly()
        }

        override fun downloadSources(): Boolean {
            return classpath.isDownloadSources()
        }

        override fun downloadJavaDoc(): Boolean {
            return classpath.isDownloadJavadoc()
        }

        override fun visitProjectDependency(artifact: ResolvedArtifactResult, testDependency: Boolean, asJavaModule: Boolean) {
            var asJavaModule = asJavaModule
            val componentIdentifier = artifact.getId().getComponentIdentifier() as ProjectComponentIdentifier
            if (componentIdentifier == currentProjectId) {
                return
            }
            if (isNotJar(artifact)) {
                return
            }
            val artifactId = artifact.getId()
            var buildDependencies: TaskDependency? = null
            if (artifactId is ComponentArtifactMetadata) {
                buildDependencies = (artifactId as ComponentArtifactMetadata).getBuildDependencies()
            }
            if (!asJavaModule) {
                val artifactProject = project.findProject(componentIdentifier.getProjectPath())
                if (artifactProject != null) {
                    asJavaModule = EclipseClassPathUtil.isInferModulePath(artifactProject)
                }
            }
            projects.add(projectDependencyBuilder.build(componentIdentifier, classpath.getFileReferenceFactory().fromFile(artifact.getFile()), buildDependencies, testDependency, asJavaModule))
        }

        override fun visitModuleDependency(
            artifact: ResolvedArtifactResult,
            sources: MutableSet<ResolvedArtifactResult?>,
            javaDoc: MutableSet<ResolvedArtifactResult?>,
            testDependency: Boolean,
            asJavaModule: Boolean
        ) {
            val sourceFile = if (sources.isEmpty()) null else sources.iterator().next()!!.getFile()
            val javaDocFile = if (javaDoc.isEmpty()) null else javaDoc.iterator().next()!!.getFile()
            val componentIdentifier = artifact.getId().getComponentIdentifier() as ModuleComponentIdentifier
            val moduleVersionIdentifier = DefaultModuleVersionIdentifier.newId(componentIdentifier.getModuleIdentifier(), componentIdentifier.getVersion())
            modules.add(createLibraryEntry(artifact.getFile(), sourceFile, javaDocFile, classpath, moduleVersionIdentifier, pathToSourceSets, testDependency, asJavaModule))
        }

        override fun visitFileDependency(artifact: ResolvedArtifactResult, testDependency: Boolean) {
            files.add(createLibraryEntry(artifact.getFile(), null, null, classpath, null, pathToSourceSets, testDependency, false))
        }

        override fun visitGradleApiDependency(artifact: ResolvedArtifactResult, sources: File?, testConfiguration: Boolean) {
            files.add(createLibraryEntry(artifact.getFile(), sources, null, classpath, null, pathToSourceSets, testConfiguration, false))
        }

        override fun visitUnresolvedDependency(unresolvedDependency: UnresolvedDependencyResult) {
            val unresolvedFile = unresolvedIdeDependencyHandler.asFile(unresolvedDependency, project.getProjectDir())
            val unresolvedLib = createUnresolvedLibraryEntry(unresolvedFile, classpath, pathToSourceSets, false, false) as UnresolvedLibrary
            unresolvedLib.setAttemptedSelector(unresolvedDependency.getAttempted())
            files.add(unresolvedLib)
            unresolvedIdeDependencyHandler.log(unresolvedDependency)
        }

        val dependencies: MutableList<AbstractClasspathEntry?>
            /*
                     * This method returns the dependencies in buckets (projects first, then modules, then files),
                     * because that's what we used to do since 1.0. It would be better to return the dependencies
                     * in the same order as they come from the resolver, but we'll need to change all the tests for
                     * that, so defer that until later.
                     */
            get() {
                val dependencies: MutableList<AbstractClasspathEntry?> =
                    ArrayList<AbstractClasspathEntry?>(projects.size + modules.size + files.size)
                dependencies.addAll(projects)
                dependencies.addAll(modules)
                dependencies.addAll(files)
                return dependencies
            }

        fun collectLibraryToSourceSetMapping(): Multimap<String?, String?> {
            val pathToSourceSetNames: Multimap<String?, String?> = LinkedHashMultimap.create<String?, String?>()
            val sourceSets = classpath.getSourceSets()

            // for non-java projects there are no source sets configured
            if (sourceSets == null) {
                return pathToSourceSetNames
            }

            for (sourceSet in sourceSets) {
                for (f in collectClasspathFiles(sourceSet)) {
                    pathToSourceSetNames.put(f.getAbsolutePath(), sourceSet.getName().replace(",", ""))
                }
            }
            return pathToSourceSetNames
        }

        /*
         * SourceSet has no access to configurations where we could ask for a lenient view. This
         * means we have to deal with possible dependency resolution issues here. We catch and
         * log the exceptions here so that the Eclipse model can be generated even if there are
         * unresolvable dependencies defined in the configuration.
         *
         * We can probably do better by inspecting the runtime classpath and finding out which
         * Configurations are part of it and only traversing any extra file collections manually.
         */
        fun collectClasspathFiles(sourceSet: SourceSet): MutableCollection<File> {
            val result = ImmutableList.builder<File?>()
            try {
                result.addAll(sourceSet.runtimeClasspath)
            } catch (e: Exception) {
                LOGGER.debug("Failed to collect source sets for Eclipse dependencies", e)
            }
            return result.build()
        }

        fun createUnresolvedLibraryEntry(binary: File, classpath: EclipseClasspath, pathToSourceSets: Multimap<String?, String?>, testDependency: Boolean, asJavaModule: Boolean): AbstractLibrary {
            return createLibraryEntry(binary, null, null, classpath, null, pathToSourceSets, testDependency, asJavaModule, false)
        }

        fun createLibraryEntry(
            binary: File,
            source: File?,
            javadoc: File?,
            classpath: EclipseClasspath,
            id: ModuleVersionIdentifier?,
            pathToSourceSets: Multimap<String?, String?>,
            testDependency: Boolean,
            asJavaModule: Boolean,
            resolved: Boolean = true
        ): AbstractLibrary {
            val referenceFactory = classpath.getFileReferenceFactory()

            val binaryRef = referenceFactory.fromFile(binary)
            val sourceRef = referenceFactory.fromFile(source)
            val javadocRef = referenceFactory.fromFile(javadoc)

            val out: AbstractLibrary
            if (binaryRef.isRelativeToPathVariable()) {
                out = Variable(binaryRef)
            } else if (resolved) {
                out = Library(binaryRef)
            } else {
                out = UnresolvedLibrary(binaryRef)
            }

            out.setJavadocPath(javadocRef)
            out.setSourcePath(sourceRef)
            out.setExported(false)
            out.setModuleVersion(id)

            val sourceSets = pathToSourceSets.get(binary.getAbsolutePath())
            if (sourceSets != null) {
                out.getEntryAttributes().put(EclipsePluginConstants.GRADLE_USED_BY_SCOPE_ATTRIBUTE_NAME, Joiner.on(',').join(sourceSets))
            }

            if (testDependency) {
                out.getEntryAttributes().put(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
            }

            if (asJavaModule) {
                out.getEntryAttributes().put(EclipsePluginConstants.MODULE_ATTRIBUTE_KEY, EclipsePluginConstants.MODULE_ATTRIBUTE_VALUE)
            }

            return out
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(EclipseDependenciesCreator::class.java)
        private fun isNotJar(artifact: ResolvedArtifactResult): Boolean {
            val libraryElements = artifact.getVariant().getAttributes().getAttribute<LibraryElements?>(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE)
            return libraryElements == null
                    || libraryElements.getName() != LibraryElements.JAR
        }
    }
}
