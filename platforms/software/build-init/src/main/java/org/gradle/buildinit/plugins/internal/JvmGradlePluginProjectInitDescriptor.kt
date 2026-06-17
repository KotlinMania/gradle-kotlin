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

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType
import org.gradle.util.internal.TextUtil
import java.util.Optional

abstract class JvmGradlePluginProjectInitDescriptor(private val documentationRegistry: DocumentationRegistry, private val libraryVersionProvider: TemplateLibraryVersionProvider) :
    LanguageLibraryProjectInitDescriptor() {
    override fun getComponentType(): ComponentType {
        return ComponentType.GRADLE_PLUGIN
    }

    override fun supportsPackage(): Boolean {
        return true
    }

    override fun generateProjectBuildScript(projectName: String, settings: InitSettings, buildScriptBuilder: BuildScriptBuilder) {
        buildScriptBuilder.repositories().mavenCentral("Use Maven Central for resolving dependencies.")

        val pluginId = settings.getPackageName() + ".greeting"
        val pluginClassName = StringUtils.capitalize(TextUtil.toCamelCase(settings.getProjectName())) + "Plugin"

        buildScriptBuilder
            .fileComment("This generated file contains a sample Gradle plugin project to get you started.")
            .fileComment(documentationRegistry.getDocumentationRecommendationFor("details on writing Custom Plugins", "custom_plugins"))

        buildScriptBuilder.plugin("Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins", "java-gradle-plugin")

        buildScriptBuilder.block(null, "gradlePlugin", Action { b: ScriptBlockBuilder? ->
            b!!.containerElement("Define the plugin", "plugins", "greeting", null, Action { g: ScriptBlockBuilder? ->
                g!!.propertyAssignment(null, "id", pluginId, true)
                g.propertyAssignment(null, "implementationClass", withPackage(settings, pluginClassName), true)
            })
        })

        val functionalTestSourceSet: BuildScriptBuilder.Expression
        if (settings.isUseTestSuites()) {
            configureDefaultTestSuite(buildScriptBuilder, settings.getTestFramework(), libraryVersionProvider)

            addTestSuite("functionalTest", buildScriptBuilder, settings.getTestFramework(), libraryVersionProvider)
            functionalTestSourceSet = buildScriptBuilder.containerElementExpression("sourceSets", "functionalTest")
        } else {
            functionalTestSourceSet = buildScriptBuilder.createContainerElement("Add a source set for the functional test suite", "sourceSets", "functionalTest", "functionalTestSourceSet")

            val functionalTestImplementation = buildScriptBuilder.containerElementExpression("configurations", "functionalTestImplementation")
            val testImplementation = buildScriptBuilder.containerElementExpression("configurations", "testImplementation")
            buildScriptBuilder.methodInvocation(null, functionalTestImplementation, "extendsFrom", testImplementation)

            val functionalTestRuntimeOnly = buildScriptBuilder.containerElementExpression("configurations", "functionalTestRuntimeOnly")
            val testRuntimeOnly = buildScriptBuilder.containerElementExpression("configurations", "testRuntimeOnly")
            buildScriptBuilder.methodInvocation(null, functionalTestRuntimeOnly, "extendsFrom", testRuntimeOnly)

            val functionalTest: BuildScriptBuilder.Expression =
                buildScriptBuilder.taskRegistration("Add a task to run the functional tests", "functionalTest", "Test", Action { b: ScriptBlockBuilder? ->
                    b!!.propertyAssignment(null, "testClassesDirs", buildScriptBuilder.propertyExpression(functionalTestSourceSet, "output.classesDirs"), true)
                    b.propertyAssignment(null, "classpath", buildScriptBuilder.propertyExpression(functionalTestSourceSet, "runtimeClasspath"), true)
                    b.methodInvocation(null, "useJUnitPlatform")
                })
            buildScriptBuilder.taskMethodInvocation("Run the functional tests as part of `check`", "check", "Task", "dependsOn", functionalTest)
            buildScriptBuilder.taskMethodInvocation("Use JUnit Jupiter for unit tests.", "test", "Test", "useJUnitPlatform")
        }
        buildScriptBuilder.methodInvocation(null, "gradlePlugin.testSourceSets.add", functionalTestSourceSet)
    }

    override fun generateConventionPluginBuildScript(conventionPluginName: String, settings: InitSettings, buildScriptBuilder: BuildScriptBuilder) {
    }

    override fun generateSources(settings: InitSettings, templateFactory: TemplateFactory) {
        val pluginId = settings.getPackageName() + ".greeting"
        val pluginClassName = StringUtils.capitalize(TextUtil.toCamelCase(settings.getProjectName())) + "Plugin"
        val testClassName = pluginClassName + "Test"
        val functionalTestClassName = pluginClassName + "FunctionalTest"

        val sourceTemplate = sourceTemplate(settings, templateFactory, pluginId, pluginClassName)
        val testTemplate = testTemplate(settings, templateFactory, pluginId, testClassName)
        val functionalTestTemplate = functionalTestTemplate(settings, templateFactory, pluginId, functionalTestClassName)
        templateFactory.whenNoSourcesAvailable(sourceTemplate, testTemplate, functionalTestTemplate).generate()
    }

    override fun getFurtherReading(settings: InitSettings): Optional<String> {
        return Optional.of<String>(documentationRegistry.getDocumentationRecommendationFor("information", "custom_plugins"))
    }

    protected abstract fun sourceTemplate(settings: InitSettings, templateFactory: TemplateFactory, pluginId: String, pluginClassName: String): TemplateOperation

    protected abstract fun testTemplate(settings: InitSettings, templateFactory: TemplateFactory, pluginId: String, testClassName: String): TemplateOperation

    protected abstract fun functionalTestTemplate(settings: InitSettings, templateFactory: TemplateFactory, pluginId: String, testClassName: String): TemplateOperation
}
