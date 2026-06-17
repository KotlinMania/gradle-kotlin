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
package org.gradle.language.cpp.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.base.internal.SourceTransformTaskConfig
import org.gradle.language.base.internal.registry.LanguageTransformContainer
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.language.cpp.CppSourceSet
import org.gradle.language.cpp.internal.DefaultCppSourceSet
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.language.cpp.tasks.CppPreCompiledHeaderCompile
import org.gradle.language.nativeplatform.internal.DependentSourceSetInternal
import org.gradle.language.nativeplatform.internal.NativeLanguageTransform
import org.gradle.language.nativeplatform.internal.PCHCompileTaskConfig
import org.gradle.language.nativeplatform.internal.SourceCompileTaskConfig
import org.gradle.model.Mutate
import org.gradle.model.RuleSource
import org.gradle.nativeplatform.internal.DefaultPreprocessingTool
import org.gradle.nativeplatform.internal.pch.PchEnabledLanguageTransform
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.platform.base.ComponentType
import org.gradle.platform.base.TypeBuilder
import org.jspecify.annotations.NullMarked

/**
 * Adds core C++ language support.
 */
@Incubating
@NullMarked
abstract class CppLangPlugin : Plugin<Project?> {
    override fun apply(project: Project) {
        val pluginManager = project.getPluginManager()
        pluginManager.apply(ComponentModelBasePlugin::class.java)
    }

    internal class Rules : RuleSource() {
        @ComponentType
        fun registerLanguage(builder: TypeBuilder<CppSourceSet>) {
            builder.defaultImplementation(DefaultCppSourceSet::class.java)
            builder.internalView(DependentSourceSetInternal::class.java)
        }

        @Mutate
        fun registerLanguageTransform(languages: LanguageTransformContainer, serviceRegistry: ServiceRegistry) {
            languages.add(Cpp())
        }
    }

    private class Cpp : NativeLanguageTransform<CppSourceSet?>(), PchEnabledLanguageTransform<CppSourceSet?> {
        override fun getSourceSetType(): Class<CppSourceSet> {
            return CppSourceSet::class.java
        }

        override fun getBinaryTools(): MutableMap<String, Class<*>> {
            val tools: MutableMap<String, Class<*>> = LinkedHashMap<String, Class<*>>()
            tools.put("cppCompiler", DefaultPreprocessingTool::class.java)
            return tools
        }

        override fun getLanguageName(): String {
            return "cpp"
        }

        override fun getToolType(): ToolType {
            return ToolType.CPP_COMPILER
        }

        override fun getTransformTask(): SourceTransformTaskConfig {
            return SourceCompileTaskConfig(this, CppCompile::class.java)
        }

        override fun getPchTransformTask(): SourceTransformTaskConfig {
            return PCHCompileTaskConfig(this, CppPreCompiledHeaderCompile::class.java)
        }
    }
}
