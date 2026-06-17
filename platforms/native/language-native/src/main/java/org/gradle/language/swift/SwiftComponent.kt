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

import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.language.BinaryCollection
import org.gradle.language.ComponentWithBinaries
import org.gradle.language.ComponentWithDependencies
import org.gradle.language.ComponentWithTargetMachines

/**
 * Configuration for a Swift component, such as a library or executable, defining the source files that make up the component plus other settings.
 *
 *
 * Swift component is composed of some Swift source files that are compiled and then linked into some binary.
 *
 *
 * An instance of this type is added as a project extension by the Swift plugins.
 *
 * @since 4.2
 */
interface SwiftComponent : ComponentWithBinaries, ComponentWithDependencies, ComponentWithTargetMachines {
    /**
     * Defines the Swift module for this component. The default value is calculated from the project name.
     */
    @JvmField
    val module: Property<String?>?

    /**
     * Defines the source files or directories of this component. You can add files or directories to this collection. When a directory is added, all source files are included for compilation.
     *
     *
     * When this collection is empty, the directory `src/main/swift` is used by default.
     */
    val source: ConfigurableFileCollection?

    /**
     * Configures the source files or directories for this component.
     */
    fun source(action: Action<in ConfigurableFileCollection?>?)

    /**
     * Returns the Swift source files of this component, as defined in [.getSource].
     */
    val swiftSource: FileCollection?

    /**
     * Returns the binaries for this library.
     *
     * @since 4.5
     */
    override fun getBinaries(): BinaryCollection<out SwiftBinary?>?

    /**
     * Returns the implementation dependencies of this component.
     */
    val implementationDependencies: Configuration?

    /**
     * Returns the Swift language level to use to compile the source files.
     *
     * @since 4.6
     */
    @JvmField
    val sourceCompatibility: Property<SwiftVersion?>?
}
