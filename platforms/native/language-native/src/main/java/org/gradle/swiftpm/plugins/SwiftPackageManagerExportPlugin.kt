/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.swiftpm.plugins

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ExactVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.SubVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionRangeSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.language.cpp.CppApplication
import org.gradle.language.cpp.CppLibrary
import org.gradle.language.swift.SwiftApplication
import org.gradle.language.swift.SwiftLibrary
import org.gradle.language.swift.SwiftVersion
import org.gradle.nativeplatform.Linkage
import org.gradle.swiftpm.Package
import org.gradle.swiftpm.internal.AbstractProduct
import org.gradle.swiftpm.internal.BranchDependency
import org.gradle.swiftpm.internal.DefaultExecutableProduct
import org.gradle.swiftpm.internal.DefaultLibraryProduct
import org.gradle.swiftpm.internal.DefaultPackage
import org.gradle.swiftpm.internal.DefaultTarget
import org.gradle.swiftpm.internal.Dependency
import org.gradle.swiftpm.internal.SwiftPmTarget
import org.gradle.swiftpm.internal.VersionDependency
import org.gradle.swiftpm.tasks.GenerateSwiftPackageManagerManifest
import org.gradle.vcs.git.GitVersionControlSpec
import org.gradle.vcs.internal.VcsResolver
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * A plugin that produces a Swift Package Manager manifests from the Gradle model.
 *
 *
 * This plugin should only be applied to the root project of a build.
 *
 * @since 4.6
 */
