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

import com.google.common.collect.ImmutableMap
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.WarPlugin
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.jvm.JavaModuleDetector
import org.gradle.plugins.ear.EarPlugin
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry
import org.gradle.plugins.ide.eclipse.model.AbstractLibrary
import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.eclipse.model.EclipseWtp
import org.gradle.plugins.ide.eclipse.model.ProjectDependency
import org.gradle.plugins.ide.internal.resolver.IdeDependencySet
import org.gradle.plugins.ide.internal.resolver.IdeDependencyVisitor
import org.gradle.plugins.ide.internal.resolver.NullGradleApiSourcesResolver
import java.io.File

class WtpClasspathAttributeSupport @Suppress("deprecation") constructor(project: Project, model: EclipseModel) {
    private val libDirName: String
    private val isUtilityProject: Boolean
    private val rootConfigFiles: MutableSet<File?>
    private val libConfigFiles: MutableSet<File?>

    init {
        isUtilityProject = !project.getPlugins().hasPlugin(WarPlugin::class.java) && !project.getPlugins().hasPlugin(EarPlugin::class.java)
        val eclipseWtp = DeprecationLogger.whileDisabled<EclipseWtp?>(org.gradle.internal.Factory { model.getWtp() })
        val wtpComponent = eclipseWtp!!.getComponent()
        libDirName = wtpComponent.getLibDeployPath()
        val rootConfigs = wtpComponent.getRootConfigurations()
        val libConfigs = wtpComponent.getLibConfigurations()
        val minusConfigs = wtpComponent.getMinusConfigurations()
        rootConfigFiles = collectFilesFromConfigs(model.getClasspath(), rootConfigs, minusConfigs)
        libConfigFiles = collectFilesFromConfigs(model.getClasspath(), libConfigs, minusConfigs)
    }

    fun enhance(classpath: Classpath) {
        for (entry in classpath.getEntries()) {
            if (entry is AbstractClasspathEntry) {
                val classpathEntry = entry
                val wtpEntries = createDeploymentAttribute(classpathEntry)
                classpathEntry.getEntryAttributes().putAll(wtpEntries)
            }
        }
    }

    private fun createDeploymentAttribute(entry: ClasspathEntry?): MutableMap<String?, Any?> {
        if (entry is AbstractLibrary) {
            return createDeploymentAttribute(entry)
        } else if (entry is ProjectDependency) {
            return ImmutableMap.of<String?, Any?>(AbstractClasspathEntry.Companion.COMPONENT_NON_DEPENDENCY_ATTRIBUTE, "")
        } else {
            return mutableMapOf<String?, Any?>()
        }
    }

    private fun createDeploymentAttribute(entry: AbstractLibrary): MutableMap<String?, Any?> {
        val file = entry.getLibrary().getFile()
        if (!isUtilityProject) {
            if (rootConfigFiles.contains(file)) {
                return ImmutableMap.of<String?, Any?>(AbstractClasspathEntry.Companion.COMPONENT_DEPENDENCY_ATTRIBUTE, "/")
            } else if (libConfigFiles.contains(file)) {
                return ImmutableMap.of<String?, Any?>(AbstractClasspathEntry.Companion.COMPONENT_DEPENDENCY_ATTRIBUTE, libDirName)
            }
        }
        return ImmutableMap.of<String?, Any?>(AbstractClasspathEntry.Companion.COMPONENT_NON_DEPENDENCY_ATTRIBUTE, "")
    }

    private class WtpClasspathAttributeDependencyVisitor(private val classpath: EclipseClasspath) : IdeDependencyVisitor {
        val files: MutableSet<File?> = LinkedHashSet<File?>()

        override fun isOffline(): Boolean {
            return classpath.isProjectDependenciesOnly()
        }

        override fun downloadSources(): Boolean {
            return false
        }

        override fun downloadJavaDoc(): Boolean {
            return false
        }

        override fun visitUnresolvedDependency(unresolvedDependency: UnresolvedDependencyResult?) {
            //already handled elsewhere
        }

        override fun visitProjectDependency(artifact: ResolvedArtifactResult?, testDependency: Boolean, asJavaModule: Boolean) {
        }

        override fun visitModuleDependency(
            artifact: ResolvedArtifactResult,
            sources: MutableSet<ResolvedArtifactResult?>?,
            javaDoc: MutableSet<ResolvedArtifactResult?>?,
            testDependency: Boolean,
            asJavaModule: Boolean
        ) {
            files.add(artifact.getFile())
        }

        override fun visitFileDependency(artifact: ResolvedArtifactResult, testDependency: Boolean) {
            files.add(artifact.getFile())
        }

        override fun visitGradleApiDependency(artifact: ResolvedArtifactResult, sources: File?, testDependency: Boolean) {
            files.add(artifact.getFile())
        }
    }

    companion object {
        private fun collectFilesFromConfigs(classpath: EclipseClasspath, configs: MutableSet<Configuration?>?, minusConfigs: MutableSet<Configuration?>?): MutableSet<File?> {
            val visitor = WtpClasspathAttributeDependencyVisitor(classpath)
            IdeDependencySet(
                classpath.getProject().getDependencies(), (classpath.getProject() as ProjectInternal).getServices().get<JavaModuleDetector?>(JavaModuleDetector::class.java),
                configs, minusConfigs, false, NullGradleApiSourcesResolver.Companion.INSTANCE, classpath.getTestConfigurations().getOrElse(mutableSetOf<Configuration?>())
            ).visit(visitor)
            return visitor.files
        }
    }
}
