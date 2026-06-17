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
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.internal.NativeComponentFactory
import org.gradle.language.nativeplatform.internal.Dimensions
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector
import org.gradle.language.swift.SwiftApplication
import org.gradle.language.swift.SwiftBinary
import org.gradle.language.swift.SwiftExecutable
import org.gradle.language.swift.SwiftPlatform
import org.gradle.language.swift.internal.DefaultSwiftApplication
import org.gradle.language.swift.internal.DefaultSwiftPlatform
import org.gradle.nativeplatform.TargetMachineFactory
import org.gradle.nativeplatform.platform.internal.Architectures
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.util.internal.TextUtil
import java.util.concurrent.Callable
import java.util.function.Supplier
import javax.inject.Inject

/**
 *
 * A plugin that produces an executable from Swift source.
 *
 *
 * Adds compile, link and install tasks to build the executable. Defaults to looking for source files in `src/main/swift`.
 *
 *
 * Adds a [SwiftApplication] extension to the project to allow configuration of the executable.
 *
 * @since 4.5
 */
abstract class SwiftApplicationPlugin
/**
 * SwiftApplicationPlugin.
 *
 * @since 4.2
 */ @Inject constructor(
    private val componentFactory: NativeComponentFactory,
    private val toolChainSelector: ToolChainSelector,
    private val attributesFactory: AttributesFactory?,
    private val targetMachineFactory: TargetMachineFactory?
) : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(SwiftBasePlugin::class.java)

        val providers = project.getProviders()

        // Add the application and extension
        val application = componentFactory.newInstance<SwiftApplication?, DefaultSwiftApplication>(SwiftApplication::class.java, DefaultSwiftApplication::class.java, "main")
        project.getExtensions().add<SwiftApplication?>(SwiftApplication::class.java, "application", application)
        project.getComponents().add(application)

        // Setup component
        application.getModule().convention(TextUtil.toCamelCase(project.getName()))

        application.getTargetMachines().convention(Dimensions.useHostAsDefaultTargetMachine(targetMachineFactory))
        application.getDevelopmentBinary().convention(project.provider<SwiftExecutable?>(Callable {
            application.getBinaries().get().stream()
                .filter { obj: SwiftBinary? -> SwiftExecutable::class.java.isInstance(obj) }
                .map<SwiftExecutable?> { obj: SwiftBinary? -> SwiftExecutable::class.java.cast(obj) }
                .filter { binary: SwiftExecutable? ->
                    !binary!!.isOptimized() && Architectures.forInput(binary.getTargetMachine().getArchitecture().getName()) == DefaultNativePlatform.host().getArchitecture()
                }
                .findFirst()
                .orElseGet(Supplier {
                    application.getBinaries().get().stream()
                        .filter { obj: SwiftBinary? -> SwiftExecutable::class.java.isInstance(obj) }
                        .map<SwiftExecutable?> { obj: SwiftBinary? -> SwiftExecutable::class.java.cast(obj) }
                        .filter { binary: SwiftExecutable? -> !binary!!.isOptimized() }
                        .findFirst()
                        .orElse(null)
                })
        }))

        project.afterEvaluate(Action { p: Project? ->
            // TODO: make build type configurable for components
            Dimensions.applicationVariants(
                application.getModule(), application.getTargetMachines(), attributesFactory,
                providers.provider<String?>(Callable { project.getGroup().toString() }), providers.provider<String?>(Callable { project.getVersion().toString() }),
                Action { variantIdentity: NativeVariantIdentity? ->
                    if (Dimensions.tryToBuildOnHost(variantIdentity)) {
                        application.getSourceCompatibility().finalizeValue()
                        val result = toolChainSelector.select<SwiftPlatform?>(
                            SwiftPlatform::class.java,
                            DefaultSwiftPlatform(variantIdentity!!.getTargetMachine(), application.getSourceCompatibility().getOrNull())
                        )
                        application.addExecutable(
                            variantIdentity,
                            variantIdentity.isDebuggable() && !variantIdentity.isOptimized(),
                            result.getTargetPlatform(),
                            result.getToolChain(),
                            result.getPlatformToolProvider()
                        )
                    }
                })

            // Configure the binaries
            application.getBinaries().realizeNow()
        })
    }
}