abstract class SwiftPackageManagerExportPlugin @Inject constructor(
    private val vcsResolver: VcsResolver,
    private val versionSelectorScheme: VersionSelectorScheme,
    private val publicationResolver: ProjectDependencyPublicationResolver,
    private val versionParser: VersionParser
) : Plugin<Project?> {
    override fun apply(project: Project) {
        @Suppress("deprecation") val manifestTask = project.getTasks().create<GenerateSwiftPackageManagerManifest>("generateSwiftPmManifest", GenerateSwiftPackageManagerManifest::class.java)
        manifestTask.getManifestFile().set(project.getLayout().getProjectDirectory().file("Package.swift"))

        // Defer attaching the model until all components have been (most likely) configured
        // TODO - make this relationship explicit to make this more reliable and offer better diagnostics
        project.afterEvaluate(object : Action<Project?> {
            override fun execute(project: Project) {
                val products = project.getProviders().provider<Package?>(MemoizingCallable(SwiftPackageManagerExportPlugin.PackageFactory(project)))
                manifestTask.getPackage().set(products)
            }
        })
    }

    private class MemoizingCallable(private var delegate: Callable<Package?>?) : Callable<Package?> {
        private var result: Package? = null

        @Throws(Exception::class)
        override fun call(): Package? {
            if (result == null) {
                result = delegate!!.call()
                delegate = null
            }
            return result
        }
    }

    private inner class PackageFactory(private val project: Project) : Callable<Package?> {
        override fun call(): Package {
            val products: MutableSet<AbstractProduct?> = LinkedHashSet<AbstractProduct?>()
            val targets: MutableList<DefaultTarget?> = ArrayList<DefaultTarget?>()
            val dependencies: MutableList<Dependency?> = ArrayList<Dependency?>()
            var swiftLanguageVersion: SwiftVersion? = null
            for (p in project.getAllprojects()) {
                for (application in p.getComponents().withType<CppApplication?>(CppApplication::class.java)) {
                    val target = DefaultTarget(application!!.getBaseName().get(), p.getProjectDir(), application.getCppSource())
                    collectDependencies(application.getImplementationDependencies(), dependencies, target)
                    val product = DefaultExecutableProduct(p.getName(), target)
                    // TODO - set header dir for applications
                    products.add(product)
                    targets.add(target)
                }
                for (library in p.getComponents().withType<CppLibrary?>(CppLibrary::class.java)) {
                    val target = DefaultTarget(library!!.getBaseName().get(), p.getProjectDir(), library.getCppSource())
                    collectDependencies(library.getImplementationDependencies(), dependencies, target)
                    val headerDirs: MutableSet<File?> = library.getPublicHeaderDirs().getFiles()
                    if (!headerDirs.isEmpty()) {
                        // TODO - deal with more than one directory
                        target.setPublicHeaderDir(headerDirs.iterator().next())
                    }
                    targets.add(target)

                    if (library.getLinkage().get().contains(Linkage.SHARED)) {
                        products.add(DefaultLibraryProduct(p.getName(), target, Linkage.SHARED))
                    }
                    if (library.getLinkage().get().contains(Linkage.STATIC)) {
                        products.add(DefaultLibraryProduct(p.getName() + "Static", target, Linkage.STATIC))
                    }
                }
                for (application in p.getComponents().withType<SwiftApplication?>(SwiftApplication::class.java)) {
                    val target = DefaultTarget(application!!.getModule().get(), p.getProjectDir(), application.getSwiftSource())
                    swiftLanguageVersion = max(swiftLanguageVersion, application.getSourceCompatibility().getOrNull())
                    collectDependencies(application.getImplementationDependencies(), dependencies, target)
                    val product = DefaultExecutableProduct(p.getName(), target)
                    products.add(product)
                    targets.add(target)
                }
                for (library in p.getComponents().withType<SwiftLibrary?>(SwiftLibrary::class.java)) {
                    val target = DefaultTarget(library!!.getModule().get(), p.getProjectDir(), library.getSwiftSource())
                    swiftLanguageVersion = max(swiftLanguageVersion, library.getSourceCompatibility().getOrNull())
                    collectDependencies(library.getImplementationDependencies(), dependencies, target)
                    targets.add(target)

                    if (library.getLinkage().get().contains(Linkage.SHARED)) {
                        products.add(DefaultLibraryProduct(p.getName(), target, Linkage.SHARED))
                    }
                    if (library.getLinkage().get().contains(Linkage.STATIC)) {
                        products.add(DefaultLibraryProduct(p.getName() + "Static", target, Linkage.STATIC))
                    }
                }
            }
            return DefaultPackage(products, targets, dependencies, swiftLanguageVersion)
        }

        fun max(v1: SwiftVersion?, v2: SwiftVersion?): SwiftVersion? {
            if (v1 == null) {
                return v2
            }
            if (v2 == null) {
                return v1
            }
            if (v1.compareTo(v2) > 0) {
                return v1
            }
            return v2
        }

        fun collectDependencies(configuration: Configuration, dependencies: MutableCollection<Dependency?>, target: DefaultTarget) {
            for (dependency in configuration.getAllDependencies()) {
                if (dependency is ProjectDependency) {
                    val projectDependency = dependency
                    val identityPath = (projectDependency as ProjectDependencyInternal).getTargetProjectIdentity().getBuildTreePath()
                    val identifier = publicationResolver.resolveComponent<SwiftPmTarget>(SwiftPmTarget::class.java, identityPath)
                    target.getRequiredTargets().add(identifier.getTargetName())
                } else if (dependency is ExternalModuleDependency) {
                    val externalDependency = dependency
                    val depSelector = DefaultModuleComponentSelector.newSelector(externalDependency.getModule(), externalDependency.getVersionConstraint())
                    val vcsSpec = vcsResolver.locateVcsFor(depSelector)
                    if (vcsSpec == null || vcsSpec !is GitVersionControlSpec) {
                        throw InvalidUserDataException(String.format("Cannot determine the Git URL for dependency on %s:%s.", dependency.getGroup(), dependency.getName()))
                    }
                    val gitSpec = vcsSpec
                    dependencies.add(toSwiftPmDependency(externalDependency, gitSpec))
                    target.getRequiredProducts().add(externalDependency.getName())
                } else {
                    throw InvalidUserDataException(String.format("Cannot map a dependency of type %s (%s)", dependency.javaClass.getSimpleName(), dependency))
                }
            }
        }

        fun toSwiftPmDependency(externalDependency: ExternalModuleDependency, gitSpec: GitVersionControlSpec): Dependency {
            if (externalDependency.getVersionConstraint().getBranch() != null) {
                if (externalDependency.getVersion() != null) {
                    throw InvalidUserDataException(
                        String.format(
                            "Cannot map a dependency on %s:%s that defines both a branch (%s) and a version constraint (%s).",
                            externalDependency.getGroup(),
                            externalDependency.getName(),
                            externalDependency.getVersionConstraint().getBranch(),
                            externalDependency.getVersion()
                        )
                    )
                }
                return BranchDependency(gitSpec.getUrl(), externalDependency.getVersionConstraint().getBranch())
            }

            val versionSelectorString = externalDependency.getVersion()
            val versionSelector = versionSelectorScheme.parseSelector(versionSelectorString)
            if (versionSelector is LatestVersionSelector) {
                val latestVersionSelector = versionSelector
                if (latestVersionSelector.getSelectorStatus() == "integration") {
                    return BranchDependency(gitSpec.getUrl(), "master")
                }
            } else if (versionSelector is ExactVersionSelector) {
                return VersionDependency(gitSpec.getUrl(), versionSelector.getSelector())
            } else if (versionSelector is VersionRangeSelector) {
                val versionRangeSelector = versionSelector
                if (versionRangeSelector.isLowerInclusive()) {
                    return VersionDependency(gitSpec.getUrl(), versionRangeSelector.getLowerBound(), versionRangeSelector.getUpperBound(), versionRangeSelector.isUpperInclusive())
                }
            } else if (versionSelector is SubVersionSelector) {
                val subVersionSelector = versionSelector
                val prefix = subVersionSelector.getPrefix()
                // TODO - take care of this in the selector parser
                if (prefix.endsWith(".")) {
                    val versionString = prefix.substring(0, prefix.length - 1)
                    val version = versionParser.transform(versionString)
                    if (version!!.getNumericParts().size == 1) {
                        val part1 = version.getNumericParts()[0]
                        return VersionDependency(gitSpec.getUrl(), part1.toString() + ".0.0")
                    }
                    if (version.getNumericParts().size == 2) {
                        val part1 = version.getNumericParts()[0]
                        val part2 = version.getNumericParts()[1]
                        return VersionDependency(gitSpec.getUrl(), part1.toString() + "." + part2 + ".0", part1.toString() + "." + (part2 + 1) + ".0", false)
                    }
                }
            }
            throw InvalidUserDataException(
                String.format(
                    "Cannot map a dependency on %s:%s with version constraint (%s).",
                    externalDependency.getGroup(),
                    externalDependency.getName(),
                    externalDependency.getVersion()
                )
            )
        }
    }
}
