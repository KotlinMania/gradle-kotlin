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
package org.gradle.nativeplatform.test.cpp.plugins

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.provider.SetProperty
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.cpp.CppApplication
import org.gradle.language.cpp.CppBinary
import org.gradle.language.cpp.CppComponent.getBinaries
import org.gradle.language.cpp.CppPlatform
import org.gradle.language.cpp.ProductionCppComponent
import org.gradle.language.cpp.internal.DefaultCppBinary
import org.gradle.language.cpp.internal.DefaultCppComponent.getBinaries
import org.gradle.language.cpp.internal.DefaultCppComponent.getName
import org.gradle.language.cpp.internal.DefaultCppComponent.getNames
import org.gradle.language.cpp.internal.DefaultCppPlatform
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.cpp.internal.NativeVariantIdentity.getName
import org.gradle.language.cpp.plugins.CppBasePlugin
import org.gradle.language.internal.DefaultBinaryCollection.realizeNow
import org.gradle.language.internal.DefaultBinaryCollection.whenElementKnown
import org.gradle.language.internal.DefaultNativeBinary.getNames
import org.gradle.language.internal.NativeComponentFactory
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithLinkUsage
import org.gradle.language.nativeplatform.internal.Dimensions
import org.gradle.language.nativeplatform.internal.Dimensions.tryToBuildOnHost
import org.gradle.language.nativeplatform.internal.Dimensions.useHostAsDefaultTargetMachine
import org.gradle.language.nativeplatform.internal.Names.getTaskName
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector
import org.gradle.language.nativeplatform.tasks.UnexportMainSymbol
import org.gradle.nativeplatform.TargetMachine
import org.gradle.nativeplatform.TargetMachineFactory
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.Companion.currentArchitecture
import org.gradle.nativeplatform.test.cpp.CppTestExecutable
import org.gradle.nativeplatform.test.cpp.CppTestSuite
import org.gradle.nativeplatform.test.cpp.internal.DefaultCppTestExecutable
import org.gradle.nativeplatform.test.cpp.internal.DefaultCppTestSuite
import org.gradle.nativeplatform.test.plugins.NativeTestingBasePlugin
import org.gradle.nativeplatform.test.tasks.RunTestExecutable
import java.util.concurrent.Callable
import java.util.function.Supplier
import javax.inject.Inject

/**
 * A plugin that sets up the infrastructure for testing C++ binaries using a simple test executable.
 *
 * Gradle will create a [RunTestExecutable] task that relies on the exit code of the binary.
 *
 * @since 4.4
 */
