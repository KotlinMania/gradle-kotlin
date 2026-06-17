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
package org.gradle.nativeplatform.test.xctest.internal

import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.internal.Describables
import org.gradle.language.ComponentDependencies
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.internal.DefaultComponentDependencies
import org.gradle.language.internal.NativeComponentFactory.newInstance
import org.gradle.language.nativeplatform.internal.Names
import org.gradle.language.swift.SwiftComponent
import org.gradle.language.swift.SwiftPlatform
import org.gradle.language.swift.internal.DefaultSwiftComponent
import org.gradle.nativeplatform.test.xctest.SwiftXCTestBinary
import org.gradle.nativeplatform.test.xctest.SwiftXCTestBundle
import org.gradle.nativeplatform.test.xctest.SwiftXCTestExecutable
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import javax.inject.Inject

/**
 * Abstract software component representing an XCTest suite.
 */
abstract class DefaultSwiftXCTestSuite @Inject constructor(name: String) : DefaultSwiftComponent<SwiftXCTestBinary?>(name, SwiftXCTestBinary::class.java), SwiftXCTestSuite {
    private val testBinary: Property<SwiftXCTestBinary?>?
    val testedComponent: Property<SwiftComponent?>?
    private val dependencies: DefaultComponentDependencies

    init {
        this.testedComponent = objectFactory.property(SwiftComponent::class.java)
        this.testBinary = objectFactory.property(SwiftXCTestBinary::class.java)
        this.dependencies = objectFactory.newInstance(DefaultComponentDependencies::class.java, getNames().withSuffix("implementation"))
    }

    val displayName: DisplayName
        get() = Describables.withTypeAndName("XCTest suite", getName()!!)

    fun addExecutable(identity: NativeVariantIdentity?, targetPlatform: SwiftPlatform?, toolChain: NativeToolChainInternal?, platformToolProvider: PlatformToolProvider?): SwiftXCTestExecutable? {
        val result: SwiftXCTestExecutable? = objectFactory.newInstance(
            DefaultSwiftXCTestExecutable::class.java,
            Names.of(getName() + "Executable", getName()!!),
            module,
            false,
            getSwiftSource(),
            implementationDependencies,
            targetPlatform,
            toolChain,
            platformToolProvider,
            identity
        )
        getBinaries().add(result)
        return result
    }

    fun addBundle(identity: NativeVariantIdentity?, targetPlatform: SwiftPlatform?, toolChain: NativeToolChainInternal?, platformToolProvider: PlatformToolProvider?): SwiftXCTestBundle? {
        val result: SwiftXCTestBundle? = objectFactory.newInstance(
            DefaultSwiftXCTestBundle::class.java,
            Names.of(getName() + "Executable", getName()!!),
            module,
            false,
            getSwiftSource(),
            implementationDependencies,
            targetPlatform,
            toolChain,
            platformToolProvider,
            identity
        )
        getBinaries().add(result)
        return result
    }

    val implementationDependencies: Configuration
        get() = dependencies.implementationDependencies

    override fun getDependencies(): ComponentDependencies {
        return dependencies
    }

    fun dependencies(action: Action<in ComponentDependencies?>) {
        action.execute(dependencies)
    }

    override fun getTestBinary(): Property<SwiftXCTestBinary?>? {
        return testBinary
    }
}
