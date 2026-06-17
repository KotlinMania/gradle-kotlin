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

import com.google.common.collect.ImmutableList
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry
import org.gradle.plugins.ide.eclipse.model.Container
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath
import org.gradle.plugins.ide.eclipse.model.Output
import org.gradle.plugins.ide.eclipse.model.SourceFolder
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.plugins.ide.internal.resolver.GradleApiSourcesResolver

class ClasspathFactory(classpath: EclipseClasspath, ideArtifactRegistry: IdeArtifactRegistry?, gradleApiSourcesResolver: GradleApiSourcesResolver?, inferModulePath: Boolean) {
    private val classpath: EclipseClasspath?
    private val dependenciesCreator: EclipseDependenciesCreator

    init {
        this.classpath = classpath
        this.dependenciesCreator = EclipseDependenciesCreator(classpath, ideArtifactRegistry, gradleApiSourcesResolver, inferModulePath)
    }

    fun createEntries(): MutableList<ClasspathEntry?> {
        val entries = ImmutableList.builder<ClasspathEntry?>()
        entries.add(createOutput())
        entries.addAll(createSourceFolders()!!)
        entries.addAll(createContainers())
        entries.addAll(createDependencies()!!)
        entries.addAll(createClassFolders()!!)
        return entries.build()
    }

    private fun createOutput(): ClasspathEntry {
        return Output(classpath!!.getProject().relativePath(classpath.getDefaultOutputDir()))
    }

    private fun createSourceFolders(): MutableList<SourceFolder?>? {
        return SourceFoldersCreator().createSourceFolders(classpath)
    }

    private fun createContainers(): MutableList<ClasspathEntry?> {
        return classpath!!.getContainers().stream()
            .map<Container?> { path: String? -> Container(path) }
            .collect(ImmutableList.toImmutableList<ClasspathEntry?>())
    }

    private fun createDependencies(): MutableList<AbstractClasspathEntry?>? {
        return dependenciesCreator.createDependencyEntries()
    }

    private fun createClassFolders(): MutableList<out ClasspathEntry?>? {
        return if (classpath!!.isProjectDependenciesOnly()) mutableListOf<ClasspathEntry?>() else ClassFoldersCreator().create(classpath)
    }
}
