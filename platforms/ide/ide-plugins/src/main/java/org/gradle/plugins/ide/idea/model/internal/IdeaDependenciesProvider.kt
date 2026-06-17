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
package org.gradle.plugins.ide.idea.model.internal

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.jvm.JavaModuleDetector
import org.gradle.plugins.ide.idea.model.Dependency
import org.gradle.plugins.ide.idea.model.FilePath
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.Path
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.plugins.ide.internal.resolver.GradleApiSourcesResolver
import org.gradle.plugins.ide.internal.resolver.IdeDependencySet
import org.gradle.plugins.ide.internal.resolver.IdeDependencyVisitor
import org.gradle.plugins.ide.internal.resolver.UnresolvedIdeDependencyHandler
import java.io.File
import java.util.LinkedList
import java.util.function.Function

class IdeaDependenciesProvider(project: ProjectInternal, artifactRegistry: IdeArtifactRegistry?, private val gradleApiSourcesResolver: GradleApiSourcesResolver) {
    private val moduleDependencyBuilder: ModuleDependencyBuilder
    private val optimizer: IdeaDependenciesOptimizer
    private val currentProjectId: ProjectComponentIdentifier

    init {
        moduleDependencyBuilder = ModuleDependencyBuilder(artifactRegistry)
        currentProjectId = project.getOwner().getComponentIdentifier()
        optimizer = IdeaDependenciesOptimizer()
    }

    fun provide(ideaModule: IdeaModule): MutableSet<Dependency?> {
        val result: MutableSet<Dependency?> = LinkedHashSet<Dependency?>()
        result.addAll(getOutputLocations(ideaModule))
        result.addAll(getDependencies(ideaModule))
        return result
    }

    private fun getOutputLocations(ideaModule: IdeaModule): MutableSet<SingleEntryModuleLibrary?> {
        if (ideaModule.getSingleEntryLibraries() == null) {
            return mutableSetOf<SingleEntryModuleLibrary?>()
        }
        val outputLocations: MutableSet<SingleEntryModuleLibrary?> = LinkedHashSet<SingleEntryModuleLibrary?>()
        for (outputLocation in ideaModule.getSingleEntryLibraries().entries) {
            val scope = outputLocation.key
            for (file in outputLocation.value) {
                if (file != null && file.isDirectory()) {
                    outputLocations.add(SingleEntryModuleLibrary(toPath(ideaModule, file), scope))
                }
            }
        }
        return outputLocations
    }

    private fun getDependencies(ideaModule: IdeaModule): MutableSet<Dependency?> {
        val dependencies: MutableSet<Dependency?> = LinkedHashSet<Dependency?>()
        val unresolvedDependencies: MutableMap<ComponentSelector?, UnresolvedDependencyResult?> = LinkedHashMap<ComponentSelector?, UnresolvedDependencyResult?>()
        for (scope in GeneratedIdeaScope.values()) {
            val visitor = visitDependencies(ideaModule, scope)
            dependencies.addAll(visitor.dependencies)
            unresolvedDependencies.putAll(visitor.unresolvedDependencies)
        }
        optimizer.optimizeDeps(dependencies)
        UnresolvedIdeDependencyHandler().log(unresolvedDependencies.values)
        return dependencies
    }

    private fun visitDependencies(ideaModule: IdeaModule, scope: GeneratedIdeaScope): IdeaDependenciesVisitor {
        val projectInternal = ideaModule.getProject() as ProjectInternal
        val handler = projectInternal.getDependencies()
        val plusConfigurations = getPlusConfigurations(ideaModule, scope)
        val minusConfigurations = getMinusConfigurations(ideaModule, scope)
        val javaModuleDetector: JavaModuleDetector? = projectInternal.getServices().get<JavaModuleDetector?>(JavaModuleDetector::class.java)

        val visitor: IdeaDependenciesVisitor = IdeaDependenciesProvider.IdeaDependenciesVisitor(ideaModule, scope.name)
        return projectInternal.getOwner().fromMutableState<IdeaDependenciesVisitor>(Function { p: ProjectInternal? ->
            IdeDependencySet(handler, javaModuleDetector, plusConfigurations, minusConfigurations, false, gradleApiSourcesResolver).visit(visitor)
            visitor
        })
    }

    private fun getPlusConfigurations(ideaModule: IdeaModule, scope: GeneratedIdeaScope): MutableCollection<Configuration?> {
        return getConfigurations(ideaModule, scope, SCOPE_PLUS)
    }

    private fun getMinusConfigurations(ideaModule: IdeaModule, scope: GeneratedIdeaScope): MutableCollection<Configuration?> {
        return getConfigurations(ideaModule, scope, SCOPE_MINUS)
    }

    private fun getConfigurations(ideaModule: IdeaModule, scope: GeneratedIdeaScope, plusMinus: String?): MutableCollection<Configuration?> {
        val plusMinusConfigurations = getPlusMinusConfigurations(ideaModule, scope)
        return (if (plusMinusConfigurations.containsKey(plusMinus)) plusMinusConfigurations.get(plusMinus) else kotlin.collections.mutableListOf<org.gradle.api.artifacts.Configuration?>())!!
    }

