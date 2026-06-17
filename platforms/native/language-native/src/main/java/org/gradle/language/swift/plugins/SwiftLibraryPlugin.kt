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
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.provider.Property
import org.gradle.language.cpp.CppBinary
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.internal.NativeComponentFactory
import org.gradle.language.nativeplatform.internal.ComponentWithNames
import org.gradle.language.nativeplatform.internal.Dimensions
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector
import org.gradle.language.swift.SwiftBinary
import org.gradle.language.swift.SwiftLibrary
import org.gradle.language.swift.SwiftPlatform
import org.gradle.language.swift.SwiftSharedLibrary
import org.gradle.language.swift.SwiftStaticLibrary
import org.gradle.language.swift.internal.DefaultSwiftLibrary
import org.gradle.language.swift.internal.DefaultSwiftPlatform
import org.gradle.language.swift.internal.DefaultSwiftSharedLibrary
import org.gradle.language.swift.internal.DefaultSwiftStaticLibrary
import org.gradle.nativeplatform.Linkage
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.TargetMachineFactory
import org.gradle.nativeplatform.platform.internal.Architectures
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.util.internal.TextUtil
import java.util.concurrent.Callable
import java.util.function.Supplier
import javax.inject.Inject

/**
 *
 * A plugin that produces a shared library from Swift source.
 *
 *
 * Adds compile, link and install tasks to build the shared library. Defaults to looking for source files in `src/main/swift`.
 *
 *
 * Adds a [SwiftComponent] extension to the project to allow configuration of the library.
 *
 * @since 4.2
 */
