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

import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType
import org.gradle.buildinit.plugins.internal.modifiers.Language
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption
import org.gradle.jvm.toolchain.JavaLanguageVersion.Companion.of
import java.util.Optional

class LanguageSpecificAdaptor(
    private val descriptor: LanguageSpecificProjectGenerator,
    private val scriptBuilderFactory: BuildScriptBuilderFactory,
    private val templateOperationFactory: TemplateOperationFactory,
    private val libraryVersionProvider: TemplateLibraryVersionProvider
) : ProjectGenerator {
    override fun getId(): String {
        return descriptor.getId()
    }

    override fun getComponentType(): ComponentType {
        return descriptor.getComponentType()
    }

    override fun getLanguage(): Language {
        return descriptor.getLanguage()
    }

    override fun isJvmLanguage(): Boolean {
        return descriptor.isJvmLanguage()
    }

    override fun getModularizationOptions(): MutableSet<ModularizationOption> {
        return descriptor.getModularizationOptions()
    }

    override fun getFurtherReading(settings: InitSettings): Optional<String> {
        return descriptor.getFurtherReading(settings)
    }

    override fun getDefaultDsl(): BuildInitDsl {
        if (descriptor.getLanguage() == Language.GROOVY) {
            return BuildInitDsl.GROOVY
        }
        return BuildInitDsl.KOTLIN
    }

    override fun getTestFrameworks(modularizationOption: ModularizationOption): MutableSet<BuildInitTestFramework> {
        return descriptor.getTestFrameworks(modularizationOption)
    }

    override fun getDefaultTestFramework(modularizationOption: ModularizationOption): BuildInitTestFramework {
        return descriptor.getDefaultTestFramework(modularizationOption)
    }

    override fun supportsPackage(): Boolean {
        return descriptor.supportsPackage()
    }

    fun generateWithExternalComments(settings: InitSettings, buildContentGenerationContext: BuildContentGenerationContext): MutableMap<String, MutableList<String>> {
        val comments = HashMap<String, MutableList<String>>()
        for (buildScriptBuilder in allBuildScriptBuilder(settings, buildContentGenerationContext)) {
            buildScriptBuilder.withComments(if (settings.isWithComments()) BuildInitComments.EXTERNAL else BuildInitComments.OFF)
                .create(settings.getTarget())
                .generate()

            if (settings.isWithComments()) {
                comments.put(buildScriptBuilder.getFileNameWithoutExtension(), buildScriptBuilder.extractComments())
            }
        }
        return comments
    }

    override fun generate(settings: InitSettings, buildContentGenerationContext: BuildContentGenerationContext) {
        for (buildScriptBuilder in allBuildScriptBuilder(settings, buildContentGenerationContext)) {
            buildScriptBuilder.create(settings.getTarget()).generate()
        }
    }

    private fun allBuildScriptBuilder(settings: InitSettings, buildContentGenerationContext: BuildContentGenerationContext): MutableList<BuildScriptBuilder> {
        val builder: MutableList<BuildScriptBuilder> = ArrayList<BuildScriptBuilder>()

        if (settings.getModularizationOption() == ModularizationOption.WITH_LIBRARY_PROJECTS) {
            builder.add(pluginsBuildSettingsScriptBuilder(settings, buildContentGenerationContext))
            builder.add(pluginsBuildBuildScriptBuilder(settings, buildContentGenerationContext))
            for (conventionPluginName in SAMPLE_CONVENTION_PLUGINS) {
                builder.add(conventionPluginScriptBuilder(conventionPluginName, settings, buildContentGenerationContext))
            }
        }

        for (subproject in settings.getSubprojects()) {
            builder.add(projectBuildScriptBuilder(subproject, settings, buildContentGenerationContext, subproject + "/build"))
        }

        val templateFactory = TemplateFactory(settings, descriptor.getLanguage(), templateOperationFactory)
        descriptor.generateSources(settings, templateFactory)

        return builder
    }

    private fun pluginsBuildSettingsScriptBuilder(settings: InitSettings, buildContentGenerationContext: BuildContentGenerationContext): BuildScriptBuilder {
        val builder = scriptBuilderFactory.scriptForNewProjectsWithoutVersionCatalog(
            settings.getDsl(),
            buildContentGenerationContext,
            pluginsBuildLocation(settings) + "/settings",
            settings.isUseIncubatingAPIs()
        )
        builder.withComments(if (settings.isWithComments()) BuildInitComments.ON else BuildInitComments.OFF)
        builder.fileComment("This settings file is used to specify which projects to include in your build-logic build.")
        builder.propertyAssignment(null, "rootProject.name", if (settings.isUseIncubatingAPIs()) "build-logic" else "buildSrc")
        builder.useVersionCatalogFromOuterBuild("Reuse version catalog from the main build.")
        return builder
    }

    private fun pluginsBuildBuildScriptBuilder(settings: InitSettings, buildContentGenerationContext: BuildContentGenerationContext): BuildScriptBuilder {
        val pluginsBuildScriptBuilder =
            scriptBuilderFactory.scriptForNewProjects(settings.getDsl(), buildContentGenerationContext, pluginsBuildLocation(settings) + "/build", settings.isUseIncubatingAPIs())
        pluginsBuildScriptBuilder.withComments(if (settings.isWithComments()) BuildInitComments.ON else BuildInitComments.OFF)
        pluginsBuildScriptBuilder.conventionPluginSupport(
            "Support convention plugins written in " + settings.getDsl()
                .toString() + ". Convention plugins are build scripts in 'src/main' that automatically become available as plugins in the main build."
        )
        if (getLanguage() == Language.KOTLIN) {
            pluginsBuildScriptBuilder.implementationDependency(null, BuildInitDependency.Companion.of("org.jetbrains.kotlin:kotlin-gradle-plugin", libraryVersionProvider.getVersion("kotlin")))
        }
        return pluginsBuildScriptBuilder
    }

    private fun projectBuildScriptBuilder(projectName: String, settings: InitSettings, buildContentGenerationContext: BuildContentGenerationContext, buildFile: String): BuildScriptBuilder {
        val buildScriptBuilder = scriptBuilderFactory.scriptForNewProjects(settings.getDsl(), buildContentGenerationContext, buildFile, settings.isUseIncubatingAPIs())
        buildScriptBuilder.withComments(if (settings.isWithComments()) BuildInitComments.ON else BuildInitComments.OFF)
        descriptor.generateProjectBuildScript(projectName, settings, buildScriptBuilder)
        return buildScriptBuilder
    }

    private fun conventionPluginScriptBuilder(conventionPluginName: String, settings: InitSettings, buildContentGenerationContext: BuildContentGenerationContext): BuildScriptBuilder {
        val buildScriptBuilder = scriptBuilderFactory.scriptForNewProjectsWithoutVersionCatalog(
            settings.getDsl(), buildContentGenerationContext,
            (pluginsBuildLocation(settings) + "/src/main/" + settings.getDsl().name.lowercase() + "/"
                    + InitSettings.Companion.CONVENTION_PLUGIN_NAME_PREFIX + "." + getLanguage().getName() + "-" + conventionPluginName + "-conventions"),
            settings.isUseIncubatingAPIs()
        )
        buildScriptBuilder.withComments(if (settings.isWithComments()) BuildInitComments.ON else BuildInitComments.OFF)
        descriptor.generateConventionPluginBuildScript(conventionPluginName, settings, buildScriptBuilder)
        return buildScriptBuilder
    }

    private fun pluginsBuildLocation(settings: InitSettings): String {
        if (settings.isUseIncubatingAPIs()) {
            return SimpleGlobalFilesBuildSettingsDescriptor.Companion.PLUGINS_BUILD_LOCATION
        } else {
            return "buildSrc"
        }
    }

    companion object {
        private val SAMPLE_CONVENTION_PLUGINS = mutableListOf<String>("common", "application", "library")
    }
}
