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
package org.gradle.language.swift.internal

import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Property
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.language.ComponentDependencies
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.internal.DefaultComponentDependencies
import org.gradle.language.swift.SwiftApplication
import org.gradle.language.swift.SwiftBinary
import org.gradle.language.swift.SwiftExecutable
import org.gradle.language.swift.SwiftPlatform
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import javax.inject.Inject

abstract class DefaultSwiftApplication @Inject constructor(name: String?) : DefaultSwiftComponent<SwiftBinary?>(name), SwiftApplication {
    private val dependencies: DefaultComponentDependencies

    init {
        this.dependencies = getObjectFactory().newInstance<DefaultComponentDependencies>(DefaultComponentDependencies::class.java, getNames().withSuffix("implementation"))
    }

    override fun getDisplayName(): DisplayName {
        return Describables.withTypeAndName("Swift application", getName())
    }

    override fun getImplementationDependencies(): Configuration? {
        return dependencies.getImplementationDependencies()
    }

    override fun getDependencies(): ComponentDependencies {
        return dependencies
    }

    fun dependencies(action: Action<in ComponentDependencies?>) {
        action.execute(dependencies)
    }

    fun addExecutable(
        identity: NativeVariantIdentity,
        testable: Boolean,
        targetPlatform: SwiftPlatform?,
        toolChain: NativeToolChainInternal?,
        platformToolProvider: PlatformToolProvider?
    ): SwiftExecutable {
        val result: SwiftExecutable = getObjectFactory().newInstance<DefaultSwiftExecutable>(
            DefaultSwiftExecutable::class.java,
            getNames().append(identity.getName()),
            getModule(),
            testable,
            getSwiftSource(),
            getImplementationDependencies()!!,
            targetPlatform!!,
            toolChain!!,
            platformToolProvider!!,
            identity
        )
        getBinaries().add(result)
        return result
    }

    abstract override fun getDevelopmentBinary(): Property<SwiftExecutable?>?
}