abstract class SwiftLibraryPlugin @Inject constructor(
    private val componentFactory: NativeComponentFactory,
    private val toolChainSelector: ToolChainSelector,
    private val attributesFactory: AttributesFactory?,
    private val targetMachineFactory: TargetMachineFactory?
) : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(SwiftBasePlugin::class.java)

        val configurations = project.getConfigurations()
        val providers = project.getProviders()

        val library = componentFactory.newInstance<SwiftLibrary?, DefaultSwiftLibrary>(SwiftLibrary::class.java, DefaultSwiftLibrary::class.java, "main")
        project.getExtensions().add<SwiftLibrary?>(SwiftLibrary::class.java, "library", library)
        project.getComponents().add(library)

        // Setup component
        val module: Property<String?> = library.getModule()
        module.set(TextUtil.toCamelCase(project.getName()))

        library.getTargetMachines().convention(Dimensions.useHostAsDefaultTargetMachine(targetMachineFactory))
        library.getDevelopmentBinary().convention(project.provider<SwiftBinary?>(object : Callable<SwiftBinary?> {
            override fun call(): SwiftBinary? {
                return this.debugSharedHostStream.findFirst().orElseGet(
                    Supplier {
                        this.debugStaticHostStream.findFirst().orElseGet(
                            Supplier {
                                this.debugSharedStream.findFirst().orElseGet(
                                    Supplier { this.debugStaticStream.findFirst().orElse(null) })
                            })
                    })
            }

            val debugStream: Stream<SwiftBinary?>?
                get() = library.getBinaries().get().stream().filter { binary: SwiftBinary? -> !binary!!.isOptimized() }

            val debugSharedStream: Stream<SwiftBinary?>?
                get() = this.debugStream.filter { obj: SwiftBinary? -> SwiftSharedLibrary::class.java.isInstance(obj) }

            val debugSharedHostStream: Stream<SwiftBinary?>?
                get() = this.debugSharedStream.filter { binary: SwiftBinary? ->
                    Architectures.forInput(
                        binary!!.getTargetMachine().getArchitecture().getName()
                    ) == DefaultNativePlatform.host().getArchitecture()
                }

            val debugStaticStream: Stream<SwiftBinary?>?
                get() = this.debugStream.filter { obj: SwiftBinary? -> SwiftStaticLibrary::class.java.isInstance(obj) }

            val debugStaticHostStream: Stream<SwiftBinary?>?
                get() = this.debugStaticStream.filter { binary: SwiftBinary? ->
                    Architectures.forInput(
                        binary!!.getTargetMachine().getArchitecture().getName()
                    ) == DefaultNativePlatform.host().getArchitecture()
                }
        }))

        project.afterEvaluate(Action { p: Project? ->
            // TODO: make build type configurable for components
            Dimensions.libraryVariants(
                library.getModule(), library.getLinkage(), library.getTargetMachines(), attributesFactory,
                providers.provider<String?>(Callable { project.getGroup().toString() }), providers.provider<String?>(Callable { project.getVersion().toString() }),
                Action { variantIdentity: NativeVariantIdentity? ->
                    if (Dimensions.tryToBuildOnHost(variantIdentity)) {
                        library.getSourceCompatibility().finalizeValue()
                        val result = toolChainSelector.select<SwiftPlatform?>(
                            SwiftPlatform::class.java,
                            DefaultSwiftPlatform(variantIdentity!!.getTargetMachine(), library.getSourceCompatibility().getOrNull())
                        )

                        if (variantIdentity.getLinkage() == Linkage.SHARED) {
                            library.addSharedLibrary(
                                variantIdentity,
                                variantIdentity.isDebuggable() && !variantIdentity.isOptimized(),
                                result.getTargetPlatform(),
                                result.getToolChain(),
                                result.getPlatformToolProvider()
                            )
                        } else {
                            library.addStaticLibrary(
                                variantIdentity,
                                variantIdentity.isDebuggable() && !variantIdentity.isOptimized(),
                                result.getTargetPlatform(),
                                result.getToolChain(),
                                result.getPlatformToolProvider()
                            )
                        }
                    }
                })

            library.getBinaries().whenElementKnown<SwiftSharedLibrary?>(SwiftSharedLibrary::class.java, Action { sharedLibrary: SwiftSharedLibrary? ->
                val names = (sharedLibrary as ComponentWithNames).getNames()
                configurations.consumable(names.withSuffix("SwiftApiElements"), Action { apiElements: ConsumableConfiguration? ->
                    // TODO This should actually extend from the api dependencies, but since Swift currently
                    // requires all dependencies to be treated like api dependencies (with transitivity) we just
                    // use the implementation dependencies here.  See https://bugs.swift.org/browse/SR-1393.
                    apiElements!!.extendsFrom((sharedLibrary as DefaultSwiftSharedLibrary).getImplementationDependencies())
                    val attrs = apiElements.getAttributes()
                    attrs.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, attrs.named<Usage?>(Usage::class.java, Usage.SWIFT_API))
                    attrs.attribute<Linkage?>(CppBinary.Companion.LINKAGE_ATTRIBUTE, Linkage.SHARED)
                    attrs.attribute<Boolean?>(CppBinary.Companion.DEBUGGABLE_ATTRIBUTE, sharedLibrary.isDebuggable())
                    attrs.attribute<Boolean?>(CppBinary.Companion.OPTIMIZED_ATTRIBUTE, sharedLibrary.isOptimized())
                    attrs.attribute<OperatingSystemFamily?>(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, sharedLibrary.getTargetMachine().getOperatingSystemFamily())
                    apiElements.getOutgoing().artifact(sharedLibrary.getModuleFile())
                })
            })

            library.getBinaries().whenElementKnown<SwiftStaticLibrary?>(SwiftStaticLibrary::class.java, Action { staticLibrary: SwiftStaticLibrary? ->
                val names = (staticLibrary as ComponentWithNames).getNames()
                configurations.consumable(names.withSuffix("SwiftApiElements"), Action { apiElements: ConsumableConfiguration? ->
                    // TODO This should actually extend from the api dependencies, but since Swift currently
                    // requires all dependencies to be treated like api dependencies (with transitivity) we just
                    // use the implementation dependencies here.  See https://bugs.swift.org/browse/SR-1393.
                    apiElements!!.extendsFrom((staticLibrary as DefaultSwiftStaticLibrary).getImplementationDependencies())
                    val attrs = apiElements.getAttributes()
                    attrs.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, attrs.named<Usage?>(Usage::class.java, Usage.SWIFT_API))
                    attrs.attribute<Linkage?>(CppBinary.Companion.LINKAGE_ATTRIBUTE, Linkage.STATIC)
                    attrs.attribute<Boolean?>(CppBinary.Companion.DEBUGGABLE_ATTRIBUTE, staticLibrary.isDebuggable())
                    attrs.attribute<Boolean?>(CppBinary.Companion.OPTIMIZED_ATTRIBUTE, staticLibrary.isOptimized())
                    attrs.attribute<OperatingSystemFamily?>(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, staticLibrary.getTargetMachine().getOperatingSystemFamily())
                    apiElements.getOutgoing().artifact(staticLibrary.getModuleFile())
                })
            })
            library.getBinaries().realizeNow()
        })
    }
}
