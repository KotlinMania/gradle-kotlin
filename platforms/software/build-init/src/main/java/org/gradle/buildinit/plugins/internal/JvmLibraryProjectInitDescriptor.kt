/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType
import org.gradle.jvm.toolchain.JavaLanguageVersion.Companion.of

class JvmLibraryProjectInitDescriptor(description: Description, libraryVersionProvider: TemplateLibraryVersionProvider, documentationRegistry: DocumentationRegistry) :
    JvmProjectInitDescriptor(description, libraryVersionProvider, documentationRegistry) {
    override fun getComponentType(): ComponentType {
        return ComponentType.LIBRARY
    }

    override fun generateProjectBuildScript(projectName: String, settings: InitSettings, buildScriptBuilder: BuildScriptBuilder) {
        super.generateProjectBuildScript(projectName, settings, buildScriptBuilder)

        applyLibraryPlugin(buildScriptBuilder)
        if (!isSingleProject(settings)) {
            buildScriptBuilder.plugin(
                "Apply the java conventions plugin from build-logic.",
                "buildlogic.java-library-conventions"
            )
        }
        buildScriptBuilder.dependency(
            "api",
            "This dependency is exported to consumers, that is to say found on their compile classpath.",
            BuildInitDependency.Companion.of("org.apache.commons:commons-math3", libraryVersionProvider.getVersion("commons-math"))
        )
        buildScriptBuilder.implementationDependency(
            "This dependency is used internally, and not exposed to consumers on their own compile classpath.",
            BuildInitDependency.Companion.of("com.google.guava:guava", libraryVersionProvider.getVersion("guava"))
        )
    }

    override fun getSourceTemplates(subproject: String, settings: InitSettings, templateFactory: TemplateFactory): MutableList<String> {
        return mutableListOf<String>("Library")
    }

    override fun getTestSourceTemplates(subproject: String, settings: InitSettings, templateFactory: TemplateFactory): MutableList<String> {
        return mutableListOf<String>(getUnitTestSourceTemplateName(settings))
    }

    companion object {
        private fun getUnitTestSourceTemplateName(settings: InitSettings): String {
            when (settings.getTestFramework()) {
                BuildInitTestFramework.SPOCK -> return "groovy/LibraryTest"
                BuildInitTestFramework.TESTNG -> return "testng/LibraryTest"
                BuildInitTestFramework.JUNIT, BuildInitTestFramework.KOTLINTEST -> return "LibraryTest"
                BuildInitTestFramework.JUNIT_JUPITER -> return "junitjupiter/LibraryTest"
                BuildInitTestFramework.SCALATEST -> return "LibrarySuite"
                else -> throw IllegalArgumentException()
            }
        }
    }
}
