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
package org.gradle.nativeplatform.toolchain.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.nativeplatform.plugins.NativeComponentPlugin
import org.gradle.nativeplatform.toolchain.Swiftc
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal
import org.gradle.nativeplatform.toolchain.internal.swift.SwiftcToolChain
import org.jspecify.annotations.NullMarked

/**
 * A [Plugin] which makes the [Swiftc](https://swift.org/compiler-stdlib/) compiler available for compiling Swift code.
 *
 * @since 4.1
 */
@Incubating
@NullMarked
abstract class SwiftCompilerPlugin : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(NativeComponentPlugin::class.java)
        val toolChainRegistry: NativeToolChainRegistryInternal = org.gradle.internal.Cast.uncheckedCast<NativeToolChainRegistryInternal?>(
            project.getExtensions().getByType<org.gradle.nativeplatform.toolchain.NativeToolChainRegistry>(org.gradle.nativeplatform.toolchain.NativeToolChainRegistry::class.java)
        )!!
        toolChainRegistry.registerBinding<Swiftc>(Swiftc::class.java, SwiftcToolChain::class.java)
        toolChainRegistry.registerDefaultToolChain(SwiftcToolChain.DEFAULT_NAME, Swiftc::class.java)
    }
}
