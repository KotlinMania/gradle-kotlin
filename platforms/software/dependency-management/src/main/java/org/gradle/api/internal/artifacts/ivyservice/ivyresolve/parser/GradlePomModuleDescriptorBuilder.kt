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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.PomDependencyMgt
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.descriptor.MavenScope
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor
import org.gradle.internal.component.external.model.maven.MavenDependencyType
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.IvyArtifactName

/**
 * This file was originally a copy of org.apache.ivy.plugins.parser.m2.PomModuleDescriptorBuilder, but has since been significantly modified.
 */
class GradlePomModuleDescriptorBuilder(
    private val pomReader: PomReader,
    private val defaultVersionSelectorScheme: VersionSelectorScheme,
    private val mavenVersionSelectorScheme: VersionSelectorScheme
) {
    val dependencies: MutableList<MavenDependencyDescriptor?> = ArrayList<MavenDependencyDescriptor?>()
    var status: String? = null
        private set
    private var componentIdentifier: ModuleComponentIdentifier? = null

    fun getComponentIdentifier(): ModuleComponentIdentifier {
        return componentIdentifier!!
    }

    fun setModuleRevId(group: String?, module: String?, version: String?) {
        val effectiveVersion = MavenVersionUtils.toEffectiveVersion(version)
        status = MavenVersionUtils.inferStatusFromEffectiveVersion(version)
        componentIdentifier = newId(DefaultModuleIdentifier.newId(group, module!!), effectiveVersion)
    }

    fun addDependency(dep: PomReader.PomDependencyData) {
        val type = if (dep.isOptional) MavenDependencyType.OPTIONAL_DEPENDENCY else MavenDependencyType.DEPENDENCY
        doAddDependency(dep, type)
    }

    fun addConstraint(dep: PomDependencyMgt) {
        doAddDependency(dep, MavenDependencyType.DEPENDENCY_MANAGEMENT)
    }

    private fun doAddDependency(dep: PomDependencyMgt, dependencyType: MavenDependencyType) {
        val scope: MavenScope
        if (dependencyType == MavenDependencyType.DEPENDENCY_MANAGEMENT) {
            scope = MavenScope.Compile
        } else {
            var scopeString = dep.scope
            if (scopeString == null || scopeString.length == 0) {
                scopeString = getDefaultScope(dep)
            }

            // unknown scope, defaulting to 'compile'
            scope = SCOPES.getOrDefault(scopeString, MavenScope.Compile)
        }

        val version = determineVersion(dep)
        val mappedVersion = convertVersionFromMavenSyntax(version)
        val selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(dep.groupId, dep.artifactId), DefaultImmutableVersionConstraint(mappedVersion))

        // Some POMs depend on themselves, don't add this dependency: Ivy doesn't allow this!
        // Example: http://repo2.maven.org/maven2/net/jini/jsk-platform/2.1/jsk-platform-2.1.pom
        if (selector.getModuleIdentifier() == componentIdentifier!!.getModuleIdentifier()) {
            return
        }

        var dependencyArtifact: IvyArtifactName? = null
        val hasClassifier = dep.classifier != null && dep.classifier.length > 0
        val hasNonJarType = dep.type != null && "jar" != dep.type
        if (hasClassifier || hasNonJarType) {
            var type = "jar"
            if (dep.type != null) {
                type = dep.type
            }
            val ext = determineExtension(type)
            val classifier = if (hasClassifier) dep.classifier else getClassifierForType(type)

            dependencyArtifact = DefaultIvyArtifactName(selector.getModule(), type, ext, classifier)
        }

        // experimentation shows the following, excluded modules are
        // inherited from parent POMs if either of the following is true:
        // the <exclusions> element is missing or the <exclusions> element
        // is present, but empty.
        val excludes: MutableList<ExcludeMetadata?> = ArrayList<ExcludeMetadata?>()
        var excluded = dep.excludedModules
        if (excluded.isEmpty()) {
            excluded = getDependencyMgtExclusions(dep)
        }
        for (excludedModule in excluded) {
            val rule = DefaultExclude(excludedModule)
            excludes.add(rule)
        }

        dependencies.add(MavenDependencyDescriptor(scope, dependencyType, selector, dependencyArtifact, excludes))
    }

    private fun convertVersionFromMavenSyntax(version: String?): String {
        val versionSelector = mavenVersionSelectorScheme.parseSelector(version)
        return defaultVersionSelectorScheme.renderSelector(versionSelector)!!
    }

    /**
     * Determines extension of dependency.
     *
     * @param type Type
     * @return Extension
     */
    private fun determineExtension(type: String?): String? {
        return if (JarDependencyType.Companion.isJarExtension(type)) "jar" else type
    }

    /**
     * Handles special types of dependencies. If one of the following types matches, a specific type of classifier is set.
     *
     * - test-jar (see [Maven documentation](http://maven.apache.org/guides/mini/guide-attached-tests.html))
     * - ejb-client (see [Maven documentation](http://maven.apache.org/plugins/maven-ejb-plugin/examples/ejb-client-dependency.html))
     *
     * @param type Type
     */
    private fun getClassifierForType(type: String?): String? {
        if (JarDependencyType.TEST_JAR.name == type) {
            return "tests"
        } else if (JarDependencyType.EJB_CLIENT.name == type) {
            return "client"
        }
        return null
    }

    private enum class JarDependencyType(name: String) {
        TEST_JAR("test-jar"), EJB_CLIENT("ejb-client"), EJB("ejb"), BUNDLE("bundle"), MAVEN_PLUGIN("maven-plugin"), ECLIPSE_PLUGIN("eclipse-plugin");

        val name: String?

        init {
            this.name = name
        }

        companion object {
            private val TYPES: MutableMap<String?, JarDependencyType?>

            init {
                TYPES = HashMap<String?, JarDependencyType?>()

                for (type in entries) {
                    TYPES.put(type.name, type)
                }
            }

            fun isJarExtension(type: String?): Boolean {
                return TYPES.containsKey(type)
            }
        }
    }

    /**
     * Determines the version of a dependency. Uses the specified version if declared for the as coordinate. If the version is not declared, try to resolve it from the dependency management section.
     * In case the version cannot be resolved with any of these methods, return the empty version
     *
     * @param dependency Dependency
     * @return Resolved dependency version
     */
    private fun determineVersion(dependency: PomDependencyMgt): String {
        var version = dependency.version
        version = if (version == null || version.length == 0) getDefaultVersion(dependency) else version
        return if (version == null) "" else version
    }

    fun addDependencyForRelocation(selector: ModuleComponentSelector) {
        // Some POMs depend on themselves through their parent POM, don't add this dependency
        // since Ivy doesn't allow this!
        // Example: http://repo2.maven.org/maven2/com/atomikos/atomikos-util/3.6.4/atomikos-util-3.6.4.pom
        if (selector.getGroup() == componentIdentifier!!.getGroup()
            && selector.getModule() == componentIdentifier!!.getModule()
        ) {
            return
        }

        dependencies.add(MavenDependencyDescriptor(MavenScope.Compile, MavenDependencyType.RELOCATION, selector, null, ImmutableList.of<ExcludeMetadata>()))
    }

    private fun getDefaultVersion(dep: PomDependencyMgt): String? {
        val pomDependencyMgt = findDependencyDefault(dep)
        if (pomDependencyMgt != null) {
            return pomDependencyMgt.version
        }
        return null
    }

    private fun getDefaultScope(dep: PomDependencyMgt): String {
        val pomDependencyMgt = findDependencyDefault(dep)
        var result: String? = null
        if (pomDependencyMgt != null) {
            result = pomDependencyMgt.scope
        }
        if ((result == null) || !SCOPES.containsKey(result)) {
            result = "compile"
        }
        return result
    }

    private fun getDependencyMgtExclusions(dep: PomDependencyMgt): MutableList<ModuleIdentifier> {
        val pomDependencyMgt = findDependencyDefault(dep)
        if (pomDependencyMgt != null) {
            return pomDependencyMgt.excludedModules
        }

        return mutableListOf<ModuleIdentifier?>()
    }

    private fun findDependencyDefault(dependency: PomDependencyMgt): PomDependencyMgt? {
        return pomReader.findDependencyDefaults(dependency.id)
    }

    companion object {
        val MAVEN2_CONFIGURATIONS: ImmutableMap<String?, Configuration?> = ImmutableMap.builder<String?, Configuration?>()
            .put("default", Configuration("default", true, true, ImmutableSet.of<String?>("runtime", "master")))
            .put("master", Configuration("master", true, true, ImmutableSet.of<String?>()))
            .put("compile", Configuration("compile", true, true, ImmutableSet.of<String?>()))
            .put("provided", Configuration("provided", true, true, ImmutableSet.of<String?>()))
            .put("runtime", Configuration("runtime", true, true, ImmutableSet.of<String?>("compile")))
            .put("test", Configuration("test", true, false, ImmutableSet.of<String?>("runtime")))
            .put("system", Configuration("system", true, true, ImmutableSet.of<String?>()))
            .put("sources", Configuration("sources", true, true, ImmutableSet.of<String?>()))
            .put("javadoc", Configuration("javadoc", true, true, ImmutableSet.of<String?>()))
            .put("optional", Configuration("optional", true, true, ImmutableSet.of<String?>())).build()

        private val SCOPES: MutableMap<String?, MavenScope> = ImmutableMap.builder<String?, MavenScope?>()
            .put("compile", MavenScope.Compile)
            .put("runtime", MavenScope.Runtime)
            .put("provided", MavenScope.Provided)
            .put("test", MavenScope.Test)
            .put("system", MavenScope.System)
            .build()
    }
}