abstract class CppUnitTestPlugin @Inject constructor(
    private val componentFactory: NativeComponentFactory,
    private val toolChainSelector: ToolChainSelector,
    private val attributesFactory: AttributesFactory,
    private val targetMachineFactory: TargetMachineFactory
) : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(CppBasePlugin::class.java)
        project.getPluginManager().apply(NativeTestingBasePlugin::class.java)

        val providers = project.getProviders()
        val tasks = project.getTasks()

        // Add the unit test and extension
        val testComponent = componentFactory.newInstance<CppTestSuite?, DefaultCppTestSuite?>(CppTestSuite::class.java, DefaultCppTestSuite::class.java, "test")
        project.getExtensions().add<CppTestSuite?>(CppTestSuite::class.java, "unitTest", testComponent)
        project.getComponents().add(testComponent!!)

        testComponent.baseName.convention(project.getName() + "Test")
        testComponent.targetMachines.convention(useHostAsDefaultTargetMachine(targetMachineFactory))

        val mainComponentName = "main"
        project.getComponents().withType<ProductionCppComponent?>(ProductionCppComponent::class.java, Action { component: ProductionCppComponent? ->
            if (mainComponentName == component!!.getName()) {
                testComponent.targetMachines.convention(component.targetMachines)
                testComponent.getTestedComponent().convention(component)
            }
        })

        testComponent.getTestBinary().convention(project.provider<CppTestExecutable?>(object : Callable<CppTestExecutable?> {
            @Throws(Exception::class)
            override fun call(): CppTestExecutable? {
                return this.allBuildableTestExecutable
                    .filter { it: DefaultCppTestExecutable? -> isCurrentArchitecture(it!!.nativePlatform) }
                    .findFirst()
                    .orElseGet(
                        Supplier {
                            this.allBuildableTestExecutable.findFirst().orElseGet(
                                Supplier { this.allTestExecutable.findFirst().orElse(null) })
                        })
            }

            fun isCurrentArchitecture(targetPlatform: NativePlatform): Boolean {
                return targetPlatform.architecture!!.equals(currentArchitecture)
            }

            val allBuildableTestExecutable: Stream<DefaultCppTestExecutable?>?
                get() = this.allTestExecutable.filter { it: DefaultCppTestExecutable? -> it!!.platformToolProvider!!.isAvailable }

            val allTestExecutable: Stream<DefaultCppTestExecutable?>?
                get() = testComponent.getBinaries().get().stream()
                    .filter { obj: CppBinary? -> CppTestExecutable::class.java.isInstance(obj) }
                    .map<DefaultCppTestExecutable?> { obj: CppBinary? ->
                        DefaultCppTestExecutable::class.java.cast(
                            obj
                        )
                    }
        }))

        testComponent.getBinaries().whenElementKnown<DefaultCppTestExecutable?>(DefaultCppTestExecutable::class.java, Action { binary: DefaultCppTestExecutable? ->
            // TODO: Replace with native test task
            val testTask = tasks.register<RunTestExecutable?>(binary.getNames().getTaskName("run"), RunTestExecutable::class.java, Action { task: RunTestExecutable? ->
                task!!.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP)
                task.setDescription("Executes C++ unit tests.")

                val installTask = binary!!.installTask!!.get()
                val installDirectory: DirectoryProperty = binary.installDirectory
                task.onlyIf(
                    "Test executable installation directory exists",
                    SerializableLambdas.spec<Task?>(SerializableLambdas.SerializableSpec { t: Task? -> installDirectory.get().getAsFile().exists() })
                )
                task.getInputs()
                    .dir(installDirectory)
                    .withPropertyName("installDirectory")
                task.setExecutable(installTask.runScriptFile.get().getAsFile())
                task.dependsOn(installDirectory)
                // TODO: Honor changes to build directory
                task.outputDir = project.getLayout().getBuildDirectory().dir("test-results/" + binary.getNames().dirName).get().getAsFile()
            })
            binary.getRunTask().set(testTask)
            configureTestSuiteWithTestedComponentWhenAvailable(project, testComponent, binary!!)
        })

        project.afterEvaluate(Action { p: Project? ->
            val mainComponent = testComponent.getTestedComponent().getOrNull()
            val mainTargetMachines: SetProperty<TargetMachine?>? = if (mainComponent != null) mainComponent.targetMachines else null
            Dimensions.unitTestVariants(
                testComponent.baseName, testComponent.targetMachines, mainTargetMachines,
                attributesFactory,
                providers.provider<String?>(Callable { project.getGroup().toString() }), providers.provider<String?>(Callable { project.getVersion().toString() }),
                Action { variantIdentity: NativeVariantIdentity? ->
                    if (tryToBuildOnHost(variantIdentity!!)) {
                        val result = toolChainSelector.select<CppPlatform?>(CppPlatform::class.java, DefaultCppPlatform(variantIdentity.targetMachine))
                        // TODO: Removing `debug` from variant name to keep parity with previous Gradle version in tooling models
                        testComponent.addExecutable(variantIdentity.getName()!!.replace("debug", ""), variantIdentity, result!!.targetPlatform, result.toolChain, result.platformToolProvider)
                    }
                })
            // TODO: Publishing for test executable?
            testComponent.getBinaries().realizeNow()
        })
    }

    private fun configureTestSuiteWithTestedComponentWhenAvailable(project: Project, testSuite: DefaultCppTestSuite, testExecutable: DefaultCppTestExecutable) {
        val target = testSuite.getTestedComponent().getOrNull()
        if (target !is ProductionCppComponent) {
            return
        }
        val testedComponent = target

        val tasks = project.getTasks()
        testedComponent.getBinaries()!!.whenElementFinalized({ testedBinary: CppBinary? ->
            if (!isTestedBinary(testExecutable, testedComponent, testedBinary!!)) {
                return@whenElementFinalized
            }
            // TODO - move this to a base plugin
            // Setup the dependency on the main binary
            // This should all be replaced by a single dependency that points at some "testable" variants of the main binary

            // Inherit implementation dependencies
            testExecutable.implementationDependencies!!.extendsFrom((testedBinary as DefaultCppBinary).implementationDependencies!!)

            // Configure test binary to link against tested component compiled objects
            val testableObjects = project.files()
            if (target is CppApplication) {
                // TODO - this should be an outgoing variant of the component under test
                val unexportMainSymbol =
                    tasks.register<UnexportMainSymbol?>(testExecutable.getNames().getTaskName("relocateMainFor"), UnexportMainSymbol::class.java, Action { task: UnexportMainSymbol? ->
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

    private fun isTestedBinary(testExecutable: DefaultCppTestExecutable, mainComponent: ProductionCppComponent, testedBinary: CppBinary): Boolean {
        // TODO: Make this more intelligent by matching the attributes of the runtime usage on the variant identities
        return testedBinary.targetMachine.getOperatingSystemFamily().getName().equals(testExecutable.getTargetMachine()!!.operatingSystemFamily!!.getName())
                && testedBinary.targetMachine.getArchitecture().getName().equals(testExecutable.getTargetMachine()!!.architecture!!.getName())
                && !testedBinary.isOptimized && hasDevelopmentBinaryLinkage(mainComponent, testedBinary)
    }

    private fun hasDevelopmentBinaryLinkage(mainComponent: ProductionCppComponent, testedBinary: CppBinary?): Boolean {
        if (testedBinary !is ConfigurableComponentWithLinkUsage) {
            return true
        }
        val developmentBinaryWithUsage = mainComponent.getDevelopmentBinary()!!.get() as ConfigurableComponentWithLinkUsage
        val testedBinaryWithUsage = testedBinary as ConfigurableComponentWithLinkUsage
        return testedBinaryWithUsage.linkage === developmentBinaryWithUsage.linkage
    }
}
