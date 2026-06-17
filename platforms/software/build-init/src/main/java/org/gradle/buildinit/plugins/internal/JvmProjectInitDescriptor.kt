/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.buildinit.plugins.internal

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.buildinit.plugins.internal.model.Description
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework
import org.gradle.buildinit.plugins.internal.modifiers.Language
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion.Companion.of
import org.gradle.util.internal.GroovyDependencyUtil
import java.util.Optional
import java.util.function.Consumer
import java.util.stream.Collectors

abstract class JvmProjectInitDescriptor(
    protected val description: Description,
    protected val libraryVersionProvider: TemplateLibraryVersionProvider,
    private val documentationRegistry: DocumentationRegistry
) : LanguageLibraryProjectInitDescriptor() {
    protected fun isSingleProject(settings: InitSettings): Boolean {
        return settings.getModularizationOption() == ModularizationOption.SINGLE_PROJECT
    }

    protected fun applicationConventionPlugin(): String {
        return InitSettings.Companion.CONVENTION_PLUGIN_NAME_PREFIX + "." + getLanguage().getName() + "-application-conventions"
    }

    protected fun libraryConventionPlugin(): String {
        return InitSettings.Companion.CONVENTION_PLUGIN_NAME_PREFIX + "." + getLanguage().getName() + "-library-conventions"
    }

    private fun commonConventionPlugin(): String {
        return InitSettings.Companion.CONVENTION_PLUGIN_NAME_PREFIX + "." + getLanguage().getName() + "-common-conventions"
    }

    override fun getId(): String {
        return getLanguage().getName() + "-" + getComponentType().toString()
    }

    override fun getLanguage(): Language {
        return description.getLanguage()
    }

    override fun isJvmLanguage(): Boolean {
        return true
    }

    override fun supportsPackage(): Boolean {
        return true
    }

    override fun getDefaultTestFramework(modularizationOption: ModularizationOption): BuildInitTestFramework {
        if (modularizationOption == ModularizationOption.WITH_LIBRARY_PROJECTS) {
            // This is the only supported option
            return BuildInitTestFramework.JUNIT_JUPITER
        }
        return description.getDefaultTestFramework()
    }

    override fun getTestFrameworks(modularizationOption: ModularizationOption): MutableSet<BuildInitTestFramework> {
        if (modularizationOption == ModularizationOption.WITH_LIBRARY_PROJECTS) {
            // This is the only supported option
            return mutableSetOf<BuildInitTestFramework>(BuildInitTestFramework.JUNIT_JUPITER)
        }
        return description.getSupportedTestFrameworks()
    }

    override fun getFurtherReading(settings: InitSettings): Optional<String> {
        val multi = if (isSingleProject(settings)) "" else "_multi_project"
        return Optional.of<String>(documentationRegistry.getSampleForMessage("building_" + getLanguage().getName() + "_" + getComponentType().pluralName() + multi))
    }

    override fun generateProjectBuildScript(projectName: String, settings: InitSettings, buildScriptBuilder: BuildScriptBuilder) {
        if (!isSingleProject(settings)) {
            return
        }

        addMavenCentral(buildScriptBuilder)

        description.getPluginName().ifPresent(Consumer { languagePlugin: String? ->
            val pluginVersionProperty = description.getPluginVersionProperty()
            val pluginVersion = if (pluginVersionProperty == null) null else libraryVersionProvider.getVersion(pluginVersionProperty)
            buildScriptBuilder.plugin("Apply the " + languagePlugin + " Plugin to add support for " + getLanguage() + ".", languagePlugin!!, pluginVersion, description.getExplicitPluginAlias())
        })

        settings.getJavaLanguageVersion().ifPresent(Consumer { languageVersion: JavaLanguageVersion? ->
            buildScriptBuilder.javaToolchainFor(languageVersion!!)
        })

        buildScriptBuilder
            .fileComment("This generated file contains a sample " + getLanguage() + " " + getComponentType() + " project to get you started.")
            .fileComment(documentationRegistry.getDocumentationRecommendationFor("details on building Java & JVM projects", "building_java_projects"))

        addStandardDependencies(buildScriptBuilder, false)

        if (settings.isUseTestSuites()) {
            // Spock test framework requires that we also have the Groovy plugin applied
            if (getLanguage() != Language.GROOVY && settings.getTestFramework() == BuildInitTestFramework.SPOCK) {
                buildScriptBuilder.plugin("Apply the groovy plugin to also add support for Groovy (needed for Spock)", "groovy")
            }
            configureDefaultTestSuite(buildScriptBuilder, settings.getTestFramework(), libraryVersionProvider)
        } else {
            addTestFramework(settings.getTestFramework(), buildScriptBuilder)
        }
    }

    override fun generateConventionPluginBuildScript(conventionPluginName: String, settings: InitSettings, buildScriptBuilder: BuildScriptBuilder) {
        if ("common" == conventionPluginName) {
            addMavenCentral(buildScriptBuilder)
            val languagePlugin = description.getPluginName().orElse("java")
            buildScriptBuilder.plugin("Apply the " + languagePlugin + " Plugin to add support for " + getLanguage() + ".", languagePlugin)
            addStandardDependencies(buildScriptBuilder, true)
            addDependencyConstraints(buildScriptBuilder)

            if (settings.isUseTestSuites()) {
                configureDefaultTestSuite(buildScriptBuilder, settings.getTestFramework(), libraryVersionProvider)
            } else {
                addTestFramework(settings.getTestFramework(), buildScriptBuilder)
            }

            settings.getJavaLanguageVersion().ifPresent(Consumer { languageVersion: JavaLanguageVersion? ->
                buildScriptBuilder.javaToolchainFor(languageVersion!!)
            })
        } else {
            buildScriptBuilder.plugin("Apply the common convention plugin for shared build configuration between library and application projects.", commonConventionPlugin())
            if ("library" == conventionPluginName) {
                applyLibraryPlugin(buildScriptBuilder)
            } else if ("application" == conventionPluginName) {
                applyApplicationPlugin(buildScriptBuilder)
            }
        }
    }

    override fun generateSources(settings: InitSettings, templateFactory: TemplateFactory) {
        for (subproject in settings.getSubprojects()) {
            val sourceTemplates = getSourceTemplates(subproject, settings, templateFactory)
            val testSourceTemplates = getTestSourceTemplates(subproject, settings, templateFactory)

            val templateOps = sourceTemplates.stream()
                .map<TemplateOperation> { t: String? -> templateFactory.fromSourceTemplate(templatePath(t!!), "main", subproject, templateLanguage(t)) }
                .collect(Collectors.toList())
            testSourceTemplates.stream()
                .map<TemplateOperation> { t: String? -> templateFactory.fromSourceTemplate(templatePath(t!!), "test", subproject, templateLanguage(t)) }
                .forEach { e: TemplateOperation? -> templateOps.add(e!!) }

            templateFactory.whenNoSourcesAvailable(subproject, templateOps).generate()
        }
    }

    private fun templatePath(baseFileName: String): String {
        return (getLanguage().getName() + getComponentType().toString() + "/" + baseFileName
                + "." + templateLanguage(baseFileName).getExtension() + ".template")
    }

    private fun templateLanguage(baseFileName: String): Language {
        if (baseFileName.startsWith("groovy/")) {
            return Language.GROOVY
        }
        return getLanguage()
    }

    protected abstract fun getSourceTemplates(subproject: String, settings: InitSettings, templateFactory: TemplateFactory): MutableList<String>

    protected abstract fun getTestSourceTemplates(subproject: String, settings: InitSettings, templateFactory: TemplateFactory): MutableList<String>

    protected fun applyApplicationPlugin(buildScriptBuilder: BuildScriptBuilder) {
        buildScriptBuilder.plugin(
            "Apply the application plugin to add support for building a CLI application in Java.",
            "application"
        )
    }

    protected fun applyLibraryPlugin(buildScriptBuilder: BuildScriptBuilder) {
        buildScriptBuilder.plugin(
            "Apply the java-library plugin for API and implementation separation.",
            "java-library"
        )
    }

    private fun addMavenCentral(buildScriptBuilder: BuildScriptBuilder) {
        buildScriptBuilder.repositories().mavenCentral("Use Maven Central for resolving dependencies.")
    }

    private fun addStandardDependencies(buildScriptBuilder: BuildScriptBuilder, constraintsDefined: Boolean) {
        when (getLanguage()) {
            Language.GROOVY -> {
                val groovyVersion = libraryVersionProvider.getVersion("groovy")
                buildScriptBuilder.implementationDependency(
                    "Use the latest Groovy version for building this library",
                    BuildInitDependency.Companion.of(GroovyDependencyUtil.groovyGroupName(groovyVersion) + ":groovy-all", if (constraintsDefined) null else groovyVersion)
                )
            }

            Language.SCALA -> {
                val scalaVersion = libraryVersionProvider.getVersion("scala")
                val scalaLibraryVersion = libraryVersionProvider.getVersion("scala-library")
                buildScriptBuilder.implementationDependency(
                    "Use Scala " + scalaVersion + " in our library project",
                    BuildInitDependency.Companion.of("org.scala-lang:scala-library", if (constraintsDefined) null else scalaLibraryVersion)
                )
            }

            else -> {}
        }
    }

    private fun addDependencyConstraints(buildScriptBuilder: BuildScriptBuilder) {
        val commonsTextVersion = libraryVersionProvider.getVersion("commons-text")
        buildScriptBuilder.implementationDependencyConstraint(
            "Define dependency versions as constraints",
            BuildInitDependency.Companion.of("org.apache.commons:commons-text", commonsTextVersion)
        )

        if (getLanguage() == Language.GROOVY) {
            val groovyVersion = libraryVersionProvider.getVersion("groovy")
            buildScriptBuilder.implementationDependencyConstraint(null, BuildInitDependency.Companion.of(GroovyDependencyUtil.groovyGroupName(groovyVersion) + ":groovy-all", groovyVersion))
        }
        if (getLanguage() == Language.SCALA) {
            val scalaLibraryVersion = libraryVersionProvider.getVersion("scala-library")
            buildScriptBuilder.implementationDependencyConstraint(null, BuildInitDependency.Companion.of("org.scala-lang:scala-library", scalaLibraryVersion))
        }
    }

    private fun addTestFramework(testFramework: BuildInitTestFramework, buildScriptBuilder: BuildScriptBuilder) {
        when (testFramework) {
            BuildInitTestFramework.SPOCK -> {
                if (getLanguage() != Language.GROOVY) {
                    val groovyVersion = libraryVersionProvider.getVersion("groovy")
                    buildScriptBuilder
                        .plugin("Apply the groovy plugin to also add support for Groovy (needed for Spock)", "groovy")
                        .testImplementationDependency(
                            "Use the latest Groovy version for Spock testing",
                            BuildInitDependency.Companion.of(GroovyDependencyUtil.groovyGroupName(groovyVersion) + ":groovy", groovyVersion)
                        )
                }
                buildScriptBuilder.testImplementationDependency(
                    "Use the awesome Spock testing and specification framework even with Java",
                    BuildInitDependency.Companion.of("org.spockframework:spock-core", libraryVersionProvider.getVersion("spock")),
                    BuildInitDependency.Companion.of("junit:junit", libraryVersionProvider.getVersion("junit"))
                )
                buildScriptBuilder.testRuntimeOnlyDependency(null, BuildInitDependency.Companion.of("org.junit.platform:junit-platform-launcher"))
                buildScriptBuilder.taskMethodInvocation(
                    "Use JUnit Platform for unit tests.",
                    "test", "Test", "useJUnitPlatform"
                )
            }

            BuildInitTestFramework.TESTNG -> buildScriptBuilder
                .testImplementationDependency(
                    "Use TestNG framework, also requires calling test.useTestNG() below",
                    BuildInitDependency.Companion.of("org.testng:testng", libraryVersionProvider.getVersion("testng"))
                )
                .taskMethodInvocation(
                    "Use TestNG for unit tests.",
                    "test", "Test", "useTestNG"
                )

            BuildInitTestFramework.JUNIT_JUPITER -> {
                buildScriptBuilder.testImplementationDependency(
                    "Use JUnit Jupiter for testing.",
                    BuildInitDependency.Companion.of("org.junit.jupiter:junit-jupiter", libraryVersionProvider.getVersion("junit-jupiter"))
                )
                buildScriptBuilder.testRuntimeOnlyDependency(null, BuildInitDependency.Companion.of("org.junit.platform:junit-platform-launcher"))

                buildScriptBuilder.taskMethodInvocation(
                    "Use JUnit Platform for unit tests.",
                    "test", "Test", "useJUnitPlatform"
                )
            }

            BuildInitTestFramework.SCALATEST -> {
                val scalaVersion = libraryVersionProvider.getVersion("scala")
                val scalaTestVersion = libraryVersionProvider.getVersion("scalatest")
                val scalaTestPlusJunitVersion = libraryVersionProvider.getVersion("scalatestplus-junit")
                val junitVersion = libraryVersionProvider.getVersion("junit")
                val scalaXmlVersion = libraryVersionProvider.getVersion("scala-xml")
                buildScriptBuilder.testImplementationDependency(
                    "Use Scalatest for testing our library",
                    BuildInitDependency.Companion.of("junit:junit", junitVersion),
                    BuildInitDependency.Companion.of("org.scalatest:scalatest_" + scalaVersion, scalaTestVersion),
                    BuildInitDependency.Companion.of("org.scalatestplus:junit-4-13_" + scalaVersion, scalaTestPlusJunitVersion)
                )
                buildScriptBuilder.testRuntimeOnlyDependency(
                    "Need scala-xml at test runtime",
                    BuildInitDependency.Companion.of("org.scala-lang.modules:scala-xml_" + scalaVersion, scalaXmlVersion)
                )
            }

            BuildInitTestFramework.KOTLINTEST -> {
                buildScriptBuilder.testImplementationDependency("Use the Kotlin Test integration.", BuildInitDependency.Companion.of("org.jetbrains.kotlin:kotlin-test"))
                // TODO: Make this work with JUnit 5.6.0 again, see https://github.com/gradle/gradle/issues/13955
                buildScriptBuilder.testImplementationDependency(
                    "Use the JUnit 5 integration.",
                    BuildInitDependency.Companion.of("org.junit.jupiter:junit-jupiter-engine", libraryVersionProvider.getVersion("junit-jupiter"))
                )
                buildScriptBuilder.testRuntimeOnlyDependency(null, BuildInitDependency.Companion.of("org.junit.platform:junit-platform-launcher"))

                buildScriptBuilder.taskMethodInvocation(
                    "Use JUnit Platform for unit tests.",
                    "test", "Test", "useJUnitPlatform"
                )
            }

            else -> buildScriptBuilder.testImplementationDependency(
                "Use JUnit test framework.",
                BuildInitDependency.Companion.of("junit:junit", libraryVersionProvider.getVersion("junit"))
            )
        }
    }
}
