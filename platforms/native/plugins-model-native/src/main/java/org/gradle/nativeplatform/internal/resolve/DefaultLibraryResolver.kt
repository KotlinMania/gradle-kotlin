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

import org.gradle.api.DomainObjectSet
import org.gradle.api.InvalidUserDataException
import org.gradle.language.base.internal.resolve.LibraryResolveException
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.Flavor
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.nativeplatform.NativeLibraryRequirement
import org.gradle.nativeplatform.SharedLibraryBinary
import org.gradle.nativeplatform.StaticLibraryBinary
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.util.internal.GUtil

internal class DefaultLibraryResolver(private val libraryBinaryLocator: LibraryBinaryLocator, private val requirement: NativeLibraryRequirement, private val context: NativeBinarySpec) {
    fun resolveLibraryBinary(): NativeLibraryBinary {
        val binaries: DomainObjectSet<NativeLibraryBinary?>? = libraryBinaryLocator.getBinaries(LibraryIdentifier(requirement.getProjectPath(), requirement.getLibraryName()))
        if (binaries == null) {
            throw LibraryResolveException(getFailureMessage(requirement))
        }
        return DefaultLibraryResolver.LibraryResolution()
            .withFlavor(context.getFlavor())
            .withPlatform(context.getTargetPlatform())
            .withBuildType(context.getBuildType())
            .resolveLibrary(binaries)
    }

    private fun getFailureMessage(requirement: NativeLibraryRequirement): String {
        return if (requirement.getProjectPath() == null || requirement.getProjectPath() == context.getProjectPath()) String.format(
            "Could not locate library '%s' required by %s.", requirement.getLibraryName(),
            this.contextMessage
        ) else String.format("Could not locate library '%s' in project '%s' required by %s.", requirement.getLibraryName(), requirement.getProjectPath(), this.contextMessage)
    }

    private val contextMessage: String
        get() = String.format("'%s' in project '%s'", context.getComponent().getName(), context.getProjectPath())

    private inner class LibraryResolution {
        private var flavor: Flavor? = null
        private var platform: NativePlatform? = null
        private var buildType: BuildType? = null

        fun withFlavor(flavor: Flavor?): LibraryResolution {
            this.flavor = flavor
            return this
        }

        fun withPlatform(platform: NativePlatform?): LibraryResolution {
            this.platform = platform
            return this
        }

        fun withBuildType(buildType: BuildType?): LibraryResolution {
            this.buildType = buildType
            return this
        }

        fun resolveLibrary(allBinaries: DomainObjectSet<NativeLibraryBinary?>): NativeLibraryBinary {
            val type = getTypeForLinkage(requirement.getLinkage())
            val candidateBinaries: DomainObjectSet<out NativeLibraryBinary> = allBinaries.withType(type)
            return resolve(candidateBinaries)
        }

        fun getTypeForLinkage(linkage: String?): Class<out NativeLibraryBinary> {
            if ("static" == linkage) {
                return StaticLibraryBinary::class.java
            }
            if ("shared" == linkage || linkage == null) {
                return SharedLibraryBinary::class.java
            }
            throw InvalidUserDataException("Not a valid linkage: " + linkage)
        }

        fun resolve(candidates: MutableSet<out NativeLibraryBinary>): NativeLibraryBinary {
            for (candidate in candidates) {
                if (flavor != null && flavor!!.getName() != candidate.getFlavor().getName()) {
                    continue
                }
                if (platform != null && platform!!.getName() != candidate.getTargetPlatform().getName()) {
                    continue
                }
                if (buildType != null && buildType!!.getName() != candidate.getBuildType().getName()) {
                    continue
                }

                return candidate
            }

            val typeName = GUtil.elvis<String?>(requirement.getLinkage(), "shared")
            throw LibraryResolveException(
                String.format(
                    "No %s library binary available for library '%s' with [flavor: '%s', platform: '%s', buildType: '%s']",
                    typeName, requirement.getLibraryName(), flavor!!.getName(), platform!!.getName(), buildType!!.getName()
                )
            )
        }
    }
}
