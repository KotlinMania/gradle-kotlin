/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.nativeplatform.test.plugins

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.internal.resolve.ProjectModelResolver
import org.gradle.api.tasks.PathSensitivity
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.nativeplatform.DependentSourceSet
import org.gradle.model.Defaults
import org.gradle.model.Each
import org.gradle.model.Finalize
import org.gradle.model.ModelMap
import org.gradle.model.RuleSource
import org.gradle.model.internal.type.ModelTypes
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal
import org.gradle.nativeplatform.internal.NativeDependentBinariesResolutionStrategy
import org.gradle.nativeplatform.plugins.NativeComponentModelPlugin
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec
import org.gradle.nativeplatform.test.internal.DefaultNativeTestSuiteBinarySpec
import org.gradle.nativeplatform.test.internal.NativeDependentBinariesResolutionStrategyTestSupport
import org.gradle.nativeplatform.test.internal.NativeTestSuiteBinarySpecInternal
import org.gradle.nativeplatform.test.tasks.RunTestExecutable
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentType
import org.gradle.platform.base.TypeBuilder
import org.gradle.platform.base.internal.dependents.DependentBinariesResolver
import org.gradle.testing.base.TestSuiteContainer
import org.gradle.testing.base.plugins.TestingModelBasePlugin
import java.io.File
import java.util.concurrent.Callable

/**
 * A plugin that sets up the infrastructure for testing native binaries.
 */
@Incubating
abstract class NativeBinariesTestPlugin : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(NativeComponentModelPlugin::class.java)
        project.getPluginManager().apply(TestingModelBasePlugin::class.java)
    }

    internal class Rules : RuleSource() {
        @ComponentType
        fun nativeTestSuiteBinary(builder: TypeBuilder<NativeTestSuiteBinarySpec?>) {
            builder.defaultImplementation(DefaultNativeTestSuiteBinarySpec::class.java)
            builder.internalView(NativeTestSuiteBinarySpecInternal::class.java)
        }

        @Finalize
        fun attachTestedBinarySourcesToTestBinaries(@Each testSuiteBinary: NativeTestSuiteBinarySpecInternal) {
            val testedBinary: BinarySpec = testSuiteBinary.getTestedBinary()
            testSuiteBinary.getInputs().withType<DependentSourceSet?>(DependentSourceSet::class.java).all(object : Action<DependentSourceSet?> {
                override fun execute(testSource: DependentSourceSet) {
                    testSource.lib(testedBinary.getInputs())
                }
            })
            testedBinary.getInputs().all(object : Action<LanguageSourceSet?> {
                override fun execute(testedSource: LanguageSourceSet?) {
                    testSuiteBinary.getInputs().add(testedSource!!)
                }
            })
        }

        @Finalize
        fun configureRunTask(@Each testSuiteBinary: NativeTestSuiteBinarySpecInternal) {
            val namingScheme = testSuiteBinary.getNamingScheme()
            val tasks = testSuiteBinary.getTasks()
            val installTask = tasks.getInstall() as InstallExecutable
            val runTask = tasks.getRun() as RunTestExecutable
            runTask.getInputs().files(installTask.getOutputs().getFiles()).withPropertyName("installTask.outputs").withPathSensitivity(PathSensitivity.RELATIVE)
            runTask.setExecutable(installTask.runScriptFile.get().getAsFile().getPath())
            val project = runTask.getProject()
            runTask.setOutputDir(project.getLayout().getBuildDirectory().getAsFile().map<File?>(Transformer { it: File? -> namingScheme.getOutputDirectory(it, "test-results") }).get())
        }

        @Defaults
        fun registerNativeDependentBinariesResolutionStrategyTestSupport(resolver: DependentBinariesResolver) {
            val nativeStrategy =
                resolver.getStrategy<NativeDependentBinariesResolutionStrategy?>(NativeDependentBinariesResolutionStrategy.Companion.NAME, NativeDependentBinariesResolutionStrategy::class.java)
            nativeStrategy!!.setTestSupport(NativeDependentBinariesResolutionStrategyTestSupport())
        }

        @Finalize
        fun wireBuildDependentsTasks(
            tasks: ModelMap<Task?>,
            testSuites: TestSuiteContainer?,
            binaries: BinaryContainer,
            dependentsResolver: DependentBinariesResolver,
            serviceRegistry: ServiceRegistry
        ) {
            val projectModelResolver = serviceRegistry.get<ProjectModelResolver?>(ProjectModelResolver::class.java)
            val nativeBinaries = binaries.withType<NativeBinarySpecInternal?>(NativeBinarySpecInternal::class.java)
            for (binary in nativeBinaries) {
                val buildDependents = tasks.get(binary.getNamingScheme().getTaskName("buildDependents"))
                val deferredDependencies: Callable<Iterable<Task?>?> = object : Callable<Iterable<Task?>?> {
                    override fun call(): Iterable<Task?> {
                        val dependencies: MutableList<Task?> = ArrayList<Task?>()
                        val result = dependentsResolver.resolve(binary).getRoot()
                        for (dependent in result.getChildren()) {
                            if (dependent.isBuildable() && dependent.isTestSuite()) {
                                val modelRegistry = projectModelResolver!!.resolveProjectModel(dependent.getId().getProjectPath())
                                val projectBinaries =
                                    modelRegistry!!.realize<ModelMap<NativeBinarySpecInternal?>>("binaries", ModelTypes.modelMap<NativeBinarySpecInternal?>(NativeBinarySpecInternal::class.java))
                                val dependentBinary = projectBinaries.get(dependent.getProjectScopedName())
                                val testSuiteBinary = dependentBinary as NativeTestSuiteBinarySpecInternal?
                                dependencies.add(testSuiteBinary!!.getCheckTask())
                            }
                        }
                        return dependencies
                    }
                }
                buildDependents!!.dependsOn(deferredDependencies)
            }
            for (testSuiteBinary in nativeBinaries.withType<NativeTestSuiteBinarySpecInternal>(NativeTestSuiteBinarySpecInternal::class.java)) {
                val buildDependents = tasks.get(testSuiteBinary.getNamingScheme().getTaskName("buildDependents"))
                buildDependents!!.dependsOn(testSuiteBinary.getCheckTask()!!)
            }
        }
    }
}
