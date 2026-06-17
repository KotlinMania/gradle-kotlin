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

import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.publish.internal.component.ConfigurationSoftwareComponentVariant
import org.gradle.language.cpp.CppExecutable
import org.gradle.language.cpp.CppPlatform
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithExecutable
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithRuntimeUsage
import org.gradle.language.nativeplatform.internal.Names
import org.gradle.nativeplatform.Linkage
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import javax.inject.Inject

class DefaultCppExecutable @Inject constructor(
    names: Names?,
    objectFactory: ObjectFactory,
    baseName: Provider<String?>?,
    sourceFiles: FileCollection?,
    componentHeaderDirs: FileCollection,
    configurations: RoleBasedConfigurationContainerInternal,
    implementation: Configuration?,
    targetPlatform: CppPlatform?,
    toolChain: NativeToolChainInternal?,
    platformToolProvider: PlatformToolProvider?,
    identity: NativeVariantIdentity?
) : DefaultCppBinary(names, objectFactory, baseName, sourceFiles, componentHeaderDirs, configurations, implementation, targetPlatform, toolChain, platformToolProvider, identity), CppExecutable,
    ConfigurableComponentWithExecutable, ConfigurableComponentWithRuntimeUsage, SoftwareComponentInternal {
    private val executableFile: RegularFileProperty
    private val executableFileProducer: Property<Task?>
    private val installationDirectory: DirectoryProperty
    private val installTaskProperty: Property<InstallExecutable?>
    private val linkTaskProperty: Property<LinkExecutable?>
    private val runtimeElementsProperty: Property<Configuration>
    private val outputs: ConfigurableFileCollection
    private val debuggerExecutableFile: RegularFileProperty

    init {
        this.executableFile = objectFactory.fileProperty()
        this.executableFileProducer = objectFactory.property<Task?>(Task::class.java)
        this.debuggerExecutableFile = objectFactory.fileProperty()
        this.installationDirectory = objectFactory.directoryProperty()
        this.linkTaskProperty = objectFactory.property<LinkExecutable?>(LinkExecutable::class.java)
        this.installTaskProperty = objectFactory.property<InstallExecutable?>(InstallExecutable::class.java)
        this.runtimeElementsProperty = objectFactory.property<Configuration?>(Configuration::class.java)
        this.outputs = objectFactory.fileCollection()
    }

    override fun getOutputs(): ConfigurableFileCollection {
        return outputs
    }

    override fun getExecutableFile(): RegularFileProperty {
        return executableFile
    }

    override fun getExecutableFileProducer(): Property<Task?> {
        return executableFileProducer
    }

    override fun getInstallDirectory(): DirectoryProperty {
        return installationDirectory
    }

    override fun getInstallTask(): Property<InstallExecutable?> {
        return installTaskProperty
    }

    override fun getLinkTask(): Property<LinkExecutable?> {
        return linkTaskProperty
    }

    override fun getDebuggerExecutableFile(): RegularFileProperty {
        return debuggerExecutableFile
    }

    override fun getRuntimeElements(): Property<Configuration> {
        return runtimeElementsProperty
    }

    override fun getRuntimeFile(): Provider<RegularFile?> {
        return executableFile
    }

    override fun getLinkage(): Linkage? {
        return null
    }

    override fun hasRuntimeFile(): Boolean {
        return true
    }

    override fun getUsages(): MutableSet<out UsageContext?> {
        val runtimeElements = runtimeElementsProperty.get()
        return mutableSetOf<ConfigurationSoftwareComponentVariant?>(ConfigurationSoftwareComponentVariant(getIdentity().getRuntimeVariant(), runtimeElements.getAllArtifacts(), runtimeElements))
    }

    override fun getRuntimeAttributes(): AttributeContainer {
        return getIdentity().getRuntimeVariant().getAttributes()
    }

    override fun getCoordinates(): ModuleVersionIdentifier {
        return getIdentity().getCoordinates()
    }
}
