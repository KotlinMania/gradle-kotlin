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
package org.gradle.nativeplatform.internal.configure

import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.Flavor
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.internal.TargetedNativeComponentInternal
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.platform.internal.NativePlatforms
import org.gradle.platform.base.internal.BinaryNamingScheme
import org.gradle.platform.base.internal.DefaultBinaryNamingScheme
import org.gradle.platform.base.internal.DefaultPlatformRequirement.Companion.create
import org.gradle.platform.base.internal.PlatformRequirement
import org.gradle.platform.base.internal.PlatformResolvers
import org.gradle.util.internal.CollectionUtils.collect
import java.util.function.Function

/**
 * Cross cutting rules for all instances of [org.gradle.nativeplatform.NativeComponentSpec]
 */
object NativeComponentRules {
    fun createBinariesImpl(
        nativeComponent: TargetedNativeComponentInternal,
        platforms: PlatformResolvers,
        buildTypes: MutableSet<out BuildType?>?,
        flavors: MutableSet<out Flavor?>?,
        nativePlatforms: NativePlatforms,
        nativeDependencyResolver: NativeDependencyResolver?,
        fileCollectionFactory: FileCollectionFactory?
    ) {
        val resolvedPlatforms = resolvePlatforms(nativeComponent, nativePlatforms, platforms)

        for (platform in resolvedPlatforms) {
            var namingScheme = DefaultBinaryNamingScheme.component(nativeComponent.getName())
            namingScheme = namingScheme.withVariantDimension<NativePlatform?>(platform, resolvedPlatforms)
            executeForEachBuildType(
                nativeComponent,
                platform as NativePlatformInternal?,
                namingScheme,
                buildTypes,
                flavors,
                nativeDependencyResolver,
                fileCollectionFactory
            )
        }
    }

    private fun resolvePlatforms(targetedComponent: TargetedNativeComponentInternal, nativePlatforms: NativePlatforms, platforms: PlatformResolvers): MutableList<NativePlatform?> {
        var targetPlatforms = targetedComponent.getTargetPlatforms()
        if (targetPlatforms.isEmpty()) {
            val requirement = create(nativePlatforms.defaultPlatformName)
            targetPlatforms = mutableListOf<PlatformRequirement?>(requirement)
        }
        return collect<NativePlatform?, PlatformRequirement?>(
            targetPlatforms,
            Function { platformRequirement: PlatformRequirement? -> platforms.resolve<NativePlatform?>(NativePlatform::class.java, platformRequirement) })
    }

    private fun executeForEachBuildType(
        projectNativeComponent: TargetedNativeComponentInternal,
        platform: NativePlatformInternal?,
        namingScheme: BinaryNamingScheme,
        allBuildTypes: MutableSet<out BuildType?>?,
        allFlavors: MutableSet<out Flavor?>?,
        nativeDependencyResolver: NativeDependencyResolver?,
        fileCollectionFactory: FileCollectionFactory?
    ) {
        val targetBuildTypes = projectNativeComponent.chooseBuildTypes(allBuildTypes)
        for (buildType in targetBuildTypes) {
            val namingSchemeWithBuildType = namingScheme.withVariantDimension<BuildType?>(buildType, targetBuildTypes)
            executeForEachFlavor(
                projectNativeComponent,
                platform,
                buildType,
                namingSchemeWithBuildType,
                allFlavors,
                nativeDependencyResolver,
                fileCollectionFactory
            )
        }
    }

    private fun executeForEachFlavor(
        projectNativeComponent: TargetedNativeComponentInternal,
        platform: NativePlatform?,
        buildType: BuildType?,
        namingScheme: BinaryNamingScheme,
        allFlavors: MutableSet<out Flavor?>?,
        nativeDependencyResolver: NativeDependencyResolver?,
        fileCollectionFactory: FileCollectionFactory?
    ) {
        val targetFlavors = projectNativeComponent.chooseFlavors(allFlavors)
        for (flavor in targetFlavors) {
            val namingSchemeWithFlavor = namingScheme.withVariantDimension<Flavor?>(flavor, targetFlavors)
            NativeBinaries.createNativeBinaries(
                projectNativeComponent,
                projectNativeComponent.getBinaries().withType<NativeBinarySpec?>(NativeBinarySpec::class.java),
                nativeDependencyResolver,
                fileCollectionFactory,
                namingSchemeWithFlavor,
                platform,
                buildType,
                flavor
            )
        }
    }
}
