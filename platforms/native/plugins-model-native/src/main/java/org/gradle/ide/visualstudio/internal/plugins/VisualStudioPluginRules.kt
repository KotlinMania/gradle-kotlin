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
package org.gradle.ide.visualstudio.internal.plugins

import org.gradle.api.Incubating
import org.gradle.api.internal.project.ProjectIdentifier
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.api.internal.resolve.ProjectModelResolver
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.ide.visualstudio.VisualStudioExtension
import org.gradle.ide.visualstudio.internal.NativeSpecVisualStudioTargetBinary
import org.gradle.ide.visualstudio.internal.VisualStudioExtensionInternal
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.service.ServiceRegistry
import org.gradle.model.Model
import org.gradle.model.Mutate
import org.gradle.model.RuleSource
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.platform.base.BinaryContainer

@Incubating
class VisualStudioPluginRules {
    object VisualStudioExtensionRules : RuleSource() {
        @Model
        fun visualStudio(extensionContainer: ExtensionContainer): VisualStudioExtensionInternal {
            return extensionContainer.getByType<VisualStudioExtension>(VisualStudioExtension::class.java) as VisualStudioExtensionInternal
        }
    }

    object VisualStudioPluginRootRules : RuleSource() {
        // This ensures that subprojects are realized and register their project and project configuration IDE artifacts
        @Mutate
        fun ensureSubprojectsAreRealized(tasks: TaskContainer, projectIdentifier: ProjectIdentifier, serviceRegistry: ServiceRegistry) {
            val projectModelResolver = serviceRegistry.get<ProjectModelResolver?>(ProjectModelResolver::class.java)
            val projectRegistry = uncheckedCast<ProjectRegistry?>(serviceRegistry.get<ProjectRegistry?>(ProjectRegistry::class.java))

            for (subproject in projectRegistry!!.getSubProjects(projectIdentifier.getPath())) {
                projectModelResolver!!.resolveProjectModel(subproject.getPath())!!.find<VisualStudioExtension>("visualStudio", VisualStudioExtension::class.java)
            }
        }
    }

    object VisualStudioPluginProjectRules : RuleSource() {
        @Mutate
        fun createVisualStudioModelForBinaries(visualStudioExtension: VisualStudioExtensionInternal, binaries: BinaryContainer) {
            for (binary in binaries.withType<NativeBinarySpec>(NativeBinarySpec::class.java)) {
                if (binary.isBuildable()) {
                    visualStudioExtension.projectRegistry!!.addProjectConfiguration(NativeSpecVisualStudioTargetBinary(binary))
                }
            }
        }

        @Mutate
        fun realizeExtension(tasks: TaskContainer, visualStudioExtension: VisualStudioExtensionInternal) {
            // Dummy rule to cause the extension to be realized
        }
    }
}
