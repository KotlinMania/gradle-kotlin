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
package org.gradle.language.cpp

import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.language.BinaryCollection
import org.gradle.language.ComponentWithBinaries
import org.gradle.language.ComponentWithDependencies
import org.gradle.language.ComponentWithTargetMachines

/**
 * Configuration for a C++ component, such as a library or executable, defining the source files and private header directories that make up the component. Private headers are those that are visible only to the source files of the component.
 *
 *
 * A C++ component is composed of some C++ source files that are compiled and then linked into some binary.
 *
 *
 * An instance of this type is added as a project extension by the C++ plugins.
 *
 * @since 4.2
 */
interface CppComponent : ComponentWithBinaries, ComponentWithDependencies, ComponentWithTargetMachines {
    /**
     * Specifies the base name for this component. This name is used to calculate various output file names. The default value is calculated from the project name.
     */
    @JvmField
    val baseName: Property<String?>?

    /**
     * Defines the source files or directories of this component. You can add files or directories to this collection. When a directory is added, all source files are included for compilation.
     *
     *
     * When this collection is empty, the directory `src/main/cpp` is used by default.
     */
    val source: ConfigurableFileCollection?

    /**
     * Configures the source files or directories for this component.
     */
    fun source(action: Action<in ConfigurableFileCollection?>?)

    /**
     * Returns the C++ source files of this component, as defined in [.getSource].
     */
    val cppSource: FileCollection?

    /**
     * Defines the private header file directories of this library.
     *
     *
     * When this collection is empty, the directory `src/main/headers` is used by default.
     */
    val privateHeaders: ConfigurableFileCollection?

    /**
     * Configures the private header directories for this component.
     */
    fun privateHeaders(action: Action<in ConfigurableFileCollection?>?)

    /**
     * Returns the private header include directories of this component, as defined in [.getPrivateHeaders].
     */
    @JvmField
    val privateHeaderDirs: FileCollection?

    /**
     * Returns all header files of this component. Includes public and private header files.
     */
    val headerFiles: FileTree?

    /**
     * Returns the implementation dependencies of this component.
     */
    val implementationDependencies: Configuration?

    /**
     * Returns the binaries for this library.
     *
     * @since 4.5
     */
    override fun getBinaries(): BinaryCollection<out CppBinary?>?
}
