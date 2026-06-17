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
package org.gradle.buildinit.plugins.internal.services

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider
import org.gradle.buildinit.plugins.internal.BasicBuildGenerator
import org.gradle.buildinit.plugins.internal.BuildContentGenerator
import org.gradle.buildinit.plugins.internal.BuildGenerator
import org.gradle.buildinit.plugins.internal.BuildInitializer
import org.gradle.buildinit.plugins.internal.BuildScriptBuilderFactory
import org.gradle.buildinit.plugins.internal.CppApplicationProjectInitDescriptor
import org.gradle.buildinit.plugins.internal.CppLibraryProjectInitDescriptor
import org.gradle.buildinit.plugins.internal.DefaultTemplateLibraryVersionProvider
import org.gradle.buildinit.plugins.internal.GitAttributesGenerator
import org.gradle.buildinit.plugins.internal.GitIgnoreGenerator
import org.gradle.buildinit.plugins.internal.GradlePropertiesGenerator
import org.gradle.buildinit.plugins.internal.GroovyGradlePluginProjectInitDescriptor
import org.gradle.buildinit.plugins.internal.JavaGradlePluginProjectInitDescriptor
import org.gradle.buildinit.plugins.internal.JvmApplicationProjectInitDescriptor
import org.gradle.buildinit.plugins.internal.JvmLibraryProjectInitDescriptor
import org.gradle.buildinit.plugins.internal.KotlinGradlePluginProjectInitDescriptor
import org.gradle.buildinit.plugins.internal.LanguageSpecificAdaptor
import org.gradle.buildinit.plugins.internal.LanguageSpecificProjectGenerator
import org.gradle.buildinit.plugins.internal.ProjectGenerator
import org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry
import org.gradle.buildinit.plugins.internal.ResourceDirsGenerator
import org.gradle.buildinit.plugins.internal.SimpleGlobalFilesBuildSettingsDescriptor
import org.gradle.buildinit.plugins.internal.SourceGeneratingBuildGenerator
import org.gradle.buildinit.plugins.internal.SwiftApplicationProjectInitDescriptor
import org.gradle.buildinit.plugins.internal.SwiftLibraryProjectInitDescriptor
import org.gradle.buildinit.plugins.internal.TemplateLibraryVersionProvider
import org.gradle.buildinit.plugins.internal.TemplateOperationFactory
import org.gradle.buildinit.plugins.internal.model.Description
import org.gradle.unexported.buildinit.plugins.internal.maven.PomProjectInitDescriptor
import org.gradle.workers.WorkerExecutor

class ProjectLayoutSetupRegistryFactory(private val mavenSettingsProvider: MavenSettingsProvider, documentationRegistry: DocumentationRegistry, workerExecutor: WorkerExecutor) {
    private val documentationRegistry: DocumentationRegistry?
    private val scriptBuilderFactory: BuildScriptBuilderFactory
    private val templateOperationBuilder: TemplateOperationFactory
    private val workerExecutor: WorkerExecutor

    init {
        this.documentationRegistry = documentationRegistry
        this.workerExecutor = workerExecutor
        this.scriptBuilderFactory = BuildScriptBuilderFactory(documentationRegistry)
        this.templateOperationBuilder = TemplateOperationFactory("/org/gradle/buildinit/tasks/templates", documentationRegistry)
    }

