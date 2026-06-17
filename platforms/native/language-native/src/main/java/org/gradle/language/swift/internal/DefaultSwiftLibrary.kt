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
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.provider.Property
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.language.LibraryDependencies
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.internal.DefaultLibraryDependencies
import org.gradle.language.swift.SwiftBinary
import org.gradle.language.swift.SwiftLibrary
import org.gradle.language.swift.SwiftPlatform
import org.gradle.language.swift.SwiftSharedLibrary
import org.gradle.language.swift.SwiftStaticLibrary
import org.gradle.nativeplatform.Linkage
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import javax.inject.Inject

abstract class DefaultSwiftLibrary @Inject constructor(name: String?, private val configurations: ConfigurationContainer?) : DefaultSwiftComponent<SwiftBinary?>(name), SwiftLibrary {
    private val dependencies: DefaultLibraryDependencies

    init {
        getLinkage().convention(mutableSetOf<Linkage?>(Linkage.SHARED))
        dependencies = getObjectFactory().newInstance<DefaultLibraryDependencies>(DefaultLibraryDependencies::class.java, getNames().withSuffix("implementation"), getNames().withSuffix("api"))
    }

    override fun getDisplayName(): DisplayName {
        return Describables.withTypeAndName("Swift library", getName())
    }

    override fun getImplementationDependencies(): Configuration? {
        return dependencies.getImplementationDependencies()
    }

    override fun getDependencies(): LibraryDependencies {
        return dependencies
    }

    fun dependencies(action: Action<in LibraryDependencies?>) {
        action.execute(dependencies)
    }

    fun addStaticLibrary(
        identity: NativeVariantIdentity,
        testable: Boolean,
        targetPlatform: SwiftPlatform?,
        toolChain: NativeToolChainInternal?,
        platformToolProvider: PlatformToolProvider?
    ): SwiftStaticLibrary {
        val result: SwiftStaticLibrary = getObjectFactory().newInstance<DefaultSwiftStaticLibrary>(
            DefaultSwiftStaticLibrary::class.java,
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

    fun addSharedLibrary(
        identity: NativeVariantIdentity,
        testable: Boolean,
        targetPlatform: SwiftPlatform?,
        toolChain: NativeToolChainInternal?,
        platformToolProvider: PlatformToolProvider?
    ): SwiftSharedLibrary {
        val result: SwiftSharedLibrary = getObjectFactory().newInstance<DefaultSwiftSharedLibrary>(
            DefaultSwiftSharedLibrary::class.java,
            getNames().append(identity.getName()),
            getModule(),
            testable,
            getSwiftSource(),
            configurations!!,
            getImplementationDependencies()!!,
            targetPlatform!!,
            toolChain!!,
            platformToolProvider!!,
            identity
        )
        getBinaries().add(result)
        return result
    }

    override fun getApiDependencies(): Configuration? {
        return dependencies.getApiDependencies()
    }

    abstract override fun getDevelopmentBinary(): Property<SwiftBinary?>?
}
