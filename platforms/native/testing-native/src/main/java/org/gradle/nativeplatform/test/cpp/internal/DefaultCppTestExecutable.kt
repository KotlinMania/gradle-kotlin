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
package org.gradle.nativeplatform.test.cpp.internal

import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.language.cpp.CppComponent
import org.gradle.language.cpp.CppPlatform
import org.gradle.language.cpp.internal.DefaultCppBinary
import org.gradle.language.cpp.internal.DefaultCppComponent
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithExecutable
import org.gradle.language.nativeplatform.internal.Names
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.test.cpp.CppTestExecutable
import org.gradle.nativeplatform.test.tasks.RunTestExecutable
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import java.util.concurrent.Callable
import javax.inject.Inject

class DefaultCppTestExecutable @Inject constructor(
    names: Names,
    private val projectLayout: ProjectLayout,
    baseName: Provider<String?>?,
    sourceFiles: FileCollection?,
    componentHeaderDirs: FileCollection,
    implementation: Configuration?,
    private val testedComponent: Provider<CppComponent?>,
    targetPlatform: CppPlatform,
    toolChain: NativeToolChainInternal?,
    platformToolProvider: PlatformToolProvider?,
    identity: NativeVariantIdentity,
    configurations: RoleBasedConfigurationContainerInternal,
    objects: ObjectFactory
) : DefaultCppBinary(names, objects, baseName, sourceFiles, componentHeaderDirs, configurations, implementation, targetPlatform, toolChain, platformToolProvider, identity), CppTestExecutable,
    ConfigurableComponentWithExecutable {
    private val executableFile: RegularFileProperty
    private val executableFileProducer: Property<Task?>
    private val installationDirectory: DirectoryProperty
    private val installTaskProperty: Property<InstallExecutable?>
    private val linkTaskProperty: Property<LinkExecutable?>
    val runTask: Property<RunTestExecutable?>
    private val outputs: ConfigurableFileCollection
    private val debuggerExecutableFile: RegularFileProperty

    init {
        this.executableFile = objects.fileProperty()
        this.executableFileProducer = objects.property<Task?>(Task::class.java)
        this.debuggerExecutableFile = objects.fileProperty()
        this.installationDirectory = objects.directoryProperty()
        this.linkTaskProperty = objects.property<LinkExecutable?>(LinkExecutable::class.java)
        this.installTaskProperty = objects.property<InstallExecutable?>(InstallExecutable::class.java)
        this.outputs = objects.fileCollection()
        this.runTask = objects.property<RunTestExecutable?>(RunTestExecutable::class.java)
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

    override fun getDebuggerExecutableFile(): Property<RegularFile?> {
        return debuggerExecutableFile
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

    override fun getCompileIncludePath(): FileCollection {
        // TODO: This should be modeled differently, perhaps as a dependency on the implementation configuration
        return super.getCompileIncludePath().plus(projectLayout.files(object : Callable<FileCollection?> {
            override fun call(): FileCollection {
                val tested = testedComponent.getOrNull()
                if (tested == null) {
                    return projectLayout.files()
                }
                return (tested as DefaultCppComponent).allHeaderDirs
            }
        }))
    }
}