    fun createProjectLayoutSetupRegistry(): ProjectLayoutSetupRegistry {
        val libraryVersionProvider = DefaultTemplateLibraryVersionProvider()
        val templateOperationBuilder = this.templateOperationBuilder
        val settingsDescriptor: BuildContentGenerator = SimpleGlobalFilesBuildSettingsDescriptor(scriptBuilderFactory, documentationRegistry!!)
        val resourcesGenerator: BuildContentGenerator = ResourceDirsGenerator()
        val gitIgnoreGenerator: BuildContentGenerator = GitIgnoreGenerator()
        val gitAttributesGenerator: BuildContentGenerator = GitAttributesGenerator()
        val gradlePropertiesGenerator: BuildContentGenerator = GradlePropertiesGenerator()
        val jvmProjectGenerators: MutableList<BuildContentGenerator?> =
            ImmutableList.of<BuildContentGenerator?>(settingsDescriptor, gitIgnoreGenerator, gitAttributesGenerator, gradlePropertiesGenerator, resourcesGenerator)
        val commonGenerators: MutableList<BuildContentGenerator?> = ImmutableList.of<BuildContentGenerator?>(settingsDescriptor, gitIgnoreGenerator, gitAttributesGenerator, gradlePropertiesGenerator)
        val basicType: BuildGenerator = BasicBuildGenerator(scriptBuilderFactory, documentationRegistry, commonGenerators)
        val mavenBuildConverter = PomProjectInitDescriptor(mavenSettingsProvider, documentationRegistry, workerExecutor)
        val registry = ProjectLayoutSetupRegistry(basicType, mavenBuildConverter, templateOperationBuilder)
        registry.add(of(JvmApplicationProjectInitDescriptor(Description.Companion.JAVA, libraryVersionProvider, documentationRegistry), jvmProjectGenerators, libraryVersionProvider))
        registry.add(of(JvmLibraryProjectInitDescriptor(Description.Companion.JAVA, libraryVersionProvider, documentationRegistry), jvmProjectGenerators, libraryVersionProvider))
        registry.add(of(JvmApplicationProjectInitDescriptor(Description.Companion.GROOVY, libraryVersionProvider, documentationRegistry), jvmProjectGenerators, libraryVersionProvider))
        registry.add(of(JvmLibraryProjectInitDescriptor(Description.Companion.GROOVY, libraryVersionProvider, documentationRegistry), jvmProjectGenerators, libraryVersionProvider))
        registry.add(of(JvmApplicationProjectInitDescriptor(Description.Companion.SCALA, libraryVersionProvider, documentationRegistry), jvmProjectGenerators, libraryVersionProvider))
        registry.add(of(JvmLibraryProjectInitDescriptor(Description.Companion.SCALA, libraryVersionProvider, documentationRegistry), jvmProjectGenerators, libraryVersionProvider))
        registry.add(of(JvmApplicationProjectInitDescriptor(Description.Companion.KOTLIN, libraryVersionProvider, documentationRegistry), jvmProjectGenerators, libraryVersionProvider))
        registry.add(of(JvmLibraryProjectInitDescriptor(Description.Companion.KOTLIN, libraryVersionProvider, documentationRegistry), jvmProjectGenerators, libraryVersionProvider))
        registry.add(of(CppApplicationProjectInitDescriptor(templateOperationBuilder, documentationRegistry), commonGenerators, libraryVersionProvider))
        registry.add(of(CppLibraryProjectInitDescriptor(templateOperationBuilder, documentationRegistry), commonGenerators, libraryVersionProvider))
        registry.add(of(JavaGradlePluginProjectInitDescriptor(libraryVersionProvider, documentationRegistry), jvmProjectGenerators, libraryVersionProvider))
        registry.add(of(GroovyGradlePluginProjectInitDescriptor(libraryVersionProvider, documentationRegistry), jvmProjectGenerators, libraryVersionProvider))
        registry.add(of(KotlinGradlePluginProjectInitDescriptor(libraryVersionProvider, documentationRegistry), jvmProjectGenerators, libraryVersionProvider))
        registry.add(of(SwiftApplicationProjectInitDescriptor(templateOperationBuilder, documentationRegistry), commonGenerators, libraryVersionProvider))
        registry.add(of(SwiftLibraryProjectInitDescriptor(templateOperationBuilder, documentationRegistry), commonGenerators, libraryVersionProvider))
        return registry
    }

    private fun of(projectGenerator: ProjectGenerator, generators: MutableList<BuildContentGenerator?>): BuildGenerator {
        return SourceGeneratingBuildGenerator(projectGenerator, generators)
    }

    private fun of(projectGenerator: LanguageSpecificProjectGenerator, generators: MutableList<BuildContentGenerator?>, libraryVersionProvider: TemplateLibraryVersionProvider): BuildInitializer {
        return of(LanguageSpecificAdaptor(projectGenerator, scriptBuilderFactory, templateOperationBuilder, libraryVersionProvider), generators)
    }
}
