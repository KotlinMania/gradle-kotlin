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
package org.gradle.language.swift.internal

import org.gradle.api.Action
import org.gradle.api.Buildable
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.internal.file.collections.FileCollectionAdapter
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskDependency
import org.gradle.language.cpp.CppBinary
import org.gradle.language.cpp.internal.NativeDependencyCache
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.internal.DefaultNativeBinary
import org.gradle.language.nativeplatform.internal.Names
import org.gradle.language.swift.SwiftBinary
import org.gradle.language.swift.SwiftPlatform
import org.gradle.language.swift.tasks.SwiftCompile
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.TargetMachine
import org.gradle.nativeplatform.internal.modulemap.ModuleMap
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import java.io.File

open class DefaultSwiftBinary(
    names: Names,
    objectFactory: ObjectFactory,
    private val nativeDependencyCache: NativeDependencyCache,
    taskDependencyFactory: TaskDependencyFactory,
    private val module: Provider<String?>?,
    private val testable: Boolean,
    private val source: FileCollection?,
    configurations: ConfigurationContainer?,
    componentImplementation: Configuration?,
    private val targetPlatform: SwiftPlatform,
    private val toolChain: NativeToolChainInternal?,
    val platformToolProvider: PlatformToolProvider?,
    identity: NativeVariantIdentity
) : DefaultNativeBinary(names, objectFactory, componentImplementation), SwiftBinary {
    val identity: NativeVariantIdentity
    private val compileModules: FileCollection
    val linkConfiguration: Configuration?
    private val runtimeLibs: Configuration?
    private val moduleFile: RegularFileProperty
    private val compileTaskProperty: Property<SwiftCompile?>
    @JvmField
    val importPathConfiguration: Configuration

    init {
        this.moduleFile = objectFactory.fileProperty()
        this.compileTaskProperty = objectFactory.property<SwiftCompile?>(SwiftCompile::class.java)

        // TODO - reduce duplication with C++ binary
        val rbConfigurations = configurations as RoleBasedConfigurationContainerInternal

        @Suppress("deprecation") val ipc = rbConfigurations.resolvableDependencyScopeLocked(names.withPrefix("swiftCompile"), Action { conf: Configuration? ->
            conf!!.extendsFrom(getImplementationDependencies())
            val attrs = conf.getAttributes()
            attrs.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, attrs.named<Usage?>(Usage::class.java, Usage.SWIFT_API))
            attrs.attribute<Boolean?>(CppBinary.Companion.DEBUGGABLE_ATTRIBUTE, identity.isDebuggable())
            attrs.attribute<Boolean?>(CppBinary.Companion.OPTIMIZED_ATTRIBUTE, identity.isOptimized())
            attrs.attribute<OperatingSystemFamily?>(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, identity.getTargetMachine().getOperatingSystemFamily())
            attrs.attribute<MachineArchitecture?>(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, identity.getTargetMachine().getArchitecture())
        })
        importPathConfiguration = ipc

        @Suppress("deprecation") val nativeLink = rbConfigurations.resolvableDependencyScopeLocked(names.withPrefix("nativeLink"), Action { conf: Configuration? ->
            conf!!.extendsFrom(getImplementationDependencies())
            val attrs = conf.getAttributes()
            attrs.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, attrs.named<Usage?>(Usage::class.java, Usage.NATIVE_LINK))
            attrs.attribute<Boolean?>(CppBinary.Companion.DEBUGGABLE_ATTRIBUTE, identity.isDebuggable())
            attrs.attribute<Boolean?>(CppBinary.Companion.OPTIMIZED_ATTRIBUTE, identity.isOptimized())
            attrs.attribute<OperatingSystemFamily?>(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, identity.getTargetMachine().getOperatingSystemFamily())
            attrs.attribute<MachineArchitecture?>(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, identity.getTargetMachine().getArchitecture())
        })

        val nativeRuntime: Configuration? = rbConfigurations.resolvableLocked(names.withPrefix("nativeRuntime"), Action { conf: Configuration? ->
            conf!!.extendsFrom(getImplementationDependencies())
            val attrs = conf.getAttributes()
            attrs.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, attrs.named<Usage?>(Usage::class.java, Usage.NATIVE_RUNTIME))
            attrs.attribute<Boolean?>(CppBinary.Companion.DEBUGGABLE_ATTRIBUTE, identity.isDebuggable())
            attrs.attribute<Boolean?>(CppBinary.Companion.OPTIMIZED_ATTRIBUTE, identity.isOptimized())
            attrs.attribute<OperatingSystemFamily?>(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, identity.getTargetMachine().getOperatingSystemFamily())
            attrs.attribute<MachineArchitecture?>(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, identity.getTargetMachine().getArchitecture())
        })

        compileModules = FileCollectionAdapter(DefaultSwiftBinary.ModulePath(importPathConfiguration), taskDependencyFactory)
        this.linkConfiguration = nativeLink
        runtimeLibs = nativeRuntime
        this.identity = identity
    }

    override fun getModule(): Provider<String?>? {
        return module
    }

    override fun getBaseName(): Provider<String?>? {
        return module
    }

    override fun isDebuggable(): Boolean {
        return identity.isDebuggable()
    }

    override fun isOptimized(): Boolean {
        return identity.isOptimized()
    }

    override fun isTestable(): Boolean {
        return testable
    }

    override fun getSwiftSource(): FileCollection? {
        return source
    }

    override fun getCompileModules(): FileCollection {
        return compileModules
    }

    override fun getLinkLibraries(): FileCollection? {
        return this.linkConfiguration
    }

    override fun getRuntimeLibraries(): FileCollection? {
        return runtimeLibs
    }

    override fun getModuleFile(): RegularFileProperty {
        return moduleFile
    }

    override fun getCompileTask(): Property<SwiftCompile?> {
        return compileTaskProperty
    }

    override fun getTargetMachine(): TargetMachine? {
        return targetPlatform.getTargetMachine()
    }

    override fun getTargetPlatform(): SwiftPlatform {
        return targetPlatform
    }

    val nativePlatform: NativePlatform?
        get() = (targetPlatform as DefaultSwiftPlatform).getNativePlatform()

    override fun getToolChain(): NativeToolChainInternal? {
        return toolChain
    }

    private inner class ModulePath(private val importPathConfig: Configuration) : MinimalFileSet, Buildable {
        private var result: MutableSet<File?>? = null

        override fun getDisplayName(): String {
            return "Module include path for " + this@DefaultSwiftBinary.toString()
        }

        override fun getFiles(): MutableSet<File?> {
            if (result == null) {
                result = LinkedHashSet<File?>()
                val moduleMaps: MutableMap<ComponentIdentifier?, ModuleMap> = LinkedHashMap<ComponentIdentifier?, ModuleMap>()
                for (artifact in importPathConfig.getIncoming().getArtifacts()) {
                    val usage = artifact.getVariant().getAttributes().getAttribute<Usage?>(Usage.USAGE_ATTRIBUTE)
                    if (usage != null && Usage.C_PLUS_PLUS_API == usage.getName()) {
                        val moduleName: String?

                        val id = artifact.getId().getComponentIdentifier()
                        if (ModuleComponentIdentifier::class.java.isAssignableFrom(id.javaClass)) {
                            moduleName = (id as ModuleComponentIdentifier).getModule()
                        } else if (ProjectComponentIdentifier::class.java.isAssignableFrom(id.javaClass)) {
                            moduleName = (id as ProjectComponentIdentifier).getProjectName()
                        } else {
                            throw IllegalArgumentException("Could not determine the name of " + id.getDisplayName() + ": unknown component identifier type: " + id.javaClass.getSimpleName())
                        }

                        val moduleMap: ModuleMap
                        if (moduleMaps.containsKey(id)) {
                            moduleMap = moduleMaps.get(id)!!
                        } else {
                            moduleMap = ModuleMap(moduleName, ArrayList<String?>())
                            moduleMaps.put(id, moduleMap)
                        }
                        moduleMap.getPublicHeaderPaths().add(artifact.getFile().getAbsolutePath())
                    }
                    // TODO Change this to only add SWIFT_API artifacts and instead parse modulemaps to discover compile task inputs
                    result!!.add(artifact.getFile())
                }

                if (!moduleMaps.isEmpty()) {
                    for (moduleMap in moduleMaps.values) {
                        result!!.add(nativeDependencyCache.getModuleMapFile(moduleMap))
                    }
                }
            }
            return result!!
        }

        override fun getBuildDependencies(): TaskDependency {
            return importPathConfig.getBuildDependencies()
        }
    }
}