    private fun getPlusMinusConfigurations(ideaModule: IdeaModule, scope: GeneratedIdeaScope): MutableMap<String?, MutableCollection<Configuration?>?> {
        val plusMinusConfigurations = ideaModule.getScopes().get(scope.name)
        return if (plusMinusConfigurations != null) plusMinusConfigurations else mutableMapOf<String?, MutableCollection<Configuration?>?>()
    }

    @Suppress("deprecation")
    private fun toPath(ideaModule: IdeaModule, file: File?): FilePath? {
        return if (file != null) DeprecationLogger.whileDisabled<FilePath>(org.gradle.internal.Factory { ideaModule.getPathFactory().path(file) }) else null
    }

    private inner class IdeaDependenciesVisitor(private val ideaModule: IdeaModule, private val scope: String?) : IdeDependencyVisitor {
        private val unresolvedIdeDependencyHandler = UnresolvedIdeDependencyHandler()

        private val projectDependencies: MutableList<Dependency?> = LinkedList<Dependency?>()
        private val moduleDependencies: MutableList<Dependency?> = LinkedList<Dependency?>()
        private val fileDependencies: MutableList<Dependency?> = LinkedList<Dependency?>()
        val unresolvedDependencies: MutableMap<ComponentSelector?, UnresolvedDependencyResult?> = LinkedHashMap<ComponentSelector?, UnresolvedDependencyResult?>()

        val isOffline: Boolean
            get() = ideaModule.isOffline()

        override fun downloadSources(): Boolean {
            return ideaModule.isDownloadSources()
        }

        override fun downloadJavaDoc(): Boolean {
            return ideaModule.isDownloadJavadoc()
        }

        override fun visitProjectDependency(artifact: ResolvedArtifactResult, testDependency: Boolean, asJavaModule: Boolean) {
            val projectId = artifact.getId().getComponentIdentifier() as ProjectComponentIdentifier
            if (projectId != currentProjectId) {
                projectDependencies.add(moduleDependencyBuilder.create(projectId, scope))
            }
        }

        override fun visitModuleDependency(
            artifact: ResolvedArtifactResult,
            sources: MutableSet<ResolvedArtifactResult>,
            javaDoc: MutableSet<ResolvedArtifactResult>,
            testDependency: Boolean,
            asJavaModule: Boolean
        ) {
            val moduleId = artifact.getId().getComponentIdentifier() as ModuleComponentIdentifier
            val library = SingleEntryModuleLibrary(toPath(ideaModule, artifact.getFile()), scope)
            library.moduleVersion = DefaultModuleVersionIdentifier.newId(moduleId.getModuleIdentifier(), moduleId.getVersion())
            val sourcePaths: MutableSet<Path?> = LinkedHashSet<Path?>()
            for (sourceArtifact in sources) {
                sourcePaths.add(toPath(ideaModule, sourceArtifact.getFile()))
            }
            library.sources = sourcePaths
            val javaDocPaths: MutableSet<Path?> = LinkedHashSet<Path?>()
            for (javaDocArtifact in javaDoc) {
                javaDocPaths.add(toPath(ideaModule, javaDocArtifact.getFile()))
            }
            library.javadoc = javaDocPaths
            moduleDependencies.add(library)
        }

        override fun visitFileDependency(artifact: ResolvedArtifactResult, testDependency: Boolean) {
            fileDependencies.add(SingleEntryModuleLibrary(toPath(ideaModule, artifact.getFile()), scope))
        }

        override fun visitGradleApiDependency(artifact: ResolvedArtifactResult, sources: File?, testDependency: Boolean) {
            fileDependencies.add(SingleEntryModuleLibrary(toPath(ideaModule, artifact.getFile()), null, toPath(ideaModule, sources), scope))
        }

        /*
         * Remembers the unresolved dependency for later logging and also adds a fake
         * file dependency, with the file path pointing to the attempted component selector.
         * This shows up in the IDE as a red flag in the dependencies view. That's not the best
         * usability and it also muddies the API contract, because we disguise an unresolved
         * dependency as a file dependency, even though that file really doesn't exist.
         *
         * Instead, when generating files on the command line, the logged warning is enough.
         * When using the Tooling API, a dedicated "unresolved dependency" object would be better
         * and could be shown in a notification. The command line warning should probably be omitted in that case.
         */
        override fun visitUnresolvedDependency(unresolvedDependency: UnresolvedDependencyResult) {
            val unresolvedFile = unresolvedIdeDependencyHandler.asFile(unresolvedDependency, ideaModule.getContentRoot())
            fileDependencies.add(SingleEntryModuleLibrary(toPath(ideaModule, unresolvedFile), scope))
            unresolvedDependencies.put(unresolvedDependency.getAttempted(), unresolvedDependency)
        }

        val dependencies: MutableCollection<Dependency?>
            /*
                     * This method returns the dependencies in buckets (projects first, then modules, then files),
                     * because that's what we used to do since 1.0. It would be better to return the dependencies
                     * in the same order as they come from the resolver, but we'll need to change all the tests for
                     * that, so defer that until later.
                     */
            get() {
                val dependencies: MutableCollection<Dependency?> = LinkedHashSet<Dependency?>()
                dependencies.addAll(projectDependencies)
                dependencies.addAll(moduleDependencies)
                dependencies.addAll(fileDependencies)
                return dependencies
            }
    }

    companion object {
        const val SCOPE_PLUS: String = "plus"
        const val SCOPE_MINUS: String = "minus"
    }
}
