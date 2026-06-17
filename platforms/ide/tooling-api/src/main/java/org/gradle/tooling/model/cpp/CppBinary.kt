/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.tooling.model.cpp

/**
 * Represents a C++ binary.
 *
 * @since 4.10
 */
interface CppBinary {
    /**
     * Returns the name of this binary. This is used to disambiguate the binaries of a project. Each binary has a unique name within its project. However, these names are not unique across multiple projects.
     */
    val name: String?

    /**
     * Returns the variant name of this binary. This is used to disambiguate the binaries of a component. Each binary has a unique variant name within its component. However, these names are not unique across multiple projects or components.
     */
    val variantName: String?

    /**
     * Returns the base name of this binary. This is used to calculate output file names.
     */
    val baseName: String?

    /**
     * Returns the compilation details.
     */
    val compilationDetails: CompilationDetails?

    /**
     * Returns the linkage details.
     */
    val linkageDetails: LinkageDetails?
}
