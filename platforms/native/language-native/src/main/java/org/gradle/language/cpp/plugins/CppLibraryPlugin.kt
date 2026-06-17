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
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.tasks.bundling.Zip
import org.gradle.language.cpp.CppBinary
import org.gradle.language.cpp.CppLibrary
import org.gradle.language.cpp.CppPlatform
import org.gradle.language.cpp.CppSharedLibrary
import org.gradle.language.cpp.CppStaticLibrary
import org.gradle.language.cpp.internal.DefaultCppLibrary
import org.gradle.language.cpp.internal.DefaultCppPlatform
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.internal.NativeComponentFactory
import org.gradle.language.nativeplatform.internal.Dimensions
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector
import org.gradle.nativeplatform.Linkage
import org.gradle.nativeplatform.TargetMachineFactory
import org.gradle.nativeplatform.platform.internal.Architectures
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.io.File
import java.util.concurrent.Callable
import java.util.function.Supplier
import javax.inject.Inject

/**
 *
 * A plugin that produces a native library from C++ source.
 *
 *
 * Assumes the source files are located in `src/main/cpp`, public headers are located in `src/main/public` and implementation header files are located in `src/main/headers`.
 *
 *
 * Adds a [CppLibrary] extension to the project to allow configuration of the library.
 *
 * @since 4.1
 */
abstract class CppLibraryPlugin
/**
 * CppLibraryPlugin.
 *
 * @since 4.2
 */ @Inject constructor(
    private val componentFactory: NativeComponentFactory,
    private val toolChainSelector: ToolChainSelector,
    private val attributesFactory: AttributesFactory?,
    private val targetMachineFactory: TargetMachineFactory?
) : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(CppBasePlugin::class.java)

        val tasks = project.getTasks()
        val providers = project.getProviders()

        // Add the library and extension
        val library = componentFactory.newInstance<CppLibrary?, DefaultCppLibrary>(CppLibrary::class.java, DefaultCppLibrary::class.java, "main")
        project.getExtensions().add<CppLibrary?>(CppLibrary::class.java, "library", library)
        project.getComponents().add(library)

        // Configure the component
        library.getBaseName().convention(project.getName())
        library.getTargetMachines().convention(Dimensions.useHostAsDefaultTargetMachine(targetMachineFactory))
        library.getDevelopmentBinary().convention(project.provider<CppBinary?>(object : Callable<CppBinary?> {
            @Throws(Exception::class)
            override fun call(): CppBinary? {
                return this.debugSharedHostStream.findFirst().orElseGet(
                    Supplier {
                        this.debugStaticHostStream.findFirst().orElseGet(
                            Supplier {
                                this.debugSharedStream.findFirst().orElseGet(
                                    Supplier { this.debugStaticStream.findFirst().orElse(null) })
                            })
                    })
            }

            val debugStream: Stream<CppBinary?>?
                get() = library.getBinaries().get().stream().filter { binary: CppBinary? -> !binary!!.isOptimized() }

            val debugSharedStream: Stream<CppBinary?>?
                get() = this.debugStream.filter { obj: CppBinary? -> CppSharedLibrary::class.java.isInstance(obj) }

            val debugSharedHostStream: Stream<CppBinary?>?
                get() = this.debugSharedStream.filter { binary: CppBinary? ->
                    Architectures.forInput(
                        binary!!.getTargetMachine().getArchitecture().getName()
                    ) == DefaultNativePlatform.host().getArchitecture()
                }

            val debugStaticStream: Stream<CppBinary?>?
                get() = this.debugStream.filter { obj: CppBinary? -> CppStaticLibrary::class.java.isInstance(obj) }

            val debugStaticHostStream: Stream<CppBinary?>?
                get() = this.debugStaticStream.filter { binary: CppBinary? ->
                    Architectures.forInput(
                        binary!!.getTargetMachine().getArchitecture().getName()
                    ) == DefaultNativePlatform.host().getArchitecture()
                }
        }))

        library.getBinaries().whenElementKnown(Action { binary: CppBinary? ->
            library.getMainPublication().addVariant(binary)
        })

        project.afterEvaluate(Action { p: Project? ->
            // TODO: make build type configurable for components
            Dimensions.libraryVariants(
                library.getBaseName(), library.getLinkage(), library.getTargetMachines(), attributesFactory,
                providers.provider<String?>(Callable { project.getGroup().toString() }), providers.provider<String?>(Callable { project.getVersion().toString() }),
                Action { variantIdentity: NativeVariantIdentity? ->
                    if (Dimensions.tryToBuildOnHost(variantIdentity)) {
                        val result = toolChainSelector.select<CppPlatform?>(CppPlatform::class.java, DefaultCppPlatform(variantIdentity!!.getTargetMachine()))

                        if (variantIdentity.getLinkage() == Linkage.SHARED) {
                            library.addSharedLibrary(variantIdentity, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider())
                        } else {
                            library.addStaticLibrary(variantIdentity, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider())
                        }
                    } else {
                        // Known, but not buildable
                        library.getMainPublication().addVariant(variantIdentity)
                    }
                })

            // TODO - deal with more than one header dir, e.g. generated public headers
            val publicHeaders = providers.provider<File?>(Callable {
                val files: MutableSet<File?> = library.getPublicHeaderDirs().getFiles()
                if (files.size != 1) {
                    throw UnsupportedOperationException(
                        String.format(
                            "The C++ library plugin currently requires exactly one public header directory, however there are %d directories configured: %s",
                            files.size,
                            files
                        )
                    )
                }
                files.iterator().next()
            })
            library.getApiElements().configure(Action { conf: ConsumableConfiguration? ->
                conf!!.getOutgoing().artifact(publicHeaders, Action { it: ConfigurablePublishArtifact? -> it!!.builtBy(library.getPublicHeaderDirs()) })
            })

            project.getPluginManager().withPlugin("maven-publish", Action { appliedPlugin: AppliedPlugin? ->
                val headersZip = tasks.register<Zip?>("cppHeaders", Zip::class.java, Action { task: Zip? ->
                    task!!.from(library.getPublicHeaderFiles())
                    task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("headers"))
                    task.getArchiveClassifier().set("cpp-api-headers")
                    task.getArchiveFileName().set("cpp-api-headers.zip")
                })
                library.getMainPublication().addArtifact(LazyPublishArtifact(headersZip, (project as ProjectInternal).getFileResolver(), project.getTaskDependencyFactory()))
            })
            library.getBinaries().realizeNow()
        })
    }
}
