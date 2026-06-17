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
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.language.cpp.internal.NativeDependencyCache
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.nativeplatform.internal.Names
import org.gradle.language.swift.SwiftPlatform
import org.gradle.nativeplatform.tasks.LinkMachOBundle
import org.gradle.nativeplatform.test.xctest.SwiftXCTestBundle
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import javax.inject.Inject

class DefaultSwiftXCTestBundle @Inject constructor(
    names: Names?,
    objectFactory: ObjectFactory,
    nativeDependencyCache: NativeDependencyCache?,
    taskDependencyFactory: TaskDependencyFactory?,
    module: Provider<String?>?,
    testable: Boolean,
    source: FileCollection?,
    configurations: ConfigurationContainer?,
    implementation: Configuration?,
    targetPlatform: SwiftPlatform?,
    toolChain: NativeToolChainInternal?,
    platformToolProvider: PlatformToolProvider?,
    identity: NativeVariantIdentity?
) : DefaultSwiftXCTestBinary(
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
), SwiftXCTestBundle {
    private val linkTask: Property<LinkMachOBundle?>

    init {
        linkTask = objectFactory.property<LinkMachOBundle?>(LinkMachOBundle::class.java)
    }

    override fun getLinkTask(): Property<LinkMachOBundle?> {
        return linkTask
    }
}
