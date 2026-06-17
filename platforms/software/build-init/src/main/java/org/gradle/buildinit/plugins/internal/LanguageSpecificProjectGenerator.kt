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
package org.gradle.buildinit.plugins.internal

import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption
import java.util.Optional

interface LanguageSpecificProjectGenerator {
    val id: String?

    val componentType: ComponentType?

    val language: Language?

    val isJvmLanguage: Boolean
        get() = false

    val modularizationOptions: MutableSet<ModularizationOption>?

    fun getFurtherReading(settings: InitSettings): Optional<String>?

    fun getTestFrameworks(modularizationOption: ModularizationOption): MutableSet<BuildInitTestFramework>?

    fun getDefaultTestFramework(modularizationOption: ModularizationOption): BuildInitTestFramework?

    fun supportsPackage(): Boolean

    fun generateProjectBuildScript(projectName: String, settings: InitSettings, buildScriptBuilder: BuildScriptBuilder)

    fun generateConventionPluginBuildScript(conventionPluginName: String, settings: InitSettings, buildScriptBuilder: BuildScriptBuilder)

    fun generateSources(settings: InitSettings, templateFactory: TemplateFactory)
}
