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
package org.gradle.nativeplatform.test.xctest.plugins

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.internal.DefaultBinaryCollection.realizeNow
import org.gradle.language.internal.NativeComponentFactory
import org.gradle.language.nativeplatform.internal.Dimensions
import org.gradle.language.nativeplatform.internal.Dimensions.tryToBuildOnHost
import org.gradle.language.nativeplatform.internal.Dimensions.useHostAsDefaultTargetMachine
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector
import org.gradle.language.nativeplatform.tasks.UnexportMainSymbol
import org.gradle.language.swift.ProductionSwiftComponent
import org.gradle.language.swift.SwiftApplication
import org.gradle.language.swift.SwiftBinary
import org.gradle.language.swift.SwiftComponent
import org.gradle.language.swift.SwiftPlatform
import org.gradle.language.swift.internal.DefaultSwiftBinary
import org.gradle.language.swift.internal.DefaultSwiftBinary.isDebuggable
import org.gradle.language.swift.internal.DefaultSwiftPlatform
import org.gradle.language.swift.plugins.SwiftBasePlugin
import org.gradle.language.swift.tasks.SwiftCompile
import org.gradle.nativeplatform.TargetMachine
import org.gradle.nativeplatform.TargetMachineFactory
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkMachOBundle
import org.gradle.nativeplatform.test.plugins.NativeTestingBasePlugin
import org.gradle.nativeplatform.test.xctest.SwiftXCTestBinary
import org.gradle.nativeplatform.test.xctest.SwiftXCTestBundle
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXCTestBinary
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXCTestBundle
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXCTestExecutable
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXCTestSuite
import org.gradle.nativeplatform.test.xctest.tasks.InstallXCTestBundle
import org.gradle.nativeplatform.test.xctest.tasks.XCTest
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.nativeplatform.toolchain.internal.NativeLanguage
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal
import org.gradle.nativeplatform.toolchain.internal.xcode.MacOSSdkPlatformPathLocator
import org.gradle.util.internal.TextUtil
import java.io.File
import java.util.Arrays
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * A plugin that sets up the infrastructure for testing native binaries with XCTest test framework. It also adds conventions on top of it.
 *
 * @since 4.2
 */
