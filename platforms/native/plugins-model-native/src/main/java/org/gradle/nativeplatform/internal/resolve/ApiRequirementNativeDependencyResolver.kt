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

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.nativeplatform.NativeDependencySet
import org.gradle.nativeplatform.NativeLibraryRequirement

/**
 * Adapts an 'api' library requirement to a default linkage, and then wraps the result so that only headers are provided.
 */
class ApiRequirementNativeDependencyResolver(private val delegate: NativeDependencyResolver) : NativeDependencyResolver {
    override fun resolve(nativeBinaryResolveResult: NativeBinaryResolveResult) {
        for (resolution in nativeBinaryResolveResult.getAllResolutions()) {
            val linkage = getLinkage(resolution)
            if ("api" == linkage) {
                resolution.setRequirement(ApiAdaptedNativeLibraryRequirement(resolution.getRequirement()))
            }
        }

        delegate.resolve(nativeBinaryResolveResult)

        for (resolution in nativeBinaryResolveResult.getAllResolutions()) {
            if (resolution.getRequirement() is ApiAdaptedNativeLibraryRequirement) {
                val adaptedRequirement = resolution.getRequirement() as ApiAdaptedNativeLibraryRequirement
                resolution.setRequirement(adaptedRequirement.original)
                //                resolution.setLibraryBinary(null);
                resolution.setNativeDependencySet(ApiNativeDependencySet(resolution.getNativeDependencySet()))
            }
        }
    }

    private fun getLinkage(resolution: NativeBinaryRequirementResolveResult): String? {
        if (resolution.getRequirement() == null) {
            return null
        }
        return resolution.getRequirement().getLinkage()
    }

    private class ApiAdaptedNativeLibraryRequirement(val original: NativeLibraryRequirement) : NativeLibraryRequirement {
        override fun withProjectPath(projectPath: String?): NativeLibraryRequirement {
            return ApiAdaptedNativeLibraryRequirement(original.withProjectPath(projectPath))
        }

        override fun getProjectPath(): String? {
            return original.getProjectPath()
        }

        override fun getLibraryName(): String? {
            return original.getLibraryName()
        }

        override fun getLinkage(): String? {
            // Rely on the default linkage for providing the headers
            return null
        }
    }

    private class ApiNativeDependencySet(private val delegate: NativeDependencySet) : NativeDependencySet {
        override fun getIncludeRoots(): FileCollection? {
            return delegate.getIncludeRoots()
        }

        override fun getLinkFiles(): FileCollection {
            return FileCollectionFactory.empty(delegate.getLinkFiles().toString())
        }

        override fun getRuntimeFiles(): FileCollection {
            return FileCollectionFactory.empty(delegate.getRuntimeFiles().toString())
        }
    }
}
