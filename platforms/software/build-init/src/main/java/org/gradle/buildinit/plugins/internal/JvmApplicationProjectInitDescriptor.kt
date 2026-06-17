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
package org.gradle.buildinit.plugins.internal

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.buildinit.plugins.internal.model.Description
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType
import org.gradle.buildinit.plugins.internal.modifiers.Language
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption
import org.gradle.jvm.toolchain.JavaLanguageVersion.Companion.of
import java.util.Arrays
import java.util.TreeSet

class JvmApplicationProjectInitDescriptor(description: Description, libraryVersionProvider: TemplateLibraryVersionProvider, documentationRegistry: DocumentationRegistry) :
    JvmProjectInitDescriptor(description, libraryVersionProvider, documentationRegistry) {
    override fun getComponentType(): ComponentType {
        return ComponentType.APPLICATION
    }

    override fun getModularizationOptions(): MutableSet<ModularizationOption> {
        return TreeSet<ModularizationOption>(Arrays.asList<ModularizationOption>(ModularizationOption.SINGLE_PROJECT, ModularizationOption.WITH_LIBRARY_PROJECTS))
    }

    override fun generateProjectBuildScript(projectName: String, settings: InitSettings, buildScriptBuilder: BuildScriptBuilder) {
        super.generateProjectBuildScript(projectName, settings, buildScriptBuilder)

        if ("app" == projectName) {
            buildScriptBuilder.block(null, "application", Action { b: ScriptBlockBuilder? ->
                var mainClass = if (getLanguage() == Language.KOTLIN) "AppKt" else "App"
                if (!isSingleProject(settings)) {
                    mainClass = "app." + mainClass
                }
                b!!.propertyAssignment("Define the main class for the application.", "mainClass", withPackage(settings, mainClass), true)
            })
        }

        if (isSingleProject(settings)) {
            applyApplicationPlugin(buildScriptBuilder)
            buildScriptBuilder.implementationDependency(
                "This dependency is used by the application.",
                BuildInitDependency.Companion.of("com.google.guava:guava", libraryVersionProvider.getVersion("guava"))
            )
        } else {
            if ("app" == projectName) {
                buildScriptBuilder.plugin(null, applicationConventionPlugin())
                buildScriptBuilder.dependencies().dependency("implementation", null, BuildInitDependency.Companion.of("org.apache.commons:commons-text"))
                buildScriptBuilder.dependencies().projectDependency("implementation", null, ":utilities")
            } else {
                buildScriptBuilder.plugin(null, libraryConventionPlugin())
                if ("utilities" == projectName) {
                    buildScriptBuilder.dependencies().projectDependency("api", null, ":list")
                }
            }
        }
    }

    override fun getSourceTemplates(subproject: String, settings: InitSettings, templateFactory: TemplateFactory): MutableList<String> {
        if (isSingleProject(settings)) {
            return mutableListOf<String>("App")
        }
        when (subproject) {
            "app" -> return ImmutableList.of<String>("multi/app/App", "multi/app/MessageUtils")
            "list" -> return ImmutableList.of<String>("multi/list/LinkedList")
            "utilities" -> return ImmutableList.of<String>("multi/utilities/JoinUtils", "multi/utilities/SplitUtils", "multi/utilities/StringUtils")
            else -> return ImmutableList.of<String>()
        }
    }

    override fun getTestSourceTemplates(subproject: String, settings: InitSettings, templateFactory: TemplateFactory): MutableList<String> {
        if (isSingleProject(settings)) {
            return mutableListOf<String>(getTestFrameWorkName(settings))
        }

        when (subproject) {
            "app" -> return ImmutableList.of<String>("multi/app/junit5/MessageUtilsTest")
            "list" -> return ImmutableList.of<String>("multi/list/junit5/LinkedListTest")
            else -> return ImmutableList.of<String>()
        }
    }

    companion object {
        private fun getTestFrameWorkName(settings: InitSettings): String {
            when (settings.getTestFramework()) {
                BuildInitTestFramework.SPOCK -> return "groovy/AppTest"
                BuildInitTestFramework.TESTNG -> return "testng/AppTest"
                BuildInitTestFramework.JUNIT, BuildInitTestFramework.KOTLINTEST -> return "AppTest"
                BuildInitTestFramework.JUNIT_JUPITER -> return "junitjupiter/AppTest"
                BuildInitTestFramework.SCALATEST -> return "AppSuite"
                else -> throw IllegalArgumentException()
            }
        }
    }
}
