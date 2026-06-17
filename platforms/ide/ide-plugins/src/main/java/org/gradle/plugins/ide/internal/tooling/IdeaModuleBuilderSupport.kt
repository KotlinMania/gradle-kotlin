/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.JavaVersion
import org.gradle.plugins.ide.idea.model.Dependency
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.ModuleDependency
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaCompilerOutput
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaContentRoot
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaDependency
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaDependencyScope
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaModuleDependency
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaSingleEntryLibraryDependency
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaSourceDirectory
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleModuleVersion
import org.jspecify.annotations.NullMarked
import java.io.File
import java.util.LinkedList

@NullMarked
object IdeaModuleBuilderSupport {
    fun convertToJavaVersion(ideaLanguageLevel: IdeaLanguageLevel?): JavaVersion? {
        if (ideaLanguageLevel == null) {
            return null
        }
        val languageLevel = ideaLanguageLevel.level
        return JavaVersion.valueOf(languageLevel!!.replaceFirst("JDK".toRegex(), "VERSION"))
    }

    fun buildContentRoot(ideaModule: IdeaModule): DefaultIdeaContentRoot {
        return DefaultIdeaContentRoot()
            .setRootDirectory(ideaModule.getContentRoot())
            .setSourceDirectories(buildSourceDirectories(ideaModule.getSourceDirs(), ideaModule.getGeneratedSourceDirs()))
            .setTestDirectories(buildSourceDirectories(ideaModule.getTestSources().getFiles(), ideaModule.getGeneratedSourceDirs()))
            .setResourceDirectories(buildSourceDirectories(ideaModule.getResourceDirs(), ideaModule.getGeneratedSourceDirs()))
            .setTestResourceDirectories(buildSourceDirectories(ideaModule.getTestResources().getFiles(), ideaModule.getGeneratedSourceDirs()))
            .setExcludeDirectories(ideaModule.getExcludeDirs())
    }

    private fun buildSourceDirectories(sourceDirs: MutableSet<File>, generatedSourceDirs: MutableSet<File>): MutableSet<DefaultIdeaSourceDirectory> {
        val out: MutableSet<DefaultIdeaSourceDirectory> = LinkedHashSet<DefaultIdeaSourceDirectory>()
        for (s in sourceDirs) {
            val sourceDirectory = DefaultIdeaSourceDirectory().setDirectory(s)
            if (generatedSourceDirs.contains(s)) {
                sourceDirectory.setGenerated(true)
            }
            out.add(sourceDirectory)
        }
        return out
    }

    fun buildCompilerOutput(ideaModule: IdeaModule): DefaultIdeaCompilerOutput {
        return DefaultIdeaCompilerOutput()
            .setInheritOutputDirs(
                if (ideaModule.getInheritOutputDirs() != null)
                    ideaModule.getInheritOutputDirs()
                else
                    false
            )
            .setOutputDir(ideaModule.getOutputDir())
            .setTestOutputDir(ideaModule.getTestOutputDir())
    }

    fun buildDependencies(resolvedDependencies: MutableSet<Dependency>): MutableList<DefaultIdeaDependency> {
        val dependencies: MutableList<DefaultIdeaDependency> = LinkedList<DefaultIdeaDependency>()
        for (dependency in resolvedDependencies) {
            if (dependency is SingleEntryModuleLibrary) {
                val d = dependency
                dependencies.add(ideaSingleEntryLibraryDependencyFor(d))
            } else if (dependency is ModuleDependency) {
                val d = dependency
                dependencies.add(ideaModuleDependencyFor(d))
            }
        }
        return dependencies
    }

    private fun ideaSingleEntryLibraryDependencyFor(d: SingleEntryModuleLibrary): DefaultIdeaSingleEntryLibraryDependency {
        val defaultDependency = DefaultIdeaSingleEntryLibraryDependency()
            .setFile(d.libraryFile)
            .setSource(d.sourceFile)
            .setJavadoc(d.javadocFile)
            .setScope(DefaultIdeaDependencyScope(d.getScope()))
            .setExported(d.isExported)

        val moduleVersionId = d.moduleVersion
        if (moduleVersionId != null) {
            defaultDependency.setGradleModuleVersion(DefaultGradleModuleVersion(moduleVersionId.getGroup(), moduleVersionId.getName(), moduleVersionId.getVersion()))
        }
        return defaultDependency
    }

    private fun ideaModuleDependencyFor(d: ModuleDependency): DefaultIdeaModuleDependency {
        return DefaultIdeaModuleDependency(d.name)
            .setExported(d.isExported)
            .setScope(DefaultIdeaDependencyScope(d.getScope()))
    }
}
