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
package org.gradle.language.swift

import org.gradle.language.ComponentWithDependencies
import org.gradle.language.nativeplatform.ComponentWithObjectFiles

/**
 * A binary built from Swift source and linked from the resulting object files.
 *
 * @since 4.2
 */
interface SwiftBinary : ComponentWithObjectFiles, ComponentWithDependencies {
    /**
     * Returns the name of the Swift module that this binary defines.
     */
    val module: Provider<String?>?

    /**
     * Returns true if this binary has testing enabled.
     *
     * @since 4.4
     */
    val isTestable: Boolean

    /**
     * Returns the Swift source files of this binary.
     */
    val swiftSource: FileCollection?

    /**
     * Returns the modules to use to compile this binary. Includes the module file of this binary's dependencies.
     *
     * @since 4.4
     */
    val compileModules: FileCollection?

    /**
     * Returns the link libraries to use to link this binary. Includes the link libraries of the component's dependencies.
     */
    val linkLibraries: FileCollection?

    /**
     * Returns the runtime libraries required by this binary. Includes the runtime libraries of the component's dependencies.
     */
    val runtimeLibraries: FileCollection?

    /**
     * Returns the compile task for this binary.
     *
     * @since 4.5
     */
    val compileTask: Provider<SwiftCompile?>?

    /**
     * Returns the module file for this binary.
     *
     * @since 4.6
     */
    @JvmField
    val moduleFile: Provider<RegularFile?>?

    /**
     * Returns the target platform for this component.
     *
     * @since 5.2
     */
    val targetPlatform: SwiftPlatform?
}
