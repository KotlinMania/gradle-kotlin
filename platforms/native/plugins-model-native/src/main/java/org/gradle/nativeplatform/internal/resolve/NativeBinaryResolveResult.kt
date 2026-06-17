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
package org.gradle.nativeplatform.internal.resolve

import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeDependencySet
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.util.internal.CollectionUtils
import org.gradle.util.internal.CollectionUtils.collect
import java.util.function.Function

class NativeBinaryResolveResult(val target: NativeBinarySpec?, libs: MutableCollection<*>) {
    val allResolutions: MutableList<NativeBinaryRequirementResolveResult> = ArrayList<NativeBinaryRequirementResolveResult>()

    init {
        for (lib in libs) {
            allResolutions.add(NativeBinaryRequirementResolveResult(lib))
        }
    }

    val allResults: MutableList<NativeDependencySet?>
        get() = collect<NativeDependencySet?, NativeBinaryRequirementResolveResult?>(
            this.allResolutions,
            Function { obj: NativeBinaryRequirementResolveResult? -> obj!!.getNativeDependencySet() })

    val allLibraryBinaries: MutableList<NativeLibraryBinary?>
        get() {
            val result: MutableList<NativeLibraryBinary?> = ArrayList<NativeLibraryBinary?>()
            for (resolution in this.allResolutions) {
                if (resolution.getLibraryBinary() != null) {
                    result.add(resolution.getLibraryBinary())
                }
            }
            return result
        }

    val pendingResolutions: MutableList<NativeBinaryRequirementResolveResult?>
        get() = CollectionUtils.filter<NativeBinaryRequirementResolveResult?>(
            this.allResolutions,
            org.gradle.api.specs.Spec { element: NativeBinaryRequirementResolveResult? -> !element!!.isComplete() })
}
