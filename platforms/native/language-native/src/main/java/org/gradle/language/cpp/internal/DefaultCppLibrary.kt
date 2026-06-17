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
package org.gradle.language.cpp.internal

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.language.LibraryDependencies
import org.gradle.language.cpp.CppBinary
import org.gradle.language.cpp.CppLibrary
import org.gradle.language.cpp.CppPlatform
import org.gradle.language.internal.DefaultLibraryDependencies
import org.gradle.language.nativeplatform.internal.PublicationAwareComponent
import org.gradle.nativeplatform.Linkage
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import javax.inject.Inject

abstract class DefaultCppLibrary @Inject constructor(name: String?, configurations: RoleBasedConfigurationContainerInternal, attributesFactory: AttributesFactory) : DefaultCppComponent(name),
    CppLibrary, PublicationAwareComponent {
    private val publicHeadersWithConvention: FileCollection
    val apiElements: NamedDomainObjectProvider<ConsumableConfiguration?>
    private val mainVariant: MainLibraryVariant
    private val dependencies: DefaultLibraryDependencies

    init {
        publicHeadersWithConvention = createDirView(getPublicHeaders(), "src/" + name + "/public")

        getLinkage().convention(mutableSetOf<Linkage?>(Linkage.SHARED))

        dependencies = getObjectFactory().newInstance<DefaultLibraryDependencies>(DefaultLibraryDependencies::class.java, getNames().withSuffix("implementation"), getNames().withSuffix("api"))

        this.apiElements = configurations.consumable(getNames().withSuffix("cppApiElements"), Action { conf: ConsumableConfiguration? ->
            conf!!.extendsFrom(dependencies.getApiDependencies())
            val attrs = conf.getAttributes()
            setApiUsage(attrs)
            attrs.attribute<String?>(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE)
        })

        val publicationAttributes: AttributeContainer = attributesFactory.mutable()
        setApiUsage(publicationAttributes)
        publicationAttributes.attribute<String?>(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.ZIP_TYPE)
        mainVariant = MainLibraryVariant("api", apiElements, publicationAttributes, getObjectFactory())
    }

    fun addSharedLibrary(identity: NativeVariantIdentity, targetPlatform: CppPlatform?, toolChain: NativeToolChainInternal?, platformToolProvider: PlatformToolProvider?): DefaultCppSharedLibrary {
        val result = getObjectFactory().newInstance<DefaultCppSharedLibrary>(
            DefaultCppSharedLibrary::class.java,
            getNames().append(identity.getName()),
            getBaseName(),
            getCppSource(),
            getAllHeaderDirs(),
            getImplementationDependencies()!!,
            targetPlatform!!,
            toolChain!!,
            platformToolProvider!!,
            identity
        )
        getBinaries().add(result)
        return result
    }

    fun addStaticLibrary(identity: NativeVariantIdentity, targetPlatform: CppPlatform?, toolChain: NativeToolChainInternal?, platformToolProvider: PlatformToolProvider?): DefaultCppStaticLibrary {
        val result = getObjectFactory().newInstance<DefaultCppStaticLibrary>(
            DefaultCppStaticLibrary::class.java,
            getNames().append(identity.getName()),
            getBaseName(),
            getCppSource(),
            getAllHeaderDirs(),
            getImplementationDependencies()!!,
            targetPlatform!!,
            toolChain!!,
            platformToolProvider!!,
            identity
        )
        getBinaries().add(result)
        return result
    }

    override fun getDisplayName(): DisplayName {
        return Describables.withTypeAndName("C++ library", getName())
    }

    override fun getImplementationDependencies(): Configuration? {
        return dependencies.getImplementationDependencies()
    }

    override fun getApiDependencies(): Configuration? {
        return dependencies.getApiDependencies()
    }

    override fun getDependencies(): LibraryDependencies {
        return dependencies
    }

    fun dependencies(action: Action<in LibraryDependencies?>) {
        action.execute(dependencies)
    }

    override fun getMainPublication(): MainLibraryVariant {
        return mainVariant
    }

    override fun publicHeaders(action: Action<in ConfigurableFileCollection?>) {
        action.execute(getPublicHeaders())
    }

    override fun getPublicHeaderDirs(): FileCollection {
        return publicHeadersWithConvention
    }

    override fun getPublicHeaderFiles(): FileTree {
        val patterns = PatternSet()
        // if you would like to add more endings to this pattern, make sure to also edit DefaultCppComponent.java and default.vcxproj.filters
        patterns.include("**/*.h")
        patterns.include("**/*.hpp")
        patterns.include("**/*.h++")
        patterns.include("**/*.hxx")
        patterns.include("**/*.hm")
        patterns.include("**/*.inl")
        patterns.include("**/*.inc")
        patterns.include("**/*.xsd")
        return publicHeadersWithConvention.getAsFileTree().matching(patterns)
    }

    override fun getAllHeaderDirs(): FileCollection {
        return publicHeadersWithConvention.plus(super.getAllHeaderDirs())
    }

    abstract override fun getDevelopmentBinary(): Property<CppBinary?>?

    companion object {
        private fun setApiUsage(attrs: AttributeContainer) {
            attrs.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, attrs.named<Usage?>(Usage::class.java, Usage.C_PLUS_PLUS_API))
        }
    }
}
