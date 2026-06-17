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

import com.google.common.collect.ImmutableSet
import org.gradle.api.Action
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework
import org.gradle.buildinit.plugins.internal.modifiers.Language
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption
import org.gradle.jvm.toolchain.JavaLanguageVersion.Companion.of

class GroovyGradlePluginProjectInitDescriptor(private val libraryVersionProvider: TemplateLibraryVersionProvider, documentationRegistry: DocumentationRegistry) : JvmGradlePluginProjectInitDescriptor(
    documentationRegistry,
    libraryVersionProvider
) {
    override fun getId(): String {
        return "groovy-gradle-plugin"
    }

    override fun getDefaultTestFramework(modularizationOption: ModularizationOption): BuildInitTestFramework {
        return BuildInitTestFramework.SPOCK
    }

    override fun getTestFrameworks(modularizationOption: ModularizationOption): MutableSet<BuildInitTestFramework> {
        return ImmutableSet.of<BuildInitTestFramework>(BuildInitTestFramework.SPOCK)
    }

    override fun getLanguage(): Language {
        return Language.GROOVY
    }

    override fun generateProjectBuildScript(projectName: String, settings: InitSettings, buildScriptBuilder: BuildScriptBuilder) {
        super.generateProjectBuildScript(projectName, settings, buildScriptBuilder)
        buildScriptBuilder.plugin("Apply the Groovy plugin to add support for Groovy", "groovy")
        if (!settings.isUseTestSuites()) {
            buildScriptBuilder.testImplementationDependency(
                "Use the awesome Spock testing and specification framework",
                BuildInitDependency.Companion.of("org.spockframework:spock-core", libraryVersionProvider.getVersion("spock"))
            )
            buildScriptBuilder.testRuntimeOnlyDependency(
                null,
                BuildInitDependency.Companion.of("org.junit.platform:junit-platform-launcher")
            )
        }
    }

    override fun sourceTemplate(settings: InitSettings, templateFactory: TemplateFactory, pluginId: String, pluginClassName: String): TemplateOperation {
        return templateFactory.fromSourceTemplate("plugin/groovy/Plugin.groovy.template", Action { t: TemplateFactory.SourceFileTemplate? ->
            t!!.subproject(settings.getSubprojects().get(0))
            t.sourceSet("main")
            t.className(pluginClassName)
            t.binding("pluginId", pluginId)
        })
    }

    override fun testTemplate(settings: InitSettings, templateFactory: TemplateFactory, pluginId: String, testClassName: String): TemplateOperation {
        return templateFactory.fromSourceTemplate("plugin/groovy/spock/PluginTest.groovy.template", Action { t: TemplateFactory.SourceFileTemplate? ->
            t!!.subproject(settings.getSubprojects().get(0))
            t.sourceSet("test")
            t.className(testClassName)
            t.binding("pluginId", pluginId)
        })
    }

    override fun functionalTestTemplate(settings: InitSettings, templateFactory: TemplateFactory, pluginId: String, testClassName: String): TemplateOperation {
        return templateFactory.fromSourceTemplate("plugin/groovy/spock/PluginFunctionalTest.groovy.template", Action { t: TemplateFactory.SourceFileTemplate? ->
            t!!.subproject(settings.getSubprojects().get(0))
            t.sourceSet("functionalTest")
            t.className(testClassName)
            t.binding("pluginId", pluginId)
        })
    }
}
