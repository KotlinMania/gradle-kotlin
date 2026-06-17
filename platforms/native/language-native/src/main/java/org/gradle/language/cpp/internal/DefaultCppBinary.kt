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
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.language.cpp.CppBinary
import org.gradle.language.cpp.CppPlatform
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.language.internal.DefaultNativeBinary
import org.gradle.language.nativeplatform.internal.Names
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.TargetMachine
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider

open class DefaultCppBinary(
    names: Names,
    objects: ObjectFactory,
    private val baseName: Provider<String?>?,
    private val sourceFiles: FileCollection?,
    componentHeaderDirs: FileCollection,
    configurations: RoleBasedConfigurationContainerInternal,
    componentImplementation: Configuration?,
    private val targetPlatform: CppPlatform,
    private val toolChain: NativeToolChainInternal?,
    @JvmField val platformToolProvider: PlatformToolProvider?,
    @JvmField val identity: NativeVariantIdentity
) : DefaultNativeBinary(names, objects, componentImplementation), CppBinary {
    private val includePath: FileCollection
    val linkConfiguration: Configuration?
    private val runtimeLibraries: FileCollection?
    val includePathConfiguration: Configuration
    private val compileTaskProperty: Property<CppCompile?>

    init {
        this.compileTaskProperty = objects.property<CppCompile?>(CppCompile::class.java)

        // TODO - reduce duplication with Swift binary
        @Suppress("deprecation") val ipc = configurations.resolvableDependencyScopeLocked(names.withPrefix("cppCompile"), Action { conf: Configuration? ->
            val attrs = conf!!.getAttributes()
            attrs.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, attrs.named<Usage?>(Usage::class.java, Usage.C_PLUS_PLUS_API))
            attrs.attribute<Boolean?>(CppBinary.Companion.DEBUGGABLE_ATTRIBUTE, identity.isDebuggable())
            attrs.attribute<Boolean?>(CppBinary.Companion.OPTIMIZED_ATTRIBUTE, identity.isOptimized())
            attrs.attribute<OperatingSystemFamily?>(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, identity.getTargetMachine().getOperatingSystemFamily())
            attrs.attribute<MachineArchitecture?>(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, identity.getTargetMachine().getArchitecture())
            conf.extendsFrom(getImplementationDependencies())
        })
        includePathConfiguration = ipc

        @Suppress("deprecation") val nativeLink = configurations.resolvableDependencyScopeLocked(names.withPrefix("nativeLink"), Action { conf: Configuration? ->
            val attrs = conf!!.getAttributes()
            attrs.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, attrs.named<Usage?>(Usage::class.java, Usage.NATIVE_LINK))
            attrs.attribute<Boolean?>(CppBinary.Companion.DEBUGGABLE_ATTRIBUTE, identity.isDebuggable())
            attrs.attribute<Boolean?>(CppBinary.Companion.OPTIMIZED_ATTRIBUTE, identity.isOptimized())
            attrs.attribute<OperatingSystemFamily?>(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, identity.getTargetMachine().getOperatingSystemFamily())
            attrs.attribute<MachineArchitecture?>(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, identity.getTargetMachine().getArchitecture())
            conf.extendsFrom(getImplementationDependencies())
        })

        val nativeRuntime: Configuration? = configurations.resolvableLocked(names.withPrefix("nativeRuntime"), Action { conf: Configuration? ->
            val attrs = conf!!.getAttributes()
            attrs.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, attrs.named<Usage?>(Usage::class.java, Usage.NATIVE_RUNTIME))
            attrs.attribute<Boolean?>(CppBinary.Companion.DEBUGGABLE_ATTRIBUTE, identity.isDebuggable())
            attrs.attribute<Boolean?>(CppBinary.Companion.OPTIMIZED_ATTRIBUTE, identity.isOptimized())
            attrs.attribute<OperatingSystemFamily?>(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, identity.getTargetMachine().getOperatingSystemFamily())
            attrs.attribute<MachineArchitecture?>(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, identity.getTargetMachine().getArchitecture())
            conf.extendsFrom(getImplementationDependencies())
        })

        val includeDirs = includePathConfiguration.getIncoming().artifactView(Action { viewConfiguration: ArtifactView.ViewConfiguration? ->
            viewConfiguration!!.attributes(Action { attributeContainer: AttributeContainer? ->
                attributeContainer!!.attribute<String?>(
                    ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE
                )
            })
        })

        includePath = componentHeaderDirs.plus(includeDirs.getFiles())
        this.linkConfiguration = nativeLink
        runtimeLibraries = nativeRuntime
    }

    override fun getBaseName(): Provider<String?>? {
        return baseName
    }

    override fun isDebuggable(): Boolean {
        return identity.isDebuggable()
    }

    override fun isOptimized(): Boolean {
        return identity.isOptimized()
    }

    override fun getCppSource(): FileCollection? {
        return sourceFiles
    }

    override fun getCompileIncludePath(): FileCollection {
        return includePath
    }

    override fun getLinkLibraries(): FileCollection? {
        return this.linkConfiguration
    }

    override fun getRuntimeLibraries(): FileCollection? {
        return runtimeLibraries
    }

    override fun getTargetMachine(): TargetMachine? {
        return targetPlatform.getTargetMachine()
    }

    override fun getTargetPlatform(): CppPlatform {
        return targetPlatform
    }

    val nativePlatform: NativePlatform?
        get() = (targetPlatform as DefaultCppPlatform).getNativePlatform()

    override fun getToolChain(): NativeToolChainInternal? {
        return toolChain
    }

    override fun getCompileTask(): Property<CppCompile?> {
        return compileTaskProperty
    }
}
