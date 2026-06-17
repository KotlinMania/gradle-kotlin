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
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption

class SimpleGlobalFilesBuildSettingsDescriptor(private val scriptBuilderFactory: BuildScriptBuilderFactory, private val documentationRegistry: DocumentationRegistry) : BuildContentGenerator {
    fun generateWithoutComments(settings: InitSettings, buildContentGenerationContext: BuildContentGenerationContext) {
        builder(settings, buildContentGenerationContext)
            .withComments(if (settings.isWithComments()) BuildInitComments.EXTERNAL else BuildInitComments.OFF)
            .create(settings.getTarget())
            .generate()
    }

    override fun generate(settings: InitSettings, buildContentGenerationContext: BuildContentGenerationContext) {
        builder(settings, buildContentGenerationContext).create(settings.getTarget()).generate()
    }

    private fun builder(settings: InitSettings, buildContentGenerationContext: BuildContentGenerationContext): BuildScriptBuilder {
        val builder = scriptBuilderFactory.scriptForNewProjectsWithoutVersionCatalog(settings.getDsl(), buildContentGenerationContext, "settings", settings.isUseIncubatingAPIs())
        builder.withComments(if (settings.isWithComments()) BuildInitComments.ON else BuildInitComments.OFF)
        builder.fileComment("The settings file is used to specify which projects to include in your build.\n\n")
            .fileComment(documentationRegistry.getDocumentationRecommendationFor("detailed information on multi-project builds", "multi_project_builds"))
        if (settings.getModularizationOption() == ModularizationOption.WITH_LIBRARY_PROJECTS && settings.isUseIncubatingAPIs()) {
            builder.includePluginsBuild()
        }

        if (settings.getJavaLanguageVersion().isPresent()) {
            builder.plugin(
                "Apply the foojay-resolver plugin to allow automatic download of JDKs",
                "org.gradle.toolchains.foojay-resolver-convention",
                "1.0.0",
                null
            )
        }

        builder.propertyAssignment(null, "rootProject.name", settings.getProjectName())
        if (!settings.getSubprojects().isEmpty()) {
            builder.methodInvocation(null, "include", *settings.getSubprojects().toTypedArray())
        }
        return builder
    }

    companion object {
        const val PLUGINS_BUILD_LOCATION: String = "build-logic"
    }
}
