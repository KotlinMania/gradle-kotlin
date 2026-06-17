/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.jvm.toolchain.internal

import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.api.internal.provider.ValueSupplier
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.internal.jvm.inspection.JavaInstallationCapability
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.jvm.toolchain.JavaCompiler
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.JavadocTool
import javax.inject.Inject

class DefaultJavaToolchainService @Inject constructor(
    private val queryService: JavaToolchainQueryService,
    private val objectFactory: ObjectFactory,
    private val compilerFactory: JavaCompilerFactory,
    private val toolFactory: ToolchainToolFactory,
    private val eventEmitter: BuildOperationProgressEventEmitter
) : JavaToolchainService {
    override fun compilerFor(config: Action<in JavaToolchainSpec>): Provider<JavaCompiler> {
        return compilerFor(configureToolchainSpec(config))
    }

    override fun compilerFor(spec: JavaToolchainSpec): Provider<JavaCompiler> {
        return queryService.findMatchingToolchain(spec, Sets.immutableEnumSet<JavaInstallationCapability>(JavaInstallationCapability.JAVA_COMPILER))
            .withSideEffect(ValueSupplier.SideEffect { toolchain: JavaToolchain? -> emitEvent(toolchain!!, DefaultJavaToolchainUsageProgressDetails.JavaTool.COMPILER) })
            .map<JavaCompiler>(Transformer { javaToolchain: JavaToolchain? -> DefaultToolchainJavaCompiler(javaToolchain!!, compilerFactory) })
    }

    override fun launcherFor(config: Action<in JavaToolchainSpec>): Provider<JavaLauncher> {
        return launcherFor(configureToolchainSpec(config))
    }

    override fun launcherFor(spec: JavaToolchainSpec): Provider<JavaLauncher> {
        return queryService.findMatchingToolchain(spec)
            .withSideEffect(ValueSupplier.SideEffect { toolchain: JavaToolchain? -> emitEvent(toolchain!!, DefaultJavaToolchainUsageProgressDetails.JavaTool.LAUNCHER) })
            .map<JavaLauncher>(Transformer { javaToolchain: JavaToolchain? -> DefaultToolchainJavaLauncher(javaToolchain!!) })
    }

    override fun javadocToolFor(config: Action<in JavaToolchainSpec>): Provider<JavadocTool> {
        return javadocToolFor(configureToolchainSpec(config))
    }

    override fun javadocToolFor(spec: JavaToolchainSpec): Provider<JavadocTool> {
        return queryService.findMatchingToolchain(spec, Sets.immutableEnumSet<JavaInstallationCapability>(JavaInstallationCapability.JAVADOC_TOOL))
            .withSideEffect(ValueSupplier.SideEffect { toolchain: JavaToolchain? -> emitEvent(toolchain!!, DefaultJavaToolchainUsageProgressDetails.JavaTool.JAVADOC) })
            .map<JavadocTool>(Transformer { javaToolchain: JavaToolchain? -> toolFactory.create<JavadocTool>(JavadocTool::class.java, javaToolchain!!) })
    }

    private fun configureToolchainSpec(config: Action<in JavaToolchainSpec>): DefaultToolchainSpec {
        val toolchainSpec = objectFactory.newInstance<DefaultToolchainSpec>(DefaultToolchainSpec::class.java)
        config.execute(toolchainSpec)
        return toolchainSpec
    }

    private fun emitEvent(toolchain: JavaToolchain, toolName: DefaultJavaToolchainUsageProgressDetails.JavaTool) {
        eventEmitter.emitNowForCurrent(DefaultJavaToolchainUsageProgressDetails(toolName, toolchain.metadata))
    }
}
