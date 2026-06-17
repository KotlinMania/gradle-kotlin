/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.language.swift.plugins

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.Usage
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.language.nativeplatform.internal.DefaultNativeComponent
import org.gradle.language.plugins.NativeBasePlugin
import org.gradle.language.swift.ProductionSwiftComponent
import org.gradle.language.swift.SwiftSharedLibrary
import org.gradle.language.swift.SwiftStaticLibrary
import org.gradle.language.swift.internal.DefaultSwiftBinary
import org.gradle.language.swift.tasks.SwiftCompile
import org.gradle.nativeplatform.toolchain.internal.xcode.MacOSSdkPathLocator
import org.gradle.nativeplatform.toolchain.plugins.SwiftCompilerPlugin
import org.gradle.swiftpm.internal.NativeProjectPublication
import org.gradle.swiftpm.internal.SwiftPmTarget
import javax.inject.Inject

/**
 * A common base plugin for the Swift application and library plugins
 *
 * @since 4.1
 */
abstract class SwiftBasePlugin @Inject constructor(private val publicationRegistry: ProjectPublicationRegistry, private val locator: MacOSSdkPathLocator) : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(NativeBasePlugin::class.java)
        project.getPluginManager().apply(SwiftCompilerPlugin::class.java)

        val tasks = project.getTasks()
        val buildDirectory = project.getLayout().getBuildDirectory()

        project.getDependencies().getAttributesSchema().attribute<Usage?>(Usage.USAGE_ATTRIBUTE).getCompatibilityRules().add(SwiftCppUsageCompatibilityRule::class.java)

        project.getComponents().withType<DefaultSwiftBinary?>(DefaultSwiftBinary::class.java, Action { binary: DefaultSwiftBinary? ->
            val names = binary!!.getNames()
            val compile = tasks.register<SwiftCompile?>(names.getCompileTaskName("swift"), SwiftCompile::class.java, Action { task: SwiftCompile? ->
                task!!.getModules().from(binary.getCompileModules())
                task.getSource().from(binary.getSwiftSource())
                task.getDebuggable().set(binary.isDebuggable())
                task.getOptimized().set(binary.isOptimized())
                if (binary.isTestable()) {
                    task.getCompilerArgs().add("-enable-testing")
                }
                if (binary.getTargetMachine().operatingSystemFamily.isMacOs()) {
                    task.getCompilerArgs().add("-sdk")
                    task.getCompilerArgs().add(locator.find().getAbsolutePath())
                }
                task.getModuleName().set(binary.getModule())
                task.getObjectFileDir().set(buildDirectory.dir("obj/" + names.getDirName()))
                task.getModuleFile().set(buildDirectory.file(binary.getModule().map<String?>(Transformer { moduleName: String? -> "modules/" + names.getDirName() + moduleName + ".swiftmodule" })))
                task.getSourceCompatibility().set(binary.getTargetPlatform().getSourceCompatibility())

                task.getTargetPlatform().set(binary.getNativePlatform())

                // TODO - make this lazy
                task.getToolChain().set(binary.getToolChain())
                if (binary is SwiftSharedLibrary || binary is SwiftStaticLibrary) {
                    task.getCompilerArgs().add("-parse-as-library")
                }
            })

            binary.getModuleFile().set(compile.flatMap<RegularFile?>(Transformer { task: SwiftCompile? -> task!!.getModuleFile() }))
            binary.getCompileTask().set(compile)
            binary.getObjectsDir().set(compile.flatMap<Directory?>(Transformer { task: SwiftCompile? -> task!!.getObjectFileDir() }))
        })

        project.getComponents().withType<ProductionSwiftComponent?>(ProductionSwiftComponent::class.java, Action { component: ProductionSwiftComponent? ->
            project.afterEvaluate(Action { p: Project? ->
                val componentInternal = component as DefaultNativeComponent
                val projectIdentity = (project as ProjectInternal).getProjectIdentity()
                publicationRegistry.registerPublication(projectIdentity, NativeProjectPublication(componentInternal.getDisplayName(), SwiftPmTarget(component.getModule().get())))
            })
        })
    }

    internal class SwiftCppUsageCompatibilityRule : AttributeCompatibilityRule<Usage?> {
        override fun execute(details: CompatibilityCheckDetails<Usage?>) {
            if (Usage.SWIFT_API == details.getConsumerValue()!!.getName()
                && Usage.C_PLUS_PLUS_API == details.getProducerValue()!!.getName()
            ) {
                details.compatible()
            }
        }
    }
}
