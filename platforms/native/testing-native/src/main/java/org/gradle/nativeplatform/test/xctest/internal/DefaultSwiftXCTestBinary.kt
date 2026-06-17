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

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.language.cpp.internal.NativeDependencyCache
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.nativeplatform.internal.Names
import org.gradle.language.swift.SwiftPlatform
import org.gradle.language.swift.internal.DefaultSwiftBinary
import org.gradle.nativeplatform.test.xctest.SwiftXCTestBinary
import org.gradle.nativeplatform.test.xctest.tasks.XCTest
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider

/**
 * Binary of a XCTest suite component.
 * This may be an executable that can be executed directly or a bundle that must be executed through xctest.
 *
 * Either way, the installation provides a single entry point for executing this binary.
 */
abstract class DefaultSwiftXCTestBinary(
    names: Names,
    objectFactory: ObjectFactory,
    nativeDependencyCache: NativeDependencyCache,
    taskDependencyFactory: TaskDependencyFactory,
    module: Provider<String?>?,
    testable: Boolean,
    source: FileCollection?,
    configurations: ConfigurationContainer?,
    implementation: Configuration?,
    targetPlatform: SwiftPlatform,
    toolChain: NativeToolChainInternal?,
    platformToolProvider: PlatformToolProvider?,
    identity: NativeVariantIdentity
) : DefaultSwiftBinary(
    names,
    objectFactory,
    nativeDependencyCache,
    taskDependencyFactory,
    module,
    testable,
    source,
    configurations,
    implementation,
    targetPlatform,
    toolChain,
    platformToolProvider,
    identity
), SwiftXCTestBinary {
    private val executableFile: RegularFileProperty
    private val installDirectory: DirectoryProperty
    private val runScriptFile: RegularFileProperty
    private val runTaskProperty: Property<XCTest?>

    init {
        this.executableFile = objectFactory.fileProperty()
        this.installDirectory = objectFactory.directoryProperty()
        this.runScriptFile = objectFactory.fileProperty()
        this.runTaskProperty = objectFactory.property<XCTest?>(XCTest::class.java)
    }

    override fun getExecutableFile(): RegularFileProperty {
        return executableFile
    }

    override fun getInstallDirectory(): DirectoryProperty {
        return installDirectory
    }

    override fun getRunScriptFile(): RegularFileProperty {
        return runScriptFile
    }

    override fun getRunTask(): Property<XCTest?> {
        return runTaskProperty
    }
}
