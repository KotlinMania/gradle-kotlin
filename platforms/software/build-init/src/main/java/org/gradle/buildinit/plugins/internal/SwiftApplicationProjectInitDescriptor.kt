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

import org.gradle.api.Action
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType

class SwiftApplicationProjectInitDescriptor(templateOperationFactory: TemplateOperationFactory, documentationRegistry: DocumentationRegistry) :
    SwiftProjectInitDescriptor(templateOperationFactory, documentationRegistry) {
    override fun getId(): String {
        return "swift-application"
    }

    override fun getComponentType(): ComponentType {
        return ComponentType.APPLICATION
    }

    override fun sourceTemplateOperation(settings: InitSettings): TemplateOperation {
        return fromSwiftTemplate("swiftapp/main.swift.template", settings, "main", "swift")
    }

    override fun testTemplateOperation(settings: InitSettings): TemplateOperation {
        return fromSwiftTemplate("swiftapp/GreeterTests.swift.template", settings, "test", "swift")
    }

    override fun testEntryPointTemplateOperation(settings: InitSettings): TemplateOperation {
        return fromSwiftTemplate("swiftapp/LinuxMain.swift.template", settings, "test", "swift")
    }

    override fun configureBuildScript(settings: InitSettings, buildScriptBuilder: BuildScriptBuilder) {
        buildScriptBuilder
            .plugin(
                "Apply the swift-application plugin to add support for building Swift executables",
                "swift-application"
            )
            .plugin(
                "Apply the xctest plugin to add support for building and running Swift test executables (Linux) or bundles (macOS)",
                "xctest"
            )
            .block(
                "Set the target operating system and architecture for this application",
                "application",
                Action { buildScriptBuilder: ScriptBlockBuilder? -> this.configureTargetMachineDefinition(buildScriptBuilder!!) })
    }
}
