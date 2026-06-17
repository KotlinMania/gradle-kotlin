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
package org.gradle.nativeplatform.test.cpp.internal

import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.internal.Describables
import org.gradle.language.ComponentDependencies
import org.gradle.language.cpp.CppComponent
import org.gradle.language.cpp.CppPlatform
import org.gradle.language.cpp.internal.DefaultCppComponent
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.internal.DefaultComponentDependencies
import org.gradle.language.internal.NativeComponentFactory.newInstance
import org.gradle.language.nativeplatform.internal.Names.Companion.of
import org.gradle.nativeplatform.test.cpp.CppTestExecutable
import org.gradle.nativeplatform.test.cpp.CppTestSuite
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import javax.inject.Inject

abstract class DefaultCppTestSuite @Inject constructor(name: String) : DefaultCppComponent(name), CppTestSuite {
    val testedComponent: Property<CppComponent?>?
    val testBinary: Property<CppTestExecutable?>
    private val dependencies: DefaultComponentDependencies

    init {
        this.testedComponent = objectFactory.property(CppComponent::class.java)
        this.testBinary = objectFactory.property(CppTestExecutable::class.java)
        this.dependencies = objectFactory.newInstance(DefaultComponentDependencies::class.java, getNames().withSuffix("implementation"))
    }

    fun addExecutable(
        variantName: String?,
        identity: NativeVariantIdentity?,
        targetPlatform: CppPlatform?,
        toolChain: NativeToolChainInternal?,
        platformToolProvider: PlatformToolProvider?
    ): CppTestExecutable? {
        val executableNames = of(getName() + variantName + "Executable", getName() + variantName)
        val testBinary: CppTestExecutable? = objectFactory.newInstance(
            DefaultCppTestExecutable::class.java, executableNames, baseName, getCppSource(), getPrivateHeaderDirs(), implementationDependencies,
            this.testedComponent, targetPlatform, toolChain, platformToolProvider, identity
        )
        getBinaries().add(testBinary)
        return testBinary
    }

    val displayName: DisplayName
        get() = Describables.withTypeAndName("C++ test suite", getName()!!)

    val implementationDependencies: Configuration
        get() = dependencies.implementationDependencies

    override fun getDependencies(): ComponentDependencies {
        return dependencies
    }

    fun dependencies(action: Action<in ComponentDependencies?>) {
        action.execute(dependencies)
    }
}
