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

import org.gradle.api.Action
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType

class CppApplicationProjectInitDescriptor(templateOperationFactory: TemplateOperationFactory, documentationRegistry: DocumentationRegistry) :
    CppProjectInitDescriptor(templateOperationFactory, documentationRegistry) {
    override fun getId(): String {
        return "cpp-application"
    }

    override fun getComponentType(): ComponentType {
        return ComponentType.APPLICATION
    }

    override fun sourceTemplateOperation(settings: InitSettings): TemplateOperation {
        return fromCppTemplate("cppapp/app.cpp.template", settings, "main", "cpp")
    }

    override fun headerTemplateOperation(settings: InitSettings): TemplateOperation {
        return fromCppTemplate("cppapp/app.h.template", settings, "main", "headers")
    }

    override fun testTemplateOperation(settings: InitSettings): TemplateOperation {
        return fromCppTemplate("cppapp/app_test.cpp.template", settings, "test", "cpp")
    }

    override fun configureBuildScript(settings: InitSettings, buildScriptBuilder: BuildScriptBuilder) {
        buildScriptBuilder
            .plugin(
                "Apply the cpp-application plugin to add support for building C++ executables",
                "cpp-application"
            )
            .plugin(
                "Apply the cpp-unit-test plugin to add support for building and running C++ test executables",
                "cpp-unit-test"
            )
            .block(
                "Set the target operating system and architecture for this application", "application",
                Action { b: ScriptBlockBuilder? -> b!!.methodInvocation(null, "targetMachines.add", buildScriptBuilder.propertyExpression(getHostTargetMachineDefinition())) })
    }
}
