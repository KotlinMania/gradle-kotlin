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
package org.gradle.language.objectivec.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.base.internal.SourceTransformTaskConfig
import org.gradle.language.base.internal.registry.LanguageTransformContainer
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.language.nativeplatform.internal.DependentSourceSetInternal
import org.gradle.language.nativeplatform.internal.NativeLanguageTransform
import org.gradle.language.nativeplatform.internal.PCHCompileTaskConfig
import org.gradle.language.nativeplatform.internal.SourceCompileTaskConfig
import org.gradle.language.objectivec.ObjectiveCSourceSet
import org.gradle.language.objectivec.internal.DefaultObjectiveCSourceSet
import org.gradle.language.objectivec.tasks.ObjectiveCCompile
import org.gradle.language.objectivec.tasks.ObjectiveCPreCompiledHeaderCompile
import org.gradle.model.Mutate
import org.gradle.model.RuleSource
import org.gradle.nativeplatform.internal.DefaultPreprocessingTool
import org.gradle.nativeplatform.internal.pch.PchEnabledLanguageTransform
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.platform.base.ComponentType
import org.gradle.platform.base.TypeBuilder

/**
 * Adds core Objective-C language support.
 */
@Incubating
abstract class ObjectiveCLangPlugin : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(ComponentModelBasePlugin::class.java)
    }

    internal class Rules : RuleSource() {
        @ComponentType
        fun registerLanguage(builder: TypeBuilder<ObjectiveCSourceSet?>) {
            builder.defaultImplementation(DefaultObjectiveCSourceSet::class.java)
            builder.internalView(DependentSourceSetInternal::class.java)
        }

        @Mutate
        fun registerLanguageTransform(languages: LanguageTransformContainer, serviceRegistry: ServiceRegistry?) {
            languages.add(ObjectiveC())
        }
    }

    private class ObjectiveC : NativeLanguageTransform<ObjectiveCSourceSet?>(), PchEnabledLanguageTransform<ObjectiveCSourceSet?> {
        val sourceSetType: Class<ObjectiveCSourceSet?>?
            get() = ObjectiveCSourceSet::class.java

        val binaryTools: MutableMap<String?, Class<*>?>
            get() {
                val tools: MutableMap<String?, Class<*>?> = LinkedHashMap<String?, Class<*>?>()
                tools.put("objcCompiler", DefaultPreprocessingTool::class.java)
                return tools
            }

        val languageName: String
            get() = "objc"

        val toolType: ToolType
            get() = ToolType.OBJECTIVEC_COMPILER

        val transformTask: SourceTransformTaskConfig
            get() = SourceCompileTaskConfig(this, ObjectiveCCompile::class.java)

        override fun getPchTransformTask(): SourceTransformTaskConfig {
            return PCHCompileTaskConfig(this, ObjectiveCPreCompiledHeaderCompile::class.java)
        }
    }
}
