/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.nativeplatform.test.internal

import org.gradle.api.Action
import org.gradle.internal.service.ServiceRegistry
import org.gradle.model.ModelMap
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeComponentSpec
import org.gradle.nativeplatform.SharedLibraryBinary
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal
import org.gradle.nativeplatform.internal.NativeComponents
import org.gradle.nativeplatform.internal.configure.NativeBinaryRules
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec
import org.gradle.nativeplatform.test.NativeTestSuiteSpec
import org.gradle.nativeplatform.test.tasks.RunTestExecutable
import org.gradle.platform.base.InvalidModelException
import org.gradle.platform.base.VariantComponentSpec
import org.gradle.platform.base.internal.BinaryNamingScheme
import org.gradle.testing.base.TestSuiteContainer
import java.io.File

/**
 * Functions for creation and configuration of native test suites.
 */
object NativeTestSuites {
    fun <S : NativeTestSuiteBinarySpec?> createNativeTestSuiteBinaries(
        binaries: ModelMap<S?>,
        testSuite: NativeTestSuiteSpec,
        testSuiteBinaryClass: Class<S?>?,
        typeString: String?, buildDir: File?, serviceRegistry: ServiceRegistry
    ) {
        for (testedBinary in testedBinariesOf(testSuite)!!) {
            if (testedBinary is SharedLibraryBinary) {
                // For now, we only create test suites for static library variants
                continue
            }
            createNativeTestSuiteBinary<S?>(binaries, testSuite, testSuiteBinaryClass, typeString, testedBinary, buildDir, serviceRegistry)
        }
    }

    private fun <S : NativeTestSuiteBinarySpec?> createNativeTestSuiteBinary(
        binaries: ModelMap<S?>,
        testSuite: NativeTestSuiteSpec,
        testSuiteBinaryClass: Class<S?>?,
        typeString: String?,
        testedBinary: NativeBinarySpec?,
        buildDir: File?, serviceRegistry: ServiceRegistry
    ) {
        val namingScheme = NativeTestSuites.namingSchemeFor(testSuite, (testedBinary as org.gradle.nativeplatform.internal.NativeBinarySpecInternal?)!!, typeString)
        val resolver = serviceRegistry.get<NativeDependencyResolver?>(NativeDependencyResolver::class.java)

        binaries.create<S?>(namingScheme.getBinaryName(), testSuiteBinaryClass, object : Action<S?> {
            override fun execute(binary: S?) {
                val testBinary = binary as NativeTestSuiteBinarySpecInternal
                testBinary.setTestedBinary(testedBinary as NativeBinarySpecInternal)
                testBinary.setNamingScheme(namingScheme)
                testBinary.setResolver(resolver)
                testBinary.setToolChain(testedBinary.getToolChain())
                val executable = testBinary.getExecutable()
                val installation = testBinary.getInstallation()
                executable.setToolChain(testedBinary.getToolChain())
                executable.setFile(NativeBinaryRules.executableFileFor(testBinary, buildDir))
                installation.setDirectory(NativeBinaryRules.installationDirFor(testBinary, buildDir))
                NativeComponents.createInstallTask(testBinary, installation, executable, namingScheme)
                NativeComponents.createExecutableTask(testBinary, testBinary.getExecutableFile())
                createRunTask(testBinary, namingScheme.getTaskName("run"))
            }
        })
    }

    private fun createRunTask(testBinary: NativeTestSuiteBinarySpecInternal, name: String?) {
        testBinary.getTasks().create<RunTestExecutable?>(name, RunTestExecutable::class.java, object : Action<RunTestExecutable?> {
            override fun execute(runTask: RunTestExecutable) {
                runTask.setDescription("Runs the " + testBinary)
                testBinary.getTasks().add(runTask)
            }
        })
    }

    fun testedBinariesOf(testSuite: NativeTestSuiteSpec): MutableCollection<NativeBinarySpec?>? {
        return NativeTestSuites.testedBinariesWithType<NativeBinarySpec?>(NativeBinarySpec::class.java, testSuite)
    }

    fun <S> testedBinariesWithType(type: Class<S?>?, testSuite: NativeTestSuiteSpec): MutableCollection<S?>? {
        val spec = testSuite.getTestedComponent() as VariantComponentSpec
        if (spec == null) {
            throw InvalidModelException(String.format("Test suite '%s' doesn't declare component under test. Please specify it with `testing $.components.myComponent`.", testSuite.getName()))
        }
        return spec.getBinaries().withType<S?>(type).values()
    }

    private fun namingSchemeFor(testSuite: NativeTestSuiteSpec, testedBinary: NativeBinarySpecInternal, typeString: String?): BinaryNamingScheme {
        return testedBinary.getNamingScheme()
            .withComponentName(testSuite.getBaseName())
            .withBinaryType(typeString)
            .withRole("executable", true)
    }

    fun <S : NativeTestSuiteSpec?> createConventionalTestSuites(testSuites: TestSuiteContainer, components: ModelMap<NativeComponentSpec>, testSuiteSpecClass: Class<S?>?) {
        for (component in components.values()) {
            val suiteName = component.getName() + "Test"
            testSuites.create<S?>(suiteName, testSuiteSpecClass, object : Action<S?> {
                override fun execute(testSuite: S?) {
                    testSuite!!.testing(component)
                }
            })
        }
    }
}
