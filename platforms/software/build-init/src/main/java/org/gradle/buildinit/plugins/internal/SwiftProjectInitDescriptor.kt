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
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework
import org.gradle.buildinit.plugins.internal.modifiers.Language
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.Companion.host
import java.util.Optional

abstract class SwiftProjectInitDescriptor(private val templateOperationFactory: TemplateOperationFactory, private val documentationRegistry: DocumentationRegistry) :
    LanguageLibraryProjectInitDescriptor() {
    override fun getLanguage(): Language {
        return Language.SWIFT
    }

    override fun generateProjectBuildScript(projectName: String, settings: InitSettings, buildScriptBuilder: BuildScriptBuilder) {
        buildScriptBuilder
            .fileComment("This generated file contains a sample Swift project to get you started.")
            .fileComment(documentationRegistry.getDocumentationRecommendationFor("details on building Swift applications and libraries", "building_swift_projects"))
        configureBuildScript(settings, buildScriptBuilder)
    }

    override fun generateConventionPluginBuildScript(conventionPluginName: String, settings: InitSettings, buildScriptBuilder: BuildScriptBuilder) {
    }

    override fun generateSources(settings: InitSettings, templateFactory: TemplateFactory) {
        val sourceTemplate = sourceTemplateOperation(settings)
        val testSourceTemplate = testTemplateOperation(settings)
        val testEntryPointTemplate = testEntryPointTemplateOperation(settings)
        templateFactory.whenNoSourcesAvailable(sourceTemplate, testSourceTemplate, testEntryPointTemplate).generate()
    }

    override fun getTestFrameworks(modularizationOption: ModularizationOption): MutableSet<BuildInitTestFramework> {
        return mutableSetOf<BuildInitTestFramework>(BuildInitTestFramework.XCTEST)
    }

    override fun getDefaultTestFramework(modularizationOption: ModularizationOption): BuildInitTestFramework {
        return BuildInitTestFramework.XCTEST
    }

    override fun getFurtherReading(settings: InitSettings): Optional<String> {
        return Optional.of<String>(documentationRegistry.getSampleForMessage("building_swift_" + getComponentType().pluralName()))
    }

    protected abstract fun sourceTemplateOperation(settings: InitSettings): TemplateOperation

    protected abstract fun testTemplateOperation(settings: InitSettings): TemplateOperation

    protected abstract fun testEntryPointTemplateOperation(settings: InitSettings): TemplateOperation

    protected open fun configureBuildScript(settings: InitSettings, buildScriptBuilder: BuildScriptBuilder) {
    }

    override fun supportsPackage(): Boolean {
        return false
    }

    protected val hostTargetMachineDefinition: String
        get() {
            val host = host()
            assert(!host.operatingSystem!!.isWindows)
            return CppProjectInitDescriptor.Companion.buildNativeHostTargetDefinition(host)
        }

    protected fun configureTargetMachineDefinition(buildScriptBuilder: ScriptBlockBuilder) {
        if (current()!!.isWindows) {
            buildScriptBuilder.methodInvocation(
                "Swift tool chain does not support Windows. The following targets macOS and Linux:",
                "targetMachines.add",
                buildScriptBuilder.propertyExpression("machines.macOS.x86_64")
            )
            buildScriptBuilder.methodInvocation(null, "targetMachines.add", buildScriptBuilder.propertyExpression("machines.linux.x86_64"))
        } else {
            buildScriptBuilder.methodInvocation(
                "Set the target operating system and architecture for this library",
                "targetMachines.add",
                buildScriptBuilder.propertyExpression(this.hostTargetMachineDefinition)
            )
        }
    }

    fun fromSwiftTemplate(template: String, settings: InitSettings, sourceSetName: String, sourceDir: String): TemplateOperation {
        val targetFileName = template.substring(template.lastIndexOf("/") + 1).replace(".template", "")
        return fromSwiftTemplate(template, targetFileName, settings, sourceSetName, sourceDir)
    }

    fun fromSwiftTemplate(template: String, targetFileName: String, settings: InitSettings, sourceSetName: String, sourceDir: String): TemplateOperation {
        require(!(settings == null || settings.getProjectName().isEmpty())) { "Project name cannot be empty for a Swift project" }

        val moduleName = ModuleNameBuilder.toModuleName(settings.getSubprojects().get(0))

        return templateOperationFactory.newTemplateOperation()
            .withTemplate(template)
            .withTarget(settings.getTarget().file(settings.getSubprojects().get(0) + "/src/" + sourceSetName + "/" + sourceDir + "/" + targetFileName).getAsFile())
            .withBinding("projectName", settings.getProjectName())
            .withBinding("moduleName", moduleName)
            .withBinding("fileComment", if (settings.isWithComments()) "This source file was generated by the Gradle 'init' task" else "")
            .create()
    }
}
