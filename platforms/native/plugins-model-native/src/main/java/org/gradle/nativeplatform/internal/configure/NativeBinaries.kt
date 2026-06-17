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
import org.gradle.internal.BiAction
import org.gradle.model.ModelMap
import org.gradle.model.internal.core.DirectNodeNoInputsModelAction
import org.gradle.model.internal.core.ModelActionRole
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.manage.instance.ManagedInstance
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.Flavor
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeComponentSpec
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.StaticLibraryBinarySpec
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.platform.base.internal.BinaryNamingScheme

object NativeBinaries {
    fun createNativeBinaries(
        component: NativeComponentSpec?,
        binaries: ModelMap<NativeBinarySpec?>,
        resolver: NativeDependencyResolver?,
        fileCollectionFactory: FileCollectionFactory?,
        namingScheme: BinaryNamingScheme,
        platform: NativePlatform?,
        buildType: BuildType?,
        flavor: Flavor?
    ) {
        if (component is NativeLibrarySpec) {
            NativeBinaries.createNativeBinary<SharedLibraryBinarySpec?>(
                SharedLibraryBinarySpec::class.java,
                binaries,
                resolver,
                fileCollectionFactory,
                namingScheme.withBinaryType("SharedLibrary").withRole("shared", false),
                platform,
                buildType,
                flavor
            )
            NativeBinaries.createNativeBinary<StaticLibraryBinarySpec?>(
                StaticLibraryBinarySpec::class.java,
                binaries,
                resolver,
                fileCollectionFactory,
                namingScheme.withBinaryType("StaticLibrary").withRole("static", false),
                platform,
                buildType,
                flavor
            )
        } else {
            NativeBinaries.createNativeBinary<NativeExecutableBinarySpec?>(
                NativeExecutableBinarySpec::class.java,
                binaries,
                resolver,
                fileCollectionFactory,
                namingScheme.withBinaryType("Executable").withRole("executable", true),
                platform,
                buildType,
                flavor
            )
        }
    }

    private fun <T : NativeBinarySpec?> createNativeBinary(
        type: Class<T?>?,
        binaries: ModelMap<NativeBinarySpec?>,
        resolver: NativeDependencyResolver?,
        fileCollectionFactory: FileCollectionFactory?,
        namingScheme: BinaryNamingScheme,
        platform: NativePlatform?,
        buildType: BuildType?,
        flavor: Flavor?
    ) {
        val name = namingScheme.binaryName
        binaries.create<T?>(name, type)

        // TODO:REUSE Refactor after removing reuse
        // This is horrendously bad.
        // We need to set the platform, _before_ the @Defaults rules of NativeBinaryRules assign the toolchain.
        // We can't just assign the toolchain here because the initializer would be closing over the toolchain which is not reusable, and this breaks model reuse.
        // So here we are just closing over the safely reusable things and then using proper dependencies for the tool chain registry.
        // Unfortunately, we can't do it in the create action because that would fire _after_ @Defaults rules.
        // We have to use a @Defaults rule to assign the tool chain because it needs to be there in user @Mutate rules
        // Or at least, the file locations do so that they can be tweaked.
        // LD - 5/6/14
        val backingNode = (binaries as ManagedInstance).getBackingNode()
        val binaryPath = backingNode.getPath().child(name)
        backingNode.applyToLink(
            ModelActionRole.Defaults, DirectNodeNoInputsModelAction.of<NativeBinarySpec?>(
                ModelReference.of<NativeBinarySpec?>(binaryPath, NativeBinarySpec::class.java),
                SimpleModelRuleDescriptor("initialize binary " + binaryPath),
                object : BiAction<MutableModelNode?, NativeBinarySpec?> {
                    override fun execute(mutableModelNode: MutableModelNode?, nativeBinarySpec: NativeBinarySpec?) {
                        initialize(nativeBinarySpec, namingScheme, resolver, fileCollectionFactory, platform, buildType, flavor)
                    }
                }
            ))
        binaries.named(name, NativeBinaryRules::class.java)
    }

    fun initialize(
        nativeBinarySpec: NativeBinarySpec?,
        namingScheme: BinaryNamingScheme?,
        resolver: NativeDependencyResolver?,
        fileCollectionFactory: FileCollectionFactory?,
        platform: NativePlatform?,
        buildType: BuildType?,
        flavor: Flavor?
    ) {
        val nativeBinary = nativeBinarySpec as NativeBinarySpecInternal
        nativeBinary.namingScheme = namingScheme
        nativeBinary.setTargetPlatform(platform)
        nativeBinary.setBuildType(buildType)
        nativeBinary.setFlavor(flavor)
        nativeBinary.setResolver(resolver)
        nativeBinary.setFileCollectionFactory(fileCollectionFactory)
    }
}
