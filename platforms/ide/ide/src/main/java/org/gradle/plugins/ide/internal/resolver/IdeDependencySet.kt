/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugins.ide.internal.resolver

import com.google.common.collect.HashBasedTable
import com.google.common.collect.Iterables
import com.google.common.collect.MultimapBuilder
import com.google.common.collect.Table
import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.result.ArtifactResolutionResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.component.Artifact
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.internal.jvm.JavaModuleDetector
import org.gradle.jvm.JvmLibrary
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.language.java.artifact.JavadocArtifact
import java.io.File

/**
 * Adapts Gradle's dependency resolution engine to the special needs of the IDE plugins.
 * Allows adding and subtracting [Configuration]s, working in offline mode and downloading sources/javadoc.
 */
class IdeDependencySet(
    private val dependencyHandler: DependencyHandler,
    javaModuleDetector: JavaModuleDetector,
    plusConfigurations: MutableCollection<Configuration>,
    minusConfigurations: MutableCollection<Configuration>,
    inferModulePath: Boolean,
    gradleApiSourcesResolver: GradleApiSourcesResolver,
    testConfigurations: MutableCollection<Configuration?>
) {
    private val javaModuleDetector: JavaModuleDetector
    private val plusConfigurations: MutableCollection<Configuration>
    private val minusConfigurations: MutableCollection<Configuration>
    private val inferModulePath: Boolean
    private val gradleApiSourcesResolver: GradleApiSourcesResolver
    private val testConfigurations: MutableCollection<Configuration?>

    constructor(
        dependencyHandler: DependencyHandler,
        javaModuleDetector: JavaModuleDetector,
        plusConfigurations: MutableCollection<Configuration>,
        minusConfigurations: MutableCollection<Configuration>,
        inferModulePath: Boolean,
        gradleApiSourcesResolver: GradleApiSourcesResolver
    ) : this(dependencyHandler, javaModuleDetector, plusConfigurations, minusConfigurations, inferModulePath, gradleApiSourcesResolver, mutableSetOf<Configuration?>())

    fun visit(visitor: IdeDependencyVisitor) {
        if (plusConfigurations.isEmpty()) {
            return
        }
        IdeDependencySet.IdeDependencyResult().visit(visitor)
    }

    /*
     * Tries to minimize the number of requests to the resolution engine by batching up requests
     * for sources/javadoc.
     *
     * There is still some inefficiency because the ArtifactCollection interface does not provide
     * detailed failure results, so we have to fall back to the more expensive ResolutionResult API.
     * We should fix this, as other IDE vendors will face the same problem.
     */
    private inner class IdeDependencyResult {
        private val resolvedArtifacts: MutableMap<ComponentArtifactIdentifier, ResolvedArtifactResult> = LinkedHashMap<ComponentArtifactIdentifier, ResolvedArtifactResult>()
        private val configurations = MultimapBuilder.hashKeys().linkedHashSetValues().build<ComponentArtifactIdentifier?, Configuration?>()
        private val unresolvedDependencies: MutableMap<ComponentSelector?, UnresolvedDependencyResult?> = LinkedHashMap<ComponentSelector?, UnresolvedDependencyResult?>()
        private val auxiliaryArtifacts: Table<ModuleComponentIdentifier?, Class<out Artifact?>?, MutableSet<ResolvedArtifactResult?>?> =
            HashBasedTable.create<ModuleComponentIdentifier?, Class<out Artifact?>?, MutableSet<ResolvedArtifactResult?>?>()

        fun visit(visitor: IdeDependencyVisitor) {
            resolvePlusConfigurations(visitor)
            resolveMinusConfigurations(visitor)
            resolveAuxiliaryArtifacts(visitor)
            visitArtifacts(visitor)
            visitUnresolvedDependencies(visitor)
        }

        fun resolvePlusConfigurations(visitor: IdeDependencyVisitor) {
            for (configuration in plusConfigurations) {
                val artifacts = getResolvedArtifacts(configuration, visitor)
                for (resolvedArtifact in artifacts) {
                    resolvedArtifacts.put(resolvedArtifact.getId(), resolvedArtifact)
                    configurations.put(resolvedArtifact.getId(), configuration)
                }
                if (artifacts.getFailures().isEmpty()) {
                    continue
                }
                for (unresolvedDependency in getUnresolvedDependencies(configuration, visitor)) {
                    unresolvedDependencies.put(unresolvedDependency.getAttempted(), unresolvedDependency)
                }
            }
        }

        fun resolveMinusConfigurations(visitor: IdeDependencyVisitor) {
            for (configuration in minusConfigurations) {
                val artifacts = getResolvedArtifacts(configuration, visitor)
                for (resolvedArtifact in artifacts) {
                    resolvedArtifacts.remove(resolvedArtifact.getId())
                    configurations.removeAll(resolvedArtifact.getId())
                }
                if (artifacts.getFailures().isEmpty()) {
                    continue
                }
                for (unresolvedDependency in getUnresolvedDependencies(configuration, visitor)) {
                    unresolvedDependencies.remove(unresolvedDependency.getAttempted())
                }
            }
        }

        fun getResolvedArtifacts(configuration: Configuration, visitor: IdeDependencyVisitor): ArtifactCollection {
            return configuration.getIncoming().artifactView(object : Action<ArtifactView.ViewConfiguration?> {
                override fun execute(viewConfiguration: ArtifactView.ViewConfiguration) {
                    viewConfiguration.lenient(true)
                    viewConfiguration.componentFilter(getComponentFilter(visitor)!!)
                }
            }).getArtifacts()
        }

        fun getComponentFilter(visitor: IdeDependencyVisitor): Spec<ComponentIdentifier?>? {
            return if (visitor.isOffline()) NOT_A_MODULE else Specs.satisfyAll<ComponentIdentifier?>()
        }

        fun getUnresolvedDependencies(configuration: Configuration, visitor: IdeDependencyVisitor): Iterable<UnresolvedDependencyResult> {
            if (visitor.isOffline()) {
                return mutableSetOf<UnresolvedDependencyResult?>()
            }
            return Iterables.filter<UnresolvedDependencyResult?>(configuration.getIncoming().getResolutionResult().getRoot().getDependencies(), UnresolvedDependencyResult::class.java)
        }

        fun resolveAuxiliaryArtifacts(visitor: IdeDependencyVisitor) {
            if (visitor.isOffline()) {
                return
            }

            val componentIdentifiers = this.moduleComponentIdentifiers
            if (componentIdentifiers.isEmpty()) {
                return
            }

            val types = getAuxiliaryArtifactTypes(visitor)
            if (types.isEmpty()) {
                return
            }

            val result: ArtifactResolutionResult = dependencyHandler.createArtifactResolutionQuery()
                .forComponents(componentIdentifiers)
                .withArtifacts(JvmLibrary::class.java, types)
                .execute()

            for (artifactsResult in result.getResolvedComponents()) {
                for (type in types) {
                    val resolvedArtifactResults: MutableSet<ResolvedArtifactResult?> = LinkedHashSet<ResolvedArtifactResult?>()

                    for (artifactResult in artifactsResult.getArtifacts(type)) {
                        if (artifactResult is ResolvedArtifactResult) {
                            resolvedArtifactResults.add(artifactResult)
                        }
                    }
                    auxiliaryArtifacts.put(artifactsResult.getId() as ModuleComponentIdentifier, type, resolvedArtifactResults)
                }
            }
        }

        val moduleComponentIdentifiers: MutableSet<ModuleComponentIdentifier?>
            get() {
                val componentIdentifiers: MutableSet<ModuleComponentIdentifier?> =
                    LinkedHashSet<ModuleComponentIdentifier?>()
                for (identifier in resolvedArtifacts.keys) {
                    val componentIdentifier = identifier.getComponentIdentifier()
                    if (componentIdentifier is ModuleComponentIdentifier) {
                        componentIdentifiers.add(componentIdentifier)
                    }
                }
                return componentIdentifiers
            }

        fun getAuxiliaryArtifactTypes(visitor: IdeDependencyVisitor): MutableList<Class<out Artifact?>> {
            val types: MutableList<Class<out Artifact?>> = ArrayList<Class<out Artifact?>>(2)
            if (visitor.downloadSources()) {
                types.add(SourcesArtifact::class.java)
            }
            if (visitor.downloadJavaDoc()) {
                types.add(JavadocArtifact::class.java)
            }
            return types
        }

        fun visitArtifacts(visitor: IdeDependencyVisitor) {
            for (artifact in resolvedArtifacts.values) {
                val componentIdentifier = artifact.getId().getComponentIdentifier()
                val testOnly = isTestConfiguration(configurations.get(artifact.getId()))
                val asModule = isModule(testOnly, artifact.getFile())
                if (componentIdentifier is ProjectComponentIdentifier) {
                    visitor.visitProjectDependency(artifact, testOnly, asModule)
                } else {
                    if (componentIdentifier is ModuleComponentIdentifier) {
                        var sources = auxiliaryArtifacts.get(componentIdentifier, SourcesArtifact::class.java)
                        sources = if (sources != null) sources else mutableSetOf<ResolvedArtifactResult?>()
                        var javaDoc = auxiliaryArtifacts.get(componentIdentifier, JavadocArtifact::class.java)
                        javaDoc = if (javaDoc != null) javaDoc else mutableSetOf<ResolvedArtifactResult?>()
                        visitor.visitModuleDependency(artifact, sources, javaDoc, testOnly, asModule)
                    } else if (isLocalGroovyDependency(artifact)) {
                        val localGroovySources = if (shouldDownloadSources(visitor)) gradleApiSourcesResolver.resolveLocalGroovySources(artifact.getFile().getName()) else null
                        visitor.visitGradleApiDependency(artifact, localGroovySources, testOnly)
                    } else {
                        visitor.visitFileDependency(artifact, testOnly)
                    }
                }
            }
        }

        fun isModule(testOnly: Boolean, artifact: File?): Boolean {
            // Test code is not treated as modules, as Eclipse does not support compiling two modules in one project anyway.
            // See also: https://bugs.eclipse.org/bugs/show_bug.cgi?id=520667
            //
            // We assume that a test-only dependency is not a module, which corresponds to how Eclipse does test running for modules:
            // It patches the main module with the tests and expects test dependencies to be part of the unnamed module (classpath).
            return javaModuleDetector.isModule(inferModulePath && !testOnly, artifact)
        }

        fun isLocalGroovyDependency(artifact: ResolvedArtifactResult): Boolean {
            val artifactFileName = artifact.getFile().getName()
            val componentIdentifier = artifact.getId().getComponentIdentifier().getDisplayName()
            return (componentIdentifier == DependencyFactoryInternal.ClassPathNotation.GRADLE_API.displayName
                    || componentIdentifier == DependencyFactoryInternal.ClassPathNotation.GRADLE_TEST_KIT.displayName
                    || componentIdentifier == DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY.displayName)
                    && artifactFileName.startsWith("groovy-")
        }

        fun shouldDownloadSources(visitor: IdeDependencyVisitor): Boolean {
            return !visitor.isOffline() && visitor.downloadSources()
        }

        fun isTestConfiguration(configurations: MutableSet<Configuration?>): Boolean {
            return testConfigurations.containsAll(configurations)
        }

        fun visitUnresolvedDependencies(visitor: IdeDependencyVisitor) {
            for (unresolvedDependency in unresolvedDependencies.values) {
                visitor.visitUnresolvedDependency(unresolvedDependency)
            }
        }
    }

    init {
        this.javaModuleDetector = javaModuleDetector
        this.plusConfigurations = plusConfigurations
        this.minusConfigurations = minusConfigurations
        this.inferModulePath = inferModulePath
        this.gradleApiSourcesResolver = gradleApiSourcesResolver
        this.testConfigurations = testConfigurations
    }

    companion object {
        private val NOT_A_MODULE: Spec<ComponentIdentifier?> = object : Spec<ComponentIdentifier?> {
            override fun isSatisfiedBy(id: ComponentIdentifier?): Boolean {
                return id !is ModuleComponentIdentifier
            }
        }
    }
}
