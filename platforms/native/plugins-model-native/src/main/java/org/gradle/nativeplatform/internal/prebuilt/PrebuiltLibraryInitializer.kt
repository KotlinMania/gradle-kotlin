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
package org.gradle.nativeplatform.internal.prebuilt

import org.gradle.api.Action
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.Flavor
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.nativeplatform.PrebuiltLibrary
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.NativePlatforms
import org.gradle.platform.base.internal.DefaultBinaryNamingScheme

class PrebuiltLibraryInitializer(
    private val instantiator: Instantiator,
    private val fileCollectionFactory: FileCollectionFactory?,
    nativePlatforms: NativePlatforms,
    allPlatforms: MutableCollection<out NativePlatform?>,
    allBuildTypes: MutableCollection<out BuildType?>,
    allFlavors: MutableCollection<out Flavor?>
) : Action<PrebuiltLibrary?> {
    private val allPlatforms: MutableSet<NativePlatform> = LinkedHashSet<NativePlatform>()
    private val allBuildTypes: MutableSet<BuildType> = LinkedHashSet<BuildType>()
    private val allFlavors: MutableSet<Flavor> = LinkedHashSet<Flavor>()

    init {
        this.allPlatforms.addAll(allPlatforms)
        this.allPlatforms.addAll(nativePlatforms.defaultPlatformDefinitions())
        this.allBuildTypes.addAll(allBuildTypes)
        this.allFlavors.addAll(allFlavors)
    }

    override fun execute(prebuiltLibrary: PrebuiltLibrary) {
        for (platform in allPlatforms) {
            for (buildType in allBuildTypes) {
                for (flavor in allFlavors) {
                    createNativeBinaries(prebuiltLibrary, platform, buildType, flavor, fileCollectionFactory)
                }
            }
        }
    }

    fun createNativeBinaries(library: PrebuiltLibrary, platform: NativePlatform, buildType: BuildType, flavor: Flavor, fileCollectionFactory: FileCollectionFactory?) {
        createNativeBinary<DefaultPrebuiltSharedLibraryBinary?>(DefaultPrebuiltSharedLibraryBinary::class.java, "shared", library, platform, buildType, flavor, fileCollectionFactory)
        createNativeBinary<DefaultPrebuiltStaticLibraryBinary?>(DefaultPrebuiltStaticLibraryBinary::class.java, "static", library, platform, buildType, flavor, fileCollectionFactory)
    }

    fun <T : NativeLibraryBinary?> createNativeBinary(
        type: Class<T?>,
        typeName: String?,
        library: PrebuiltLibrary,
        platform: NativePlatform,
        buildType: BuildType,
        flavor: Flavor,
        fileCollectionFactory: FileCollectionFactory?
    ) {
        val name = getName(typeName, library, platform, buildType, flavor)
        val nativeBinary = instantiator.newInstance<T?>(type, name, library, buildType, platform, flavor, fileCollectionFactory)
        library.getBinaries().add(nativeBinary!!)
    }

    private fun getName(typeName: String?, library: PrebuiltLibrary, platform: NativePlatform, buildType: BuildType, flavor: Flavor): String {
        val namingScheme = DefaultBinaryNamingScheme.component(library.getName())
            .withBinaryType(typeName)
            .withVariantDimension(platform.getName())
            .withVariantDimension(buildType.getName())
            .withVariantDimension(flavor.getName())
        return namingScheme.binaryName
    }
}
