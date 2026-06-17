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
package org.gradle.nativeplatform.toolchain.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.nativeplatform.plugins.NativeComponentPlugin
import org.gradle.nativeplatform.toolchain.Clang
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal
import org.gradle.nativeplatform.toolchain.internal.clang.ClangToolChain
import org.jspecify.annotations.NullMarked

/**
 * A [Plugin] which makes the [Clang](http://clang.llvm.org) compiler available for compiling C/C++ code.
 */
@Incubating
@NullMarked
abstract class ClangCompilerPlugin : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(NativeComponentPlugin::class.java)
        val toolChainRegistry: NativeToolChainRegistryInternal = org.gradle.internal.Cast.uncheckedCast<NativeToolChainRegistryInternal?>(
            project.getExtensions().getByType<org.gradle.nativeplatform.toolchain.NativeToolChainRegistry>(org.gradle.nativeplatform.toolchain.NativeToolChainRegistry::class.java)
        )!!
        toolChainRegistry.registerBinding<Clang>(Clang::class.java, ClangToolChain::class.java)
        toolChainRegistry.registerDefaultToolChain(ClangToolChain.DEFAULT_NAME, Clang::class.java)
    }
}
