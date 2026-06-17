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
package org.gradle.language.cpp.plugins

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.language.cpp.CppApplication
import org.gradle.language.cpp.CppBinary
import org.gradle.language.cpp.CppExecutable
import org.gradle.language.cpp.CppPlatform
import org.gradle.language.cpp.internal.DefaultCppApplication
import org.gradle.language.cpp.internal.DefaultCppPlatform
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.internal.NativeComponentFactory
import org.gradle.language.nativeplatform.internal.Dimensions
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector
import org.gradle.nativeplatform.TargetMachineFactory
import org.gradle.nativeplatform.platform.internal.Architectures
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.util.concurrent.Callable
import java.util.function.Supplier
import javax.inject.Inject

/**
 *
 * A plugin that produces a native application from C++ source.
 *
 *
 * Assumes the source files are located in `src/main/cpp` and header files are located in `src/main/headers`.
 *
 *
 * Adds a [CppApplication] extension to the project to allow configuration of the application.
 *
 * @since 4.5
 */
abstract class CppApplicationPlugin @Inject constructor(
    private val componentFactory: NativeComponentFactory,
    private val toolChainSelector: ToolChainSelector,
    private val attributesFactory: AttributesFactory?,
    private val targetMachineFactory: TargetMachineFactory?
) : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(CppBasePlugin::class.java)

        val providers = project.getProviders()

        // Add the application and extension
        val application = componentFactory.newInstance<CppApplication?, DefaultCppApplication>(CppApplication::class.java, DefaultCppApplication::class.java, "main")
        project.getExtensions().add<CppApplication?>(CppApplication::class.java, "application", application)
        project.getComponents().add(application)

        // Configure the component
        application.getBaseName().convention(project.getName())
        application.getTargetMachines().convention(Dimensions.useHostAsDefaultTargetMachine(targetMachineFactory))
        application.getDevelopmentBinary().convention(project.provider<CppExecutable?>(Callable {
            application.getBinaries().get().stream()
                .filter { obj: CppBinary? -> CppExecutable::class.java.isInstance(obj) }
                .map<CppExecutable?> { obj: CppBinary? -> CppExecutable::class.java.cast(obj) }
                .filter { binary: CppExecutable? ->
                    !binary!!.isOptimized() && Architectures.forInput(binary.getTargetMachine().getArchitecture().getName()) == DefaultNativePlatform.host().getArchitecture()
                }
                .findFirst()
                .orElseGet(Supplier {
                    application.getBinaries().get().stream()
                        .filter { obj: CppBinary? -> CppExecutable::class.java.isInstance(obj) }
                        .map<CppExecutable?> { obj: CppBinary? -> CppExecutable::class.java.cast(obj) }
                        .filter { binary: CppExecutable? -> !binary!!.isOptimized() }
                        .findFirst()
                        .orElse(null)
                })
        }))

        application.getBinaries().whenElementKnown(Action { binary: CppBinary? ->
            application.getMainPublication().addVariant(binary)
        })

        project.afterEvaluate(Action { p: Project? ->
            // TODO: make build type configurable for components
            Dimensions.applicationVariants(
                application.getBaseName(), application.getTargetMachines(), attributesFactory,
                providers.provider<String?>(Callable { project.getGroup().toString() }), providers.provider<String?>(Callable { project.getVersion().toString() }),
                Action { variantIdentity: NativeVariantIdentity? ->
                    if (Dimensions.tryToBuildOnHost(variantIdentity)) {
                        val result = toolChainSelector.select<CppPlatform?>(CppPlatform::class.java, DefaultCppPlatform(variantIdentity!!.getTargetMachine()))
                        application.addExecutable(variantIdentity, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider())
                    } else {
                        // Known, but not buildable
                        application.getMainPublication().addVariant(variantIdentity)
                    }
                })

            // Configure the binaries
            application.getBinaries().realizeNow()
        })
    }
}