abstract class XCTestConventionPlugin @Inject constructor(
    private val sdkPlatformPathLocator: MacOSSdkPlatformPathLocator,
    private val toolChainSelector: ToolChainSelector,
    private val componentFactory: NativeComponentFactory,
    private val attributesFactory: AttributesFactory,
    private val targetMachineFactory: TargetMachineFactory
) : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(SwiftBasePlugin::class.java)
        project.getPluginManager().apply(NativeTestingBasePlugin::class.java)

        val providers = project.getProviders()

        // Create test suite component
        // TODO - Reuse logic from Swift*Plugin
        // TODO - component name and extension name aren't the same
        // TODO - should use `src/xctest/swift` as the convention?
        // Add the test suite and extension
        val testSuite = componentFactory.newInstance<SwiftXCTestSuite?, DefaultSwiftXCTestSuite?>(SwiftXCTestSuite::class.java, DefaultSwiftXCTestSuite::class.java, "test")

        project.getExtensions().add<SwiftXCTestSuite?>(SwiftXCTestSuite::class.java, "xctest", testSuite)
        project.getComponents().add(testSuite!!)

        // Setup component
        testSuite.module.set(TextUtil.toCamelCase(project.getName() + "Test"))

        val testComponent = testSuite

        testComponent.targetMachines.convention(useHostAsDefaultTargetMachine(targetMachineFactory))
        testComponent.sourceCompatibility.convention(testComponent.getTestedComponent().flatMap<S?>(Transformer { it: SwiftComponent? -> it!!.sourceCompatibility }))
        val mainComponentName = "main"

        project.getComponents().withType<ProductionSwiftComponent?>(ProductionSwiftComponent::class.java, Action { component: ProductionSwiftComponent? ->
            if (mainComponentName == component!!.getName()) {
                testComponent.targetMachines.convention(component.targetMachines)
                testComponent.getTestedComponent().convention(component)
            }
        })

        testComponent.getTestBinary().convention(project.provider<SwiftXCTestBinary?>(Callable {
            testComponent.getBinaries()!!.get()!!.stream()
                .filter { obj: SwiftXCTestBinary? -> SwiftXCTestBinary::class.java.isInstance(obj) }
                .map<SwiftXCTestBinary?> { obj: SwiftXCTestBinary? -> SwiftXCTestBinary::class.java.cast(obj) }
                .findFirst()
                .orElse(null)
        }))

        testComponent.getBinaries()!!.whenElementKnown<DefaultSwiftXCTestBinary?>(DefaultSwiftXCTestBinary::class.java, Action { binary: DefaultSwiftXCTestBinary? ->
            // Create test suite test task
            val testingTask = project.getTasks().register<XCTest?>("xcTest", XCTest::class.java, Action { task: XCTest? ->
                task!!.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP)
                task.setDescription("Executes XCTest suites")
                task.getTestInstallDirectory().set(binary!!.getInstallDirectory())
                task.getRunScriptFile().set(binary.getRunScriptFile())
                task.getWorkingDirectory().set(binary.getInstallDirectory())
            })
            binary!!.getRunTask().set(testingTask)

            configureTestSuiteBuildingTasks(project, binary)
            configureTestSuiteWithTestedComponentWhenAvailable(project, testComponent, binary)
        })

        project.afterEvaluate(Action { p: Project? ->
            val mainComponent = testComponent.getTestedComponent().getOrNull()
            val mainTargetMachines: SetProperty<TargetMachine?>? = if (mainComponent != null) mainComponent.targetMachines else null
            Dimensions.unitTestVariants(
                testComponent.module, testComponent.targetMachines, mainTargetMachines,
                attributesFactory,
                providers.provider<String?>(Callable { project.getGroup().toString() }), providers.provider<String?>(Callable { project.getVersion().toString() }),
                Action { variantIdentity: NativeVariantIdentity? ->
                    if (tryToBuildOnHost(variantIdentity!!)) {
                        testComponent.sourceCompatibility.finalizeValue()
                        val result =
                            toolChainSelector.select<SwiftPlatform?>(SwiftPlatform::class.java, DefaultSwiftPlatform(variantIdentity.targetMachine, testComponent.sourceCompatibility.getOrNull()))

                        // Create test suite executable
                        if (result!!.targetPlatform!!.targetMachine.getOperatingSystemFamily().isMacOs()) {
                            testComponent.addBundle(variantIdentity, result.targetPlatform, result.toolChain, result.platformToolProvider)
                        } else {
                            testComponent.addExecutable(variantIdentity, result.targetPlatform, result.toolChain, result.platformToolProvider)
                        }
                    }
                })
            testComponent.getBinaries().realizeNow()
        })
    }

    private fun configureTestSuiteBuildingTasks(project: Project, binary: DefaultSwiftXCTestBinary) {
        // Overwrite the source to exclude `LinuxMain.swift`
        val compile: SwiftCompile = binary.getCompileTask().get()!!
        compile.source.setFrom(binary.getSwiftSource()!!.getAsFileTree().matching(Action { patterns: PatternFilterable? -> patterns!!.include("**/*").exclude("**/LinuxMain.swift") }))

        if (binary is SwiftXCTestBundle) {
            val tasks = project.getTasks()
            val names = binary.getNames()

            // TODO - creating a bundle should be done by some general purpose plugin

            // TODO - make this lazy
            val currentPlatform = DefaultNativePlatform("current")
            val toolChainRegistry: NativeToolChainRegistryInternal = org.gradle.internal.Cast.uncheckedCast<NativeToolChainRegistryInternal?>(
                project.getExtensions().getByType<org.gradle.nativeplatform.toolchain.NativeToolChainRegistry?>(org.gradle.nativeplatform.toolchain.NativeToolChainRegistry::class.java)
            )!!
            val toolChain: NativeToolChain? = toolChainRegistry.getForPlatform(NativeLanguage.SWIFT, currentPlatform)

            // Platform specific arguments
            // TODO: Need to lazily configure compile task
            // TODO: Ultimately, this should be some kind of 3rd party dependency that's visible to dependency management.
            compile.compilerArgs.addAll(project.provider<T?>(Callable {
                val platformSdkPath = sdkPlatformPathLocator.find()
                val frameworkDir = File(platformSdkPath, "Developer/Library/Frameworks")
                // Since Xcode 11/12, the XCTest framework is being replaced by a different library that's available in the sdk root
                val extraInclude = File(platformSdkPath, "Developer/usr/lib")
                Arrays.asList<T?>("-parse-as-library", "-F" + frameworkDir.getAbsolutePath(), "-I", extraInclude.getAbsolutePath(), "-v")
            }))

            // Add a link task
            val link = tasks.register<LinkMachOBundle?>(names.getTaskName("link"), LinkMachOBundle::class.java, Action { task: LinkMachOBundle? ->
                task!!.linkerArgs.set(project.provider<T?>(Callable {
                    val platformSdkPath = sdkPlatformPathLocator.find()
                    val frameworkDir = File(platformSdkPath, "Developer/Library/Frameworks")
                    // Since Xcode 11/12, the XCTest framework is being replaced by a different library that's available in the sdk root
                    val extraInclude = File(platformSdkPath, "Developer/usr/lib")
                    Arrays.asList<T?>(
                        "-F" + frameworkDir.getAbsolutePath(),
                        "-L", extraInclude.getAbsolutePath(),
                        "-framework", "XCTest",
                        "-Xlinker", "-rpath", "-Xlinker", "@executable_path/../Frameworks",
                        "-Xlinker", "-rpath", "-Xlinker", "@loader_path/../Frameworks"
                    )
                }))
                task.source(binary.getObjects())
                task.lib(binary.getLinkLibraries()!!)
                val toolProvider = (toolChain as NativeToolChainInternal).select(currentPlatform)

                val exeLocation: Provider<RegularFile?> = project.getLayout().getBuildDirectory()
                    .file(binary.getBaseName()!!.map<String?>(Transformer { baseName: String? -> toolProvider!!.getExecutableName("exe/" + names.dirName + baseName) }))
                task.linkedFile.set(exeLocation)
                task.targetPlatform!!.set(currentPlatform)
                task.toolChain!!.set(toolChain)
                task.getDebuggable().set(binary.isDebuggable())
            })


            val install = tasks.register<InstallXCTestBundle?>(names.getTaskName("install"), InstallXCTestBundle::class.java, Action { task: InstallXCTestBundle? ->
                task!!.getBundleBinaryFile().set(link.get()!!.linkedFile)
                task.getInstallDirectory().set(project.getLayout().getBuildDirectory().dir("install/" + names.dirName))
            })
            binary.getInstallDirectory().set(install.flatMap<Directory?>(Transformer { task: InstallXCTestBundle? -> task!!.getInstallDirectory() }))
            binary.getExecutableFile().set(link.flatMap<RegularFile?>(Transformer { task: LinkMachOBundle? -> task!!.linkedFile }))

            val bundle = binary as DefaultSwiftXCTestBundle
            bundle.getLinkTask().set(link)
            bundle.getRunScriptFile().set(install.flatMap<RegularFile?>(Transformer { task: InstallXCTestBundle? -> task!!.getRunScriptFile() }))
        } else {
            val executable = binary as DefaultSwiftXCTestExecutable
            executable.getRunScriptFile().set(executable.installTask!!.flatMap<RegularFile?>(Transformer { task: InstallExecutable? -> task!!.runScriptFile }))

            // Rename `LinuxMain.swift` to `main.swift` so the entry point is correctly detected by swiftc
            if (binary.getTargetMachine()!!.operatingSystemFamily.isLinux()) {
                val renameLinuxMainTask = project.getTasks().register<Sync?>("renameLinuxMain", Sync::class.java, Action { task: Sync? ->
                    task!!.from(binary.getSwiftSource())
                    task.into(project.getLayout().getBuildDirectory().dir("linuxMain"))
                    task.include("LinuxMain.swift")
                    task.rename(".*", "main.swift")
                })
                compile.source.from(
                    project.files(renameLinuxMainTask.map<File?>(Transformer { obj: Sync? -> obj!!.getDestinationDir() })).getAsFileTree()
                        .matching(Action { patterns: PatternFilterable? -> patterns!!.include("**/*.swift") })
                )
            }
        }
    }

    private fun configureTestSuiteWithTestedComponentWhenAvailable(project: Project, testSuite: DefaultSwiftXCTestSuite, testExecutable: DefaultSwiftXCTestBinary) {
        val target = testSuite.getTestedComponent().getOrNull()
        if (target !is ProductionSwiftComponent) {
            return
        }
        val testedComponent = target

        val tasks = project.getTasks()
        testedComponent.getBinaries()!!.whenElementFinalized({ testedBinary: SwiftBinary? ->
            if (testedBinary !== testedComponent.getDevelopmentBinary()!!.get()) {
                return@whenElementFinalized
            }
            // Setup the dependency on the main binary
            // This should all be replaced by a single dependency that points at some "testable" variants of the main binary

            // Inherit implementation dependencies
            testExecutable.implementationDependencies!!.extendsFrom((testedBinary as DefaultSwiftBinary).implementationDependencies!!)

            // Configure test binary to compile against binary under test
            val compileDependency = project.getDependencies().create(project.files(testedBinary.moduleFile))
            testExecutable.importPathConfiguration.getDependencies().add(compileDependency)

            // Configure test binary to link against tested component compiled objects
            val testableObjects = project.files()
            if (testedComponent is SwiftApplication) {
                val unexportMainSymbol = tasks.register<UnexportMainSymbol?>("relocateMainForTest", UnexportMainSymbol::class.java, Action { task: UnexportMainSymbol? ->
                    val dirName = testedBinary.getNames().dirName
                    task!!.outputDirectory.set(project.getLayout().getBuildDirectory().dir("obj/for-test/" + dirName))
                    task.objects.from(testedBinary.objects)
                })
                testableObjects.from(unexportMainSymbol.map<Any?>(Transformer { task: UnexportMainSymbol? -> task!!.relocatedObjects }))
            } else {
                testableObjects.from(testedBinary.objects)
            }
            val linkDependency = project.getDependencies().create(testableObjects)
            testExecutable.linkConfiguration!!.getDependencies().add(linkDependency)
        })
    }
}
