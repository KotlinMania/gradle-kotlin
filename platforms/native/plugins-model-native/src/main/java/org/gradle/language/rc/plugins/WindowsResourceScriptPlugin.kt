/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.language.rc.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.base.internal.registry.LanguageTransformContainer
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.language.nativeplatform.internal.NativeLanguageTransform
import org.gradle.language.rc.WindowsResourceSet
import org.gradle.language.rc.internal.DefaultWindowsResourceSet
import org.gradle.language.rc.plugins.internal.WindowsResourcesCompileTaskConfig
import org.gradle.model.Mutate
import org.gradle.model.RuleSource
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.internal.DefaultPreprocessingTool
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentType
import org.gradle.platform.base.TypeBuilder

/**
 * Adds core language support for Windows resource script files.
 */
@Incubating
abstract class WindowsResourceScriptPlugin : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(ComponentModelBasePlugin::class.java)
    }

    internal class Rules : RuleSource() {
        @ComponentType
        fun registerLanguage(builder: TypeBuilder<WindowsResourceSet?>) {
            builder.defaultImplementation(DefaultWindowsResourceSet::class.java)
        }

        @Mutate
        fun registerLanguageTransform(languages: LanguageTransformContainer, serviceRegistry: ServiceRegistry?) {
            languages.add(WindowsResources())
        }
    }

    private class WindowsResources : NativeLanguageTransform<WindowsResourceSet?>() {
        val sourceSetType: Class<WindowsResourceSet?>?
            get() = WindowsResourceSet::class.java

        val binaryTools: MutableMap<String?, Class<*>?>
            get() {
                val tools: MutableMap<String?, Class<*>?> = LinkedHashMap<String?, Class<*>?>()
                tools.put("rcCompiler", DefaultPreprocessingTool::class.java)
                return tools
            }

        val languageName: String
            get() = "rc"

        val toolType: ToolType
            get() = ToolType.WINDOW_RESOURCES_COMPILER

        val transformTask: SourceTransformTaskConfig
            get() = WindowsResourcesCompileTaskConfig()

        public override fun applyToBinary(binary: BinarySpec?): Boolean {
            return binary is NativeBinarySpec && shouldProcessResources(binary)
        }

        fun shouldProcessResources(binary: NativeBinarySpec): Boolean {
            return binary.getTargetPlatform().operatingSystem!!.isWindows
        }
    }
}
