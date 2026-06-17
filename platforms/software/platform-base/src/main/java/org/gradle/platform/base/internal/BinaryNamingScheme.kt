/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.platform.base.internal

import org.gradle.api.Named
import java.io.File

interface BinaryNamingScheme {
    val binaryName: String?

    fun getTaskName(verb: String?): String?

    fun getTaskName(verb: String?, target: String?): String?

    /**
     * Returns a directory that can be used for outputs for this binary.
     */
    fun getOutputDirectory(baseDir: File?): File?

    /**
     * Returns a directory that can be used for outputs of the given type for this binary.
     */
    fun getOutputDirectory(baseDir: File?, outputType: String?): File?

    val description: String?

    val variantDimensions: MutableList<String?>?

    /**
     * Creates a copy of this scheme, replacing the component name.
     */
    fun withComponentName(componentName: String?): BinaryNamingScheme?

    /**
     * Creates a copy of this scheme, replacing the role. The 'role' refers to the role that the binary plays within its component.
     */
    fun withRole(role: String?, isMain: Boolean): BinaryNamingScheme?

    /**
     * Creates a copy of this scheme, replacing the binary type.
     */
    fun withBinaryType(type: String?): BinaryNamingScheme?

    /**
     * Creates a copy of this scheme, specifying a binary name. This overrides the default binary name that would be generated from the other attributes.
     */
    fun withBinaryName(name: String?): BinaryNamingScheme?

    /**
     * Creates a copy of this scheme, *adding* a variant dimension.
     */
    fun withVariantDimension(dimension: String?): BinaryNamingScheme?

    /**
     * Creates a copy of this scheme, *adding* a variant dimension if required.
     */
    fun <T : Named?> withVariantDimension(value: T?, allValuesForAxis: MutableCollection<out T?>?): BinaryNamingScheme?
}
