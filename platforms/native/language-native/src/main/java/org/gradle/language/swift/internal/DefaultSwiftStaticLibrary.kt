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

import com.google.common.collect.Sets
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.publish.internal.component.ConfigurationSoftwareComponentVariant
import org.gradle.language.cpp.internal.NativeDependencyCache
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithLinkUsage
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithRuntimeUsage
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithStaticLibrary
import org.gradle.language.nativeplatform.internal.Names
import org.gradle.language.swift.SwiftPlatform
import org.gradle.language.swift.SwiftStaticLibrary
import org.gradle.nativeplatform.Linkage
import org.gradle.nativeplatform.tasks.CreateStaticLibrary
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import javax.inject.Inject

class DefaultSwiftStaticLibrary @Inject constructor(
    names: Names?,
    objectFactory: ObjectFactory,
    nativeDependencyCache: NativeDependencyCache?,
    taskDependencyFactory: TaskDependencyFactory?,
    module: Provider<String?>?,
    testable: Boolean,
    source: FileCollection?,
    configurations: ConfigurationContainer?,
    implementation: Configuration?,
    targetPlatform: SwiftPlatform?,
    toolChain: NativeToolChainInternal?,
    platformToolProvider: PlatformToolProvider?,
    identity: NativeVariantIdentity?
) : DefaultSwiftBinary(
    names,
    objectFactory,
    nativeDependencyCache,
    taskDependencyFactory,
    module,
    testable,
    source,
    configurations,
    implementation,
    targetPlatform,
    toolChain,
    platformToolProvider,
    identity
), SwiftStaticLibrary, ConfigurableComponentWithStaticLibrary, ConfigurableComponentWithLinkUsage, ConfigurableComponentWithRuntimeUsage, SoftwareComponentInternal {
    private val linkFile: RegularFileProperty
    private val linkFileProducer: Property<Task?>
    private val createTaskProperty: Property<CreateStaticLibrary?>
    private val linkElements: Property<Configuration>
    private val runtimeElements: Property<Configuration>
    private val outputs: ConfigurableFileCollection

    init {
        this.linkFile = objectFactory.fileProperty()
        this.linkFileProducer = objectFactory.property<Task?>(Task::class.java)
        this.createTaskProperty = objectFactory.property<CreateStaticLibrary?>(CreateStaticLibrary::class.java)
        this.linkElements = objectFactory.property<Configuration?>(Configuration::class.java)
        this.runtimeElements = objectFactory.property<Configuration?>(Configuration::class.java)
        this.outputs = objectFactory.fileCollection()
    }

    override fun getOutputs(): ConfigurableFileCollection {
        return outputs
    }

    override fun getLinkFile(): RegularFileProperty {
        return linkFile
    }

    override fun getLinkFileProducer(): Property<Task?> {
        return linkFileProducer
    }

    override fun getCreateTask(): Property<CreateStaticLibrary?> {
        return createTaskProperty
    }

    override fun getLinkElements(): Property<Configuration> {
        return linkElements
    }

    override fun getRuntimeElements(): Property<Configuration> {
        return runtimeElements
    }

    override fun getLinkage(): Linkage? {
        return Linkage.STATIC
    }

    override fun hasRuntimeFile(): Boolean {
        return false
    }

    override fun getRuntimeFile(): Provider<RegularFile?> {
        return Providers.notDefined<RegularFile?>()
    }

    override fun getLinkAttributes(): AttributeContainer {
        return getIdentity().getLinkVariant().getAttributes()
    }

    override fun getRuntimeAttributes(): AttributeContainer {
        return getIdentity().getRuntimeVariant().getAttributes()
    }

    override fun getUsages(): MutableSet<out UsageContext?> {
        val linkElements = getLinkElements().get()
        val runtimeElements = getRuntimeElements().get()
        return Sets.newHashSet<ConfigurationSoftwareComponentVariant?>( // TODO: Does a static library have runtime elements?
            ConfigurationSoftwareComponentVariant(getIdentity().getLinkVariant(), linkElements.getAllArtifacts(), linkElements),
            ConfigurationSoftwareComponentVariant(getIdentity().getRuntimeVariant(), runtimeElements.getAllArtifacts(), runtimeElements)
        )
    }
}
